package com.aliothmoon.maameow.domain.service

import android.os.IBinder
import android.os.Process
import com.aliothmoon.maameow.MaaCoreService
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.domain.models.ClientResourceProfile
import com.aliothmoon.maameow.manager.LogcatServiceManager
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.manager.RemoteServiceManager.useRemoteService
import com.aliothmoon.maameow.remote.ResourceFiles
import com.aliothmoon.maameow.remote.SetupResult
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.resolveSelectedLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

class MaaResourceLoader(
    private val pathConfig: MaaPathConfig,
    private val appSettings: AppSettingsManager,
    private val chainState: TaskChainState,
    private val activityManager: ActivityManager,
    private val coreDataPusher: CoreDataPusher,
) {
    // 换档重启期间抑制 reset()，主动解绑会触发 onServiceDisconnected
    private val fullReloadInProgress = AtomicBoolean(false)

    private val loadMutex = Mutex()

    /** 当前提权进程已加载资源的客户端，null = 该进程还没被任何资源档污染 */
    @Volatile
    private var loadedClientType: String? = null

    /** Ready 对应的提权进程；Shizuku 重启后的重绑不经过 Died/Disconnected，只能靠它识别换了进程 */
    @Volatile
    private var loadedBinder: IBinder? = null

    sealed class State {
        data object NotLoaded : State()
        data class Loading(val message: String = "") : State()
        data class Reloading(val message: String = "") : State()
        data object Ready : State()

        /**
         * @param permanent true = 资源文件缺失，重试无意义，需用户手动重新初始化；
         *                  false = IPC/IO 临时失败，ensureLoaded() 可再次尝试加载。
         * @param reason STORAGE_INACCESSIBLE = 提权进程读写不了 core 根目录，需切换数据目录（#227）
         */
        data class Failed(
            val message: String,
            val permanent: Boolean = false,
            val reason: FailReason = FailReason.GENERIC,
        ) : State()

        enum class FailReason { GENERIC, STORAGE_INACCESSIBLE }
    }

    private val _state = MutableStateFlow<State>(State.NotLoaded)
    val state: StateFlow<State> = _state.asStateFlow()

    suspend fun load(clientType: String = chainState.clientType): Result<Unit> =
        loadMutex.withLock { loadLocked(clientType) }

    private suspend fun loadLocked(clientType: String): Result<Unit> {
        // MaaCore 的 TaskData 和函数内 static 在进程内不可回滚，日服写入的
        // CharsNameOcrReplace.replaceFull 切回国服也删不掉，换资源档只能换进程
        val previousClientType = loadedClientType
        val restartNeeded = requiresServiceRestart(previousClientType, clientType)

        _state.value = if (restartNeeded) State.Reloading() else State.Loading()
        if (!pathConfig.isResourceReady) {
            Timber.e("MaaResourceLoader.load() aborted: resource not ready (version.json missing or app version mismatch)")
            _state.value = State.Failed("资源未就绪，请重新初始化", permanent = true)
            return Result.failure(Exception("Resource not ready"))
        }
        Timber.i("MaaCore resources loading, client type=$clientType")
        try {
            refreshHotUpdate(clientType)
        } catch (e: Exception) {
            Timber.e(e, "refreshHotUpdate error")
        }

        return try {
            if (restartNeeded) {
                restartRemoteServiceForProfileSwitch(previousClientType, clientType)
            }
            // 下发 LoadResource 即视为污染，中途失败也不例外
            loadedClientType = clientType
            useRemoteService { srv ->
                val setupCode = srv.setup(pathConfig.coreRootDir, appSettings.debugMode.value)
                if (setupCode != SetupResult.OK) {
                    val desc = SetupResult.describe(setupCode)
                    val msg = "Remote setup failed: $desc"
                    Timber.e("%s userDir=%s", msg, pathConfig.coreRootDir)
                    val inaccessible = setupCode == SetupResult.ERR_USER_DIR_INACCESSIBLE
                    _state.value = State.Failed(
                        message = msg,
                        permanent = inaccessible,
                        reason = if (inaccessible) State.FailReason.STORAGE_INACCESSIBLE
                        else State.FailReason.GENERIC,
                    )
                    return@useRemoteService Result.failure(Exception(msg))
                }
                srv.setForceFullscreenOnVirtualDisplay(appSettings.forceFullscreenOnVirtualDisplay.value)

                if (appSettings.debugMode.value) {
                    val appPid = Process.myPid()
                    val servicePid = srv.pid()
                    CoroutineScope(Dispatchers.IO).async {
                        runCatching {
                            LogcatServiceManager.bind()
                            LogcatServiceManager.startCapture(
                                appPid,
                                servicePid,
                                pathConfig.coreRootDir
                            )
                        }.onFailure { Timber.w(it, "LogcatService startCapture failed") }
                    }
                }

                val maa = srv.maaCoreService
                val isGlobal = ClientResourceProfile.of(clientType).isNotEmpty()

                copyTasksJson(pathConfig.cacheResourceDir)
                if (isGlobal) {
                    copyTasksJson(pathConfig.globalCacheResourceDir(clientType).absolutePath)
                }

                // 独立目录：投递热更包与用户文件
                if (!coreDataPusher.prepare(srv)) {
                    val msg = "Core data prepare failed"
                    _state.value = State.Failed(msg)
                    Timber.e(msg)
                    return@useRemoteService Result.failure(Exception(msg))
                }

                if (!loadResIfExists(maa, pathConfig.rootDir)) {
                    _state.value = State.Failed("Failed to load main resource")
                    Timber.e("LoadResource failed: ${pathConfig.rootDir}")
                    return@useRemoteService Result.failure(Exception("Failed to load main resource"))
                }

                val followUps = buildList {
                    add(pathConfig.cacheDir)
                    if (isGlobal) {
                        pathConfig.globalResourceDir(clientType).parent?.let(::add)
                        pathConfig.globalCacheResourceDir(clientType).parent?.let(::add)
                    }
                }

                followUps.forEach { loadResIfExists(maa, it) }

                if (appSettings.tasksOverrideEnabled.value) {
                    loadResIfExists(maa, pathConfig.overridesDir)
                }

                loadedBinder = srv.asBinder()
                _state.value = State.Ready
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "MaaResourceLoader error")
            _state.value = State.Failed(e.message ?: "Resource loading exception")
            Result.failure(e)
        } finally {
            // 抑制窗口内服务若真死了，那次 reset() 被吞了，这里补回来
            val wasSuppressing = fullReloadInProgress.getAndSet(false)
            if (wasSuppressing &&
                RemoteServiceManager.state.value !is RemoteServiceManager.ServiceState.Connected
            ) {
                Timber.w("Service not connected after profile switch, dropping loaded state")
                reset()
            }
        }
    }

    /** 解绑即销毁旧进程，MaaCore 的进程级单例随之清零 */
    private suspend fun restartRemoteServiceForProfileSwitch(from: String?, to: String) {
        Timber.i("Client type changed (%s -> %s), restarting elevated service", from, to)
        fullReloadInProgress.set(true)
        loadedClientType = null
        runCatching { RemoteServiceManager.unbind() }
            .onFailure { Timber.w(it, "unbind before profile switch failed") }
        withTimeoutOrNull(SERVICE_RESTART_TIMEOUT_MS.milliseconds) {
            RemoteServiceManager.state.first { it !is RemoteServiceManager.ServiceState.Connected }
        }
        // destroy 是 oneway，给旧进程留点退出窗口
        delay(SERVICE_RESTART_SETTLE_MS.milliseconds)
    }

    /** 确保活动关卡与热更 tasks.json 就位，刚拉过会复用 */
    private suspend fun refreshHotUpdate(clientType: String) {
        withTimeout(30_000.milliseconds) {
            withContext(Dispatchers.IO) { activityManager.load(clientType) }
        }
    }

    /** 存在性按 App 侧目录判断：独立目录下内置资源、热更、用户文件两侧一致 */
    private fun loadResIfExists(maa: MaaCoreService, parentDir: String): Boolean {
        val resDir = File(parentDir, "resource")
        if (!resDir.exists()) {
            Timber.d("Resource directory not found, skipping: ${resDir.absolutePath}")
            return true
        }
        val coreDir = pathConfig.toCorePath(parentDir)
        return maa.LoadResource(coreDir).also { ok ->
            if (ok) Timber.i("LoadResource succeeded: $coreDir")
            else Timber.w("LoadResource failed: $coreDir")
        }
    }

    /** 资源档与已加载的不一致时重新加载，必要时连带重启进程 */
    suspend fun ensureLoaded(clientType: String = chainState.clientType): Result<Unit> {
        return when (val s = _state.value) {
            is State.Ready -> {
                val current = RemoteServiceManager.getInstanceOrNull()
                if (current != null && resetIfStale(current)) load(clientType)
                else ensureProfile(clientType)
            }

            is State.Failed -> if (s.permanent) {
                // 资源文件缺失，重试无意义
                Result.failure(Exception(s.message))
            } else {
                // 临时失败（IPC/IO），重新尝试加载
                load(clientType)
            }

            is State.Loading, is State.Reloading -> {
                // 等待当前加载结束，避免并发启动时误报失败
                val terminal = _state.first { it is State.Ready || it is State.Failed }
                if (terminal is State.Ready) ensureProfile(clientType)
                else Result.failure(Exception((terminal as State.Failed).message))
            }

            else -> load(clientType)
        }
    }

    private suspend fun ensureProfile(clientType: String): Result<Unit> {
        val loaded = loadedClientType
        if (loaded != null && ClientResourceProfile.of(loaded) == ClientResourceProfile.of(clientType)) {
            return Result.success(Unit)
        }
        Timber.i("Loaded client %s mismatches requested %s, reloading", loaded, clientType)
        return load(clientType)
    }

    /** 提权进程已消失，只有这条路径能清 [loadedClientType] */
    fun reset() {
        if (fullReloadInProgress.get()) {
            Timber.i("Skip resource reset while full reload is in progress")
            return
        }
        loadedClientType = null
        loadedBinder = null
        _state.value = State.NotLoaded
    }

    /** 已连上的不是加载资源的那个进程时作废 Ready，返回是否作废了 */
    fun resetIfStale(service: RemoteService): Boolean {
        if (_state.value !is State.Ready) return false
        val loaded = loadedBinder
        if (loaded == null || loaded === service.asBinder()) return false
        Timber.w("Elevated service replaced since resources were loaded, dropping Ready")
        reset()
        return true
    }

    /**
     * 配置变了要重载，但进程还活着
     * 必须保留 [loadedClientType]，清掉会让换客户端误判成无需重启
     */
    fun invalidate() {
        if (fullReloadInProgress.get()) {
            Timber.i("Skip resource invalidate while full reload is in progress")
            return
        }
        _state.value = State.NotLoaded
    }

    private fun copyTasksJson(resourcePath: String) {
        if (!ResourceFiles.deriveTasksJson(File(resourcePath))) Timber.w("copyTasksJson failed: $resourcePath")
    }

    companion object {
        private const val SERVICE_RESTART_TIMEOUT_MS = 5_000L
        private const val SERVICE_RESTART_SETTLE_MS = 500L

        /** 换档必须换进程，global 之间互切也算 */
        internal fun requiresServiceRestart(loaded: String?, target: String): Boolean =
            loaded != null && ClientResourceProfile.of(loaded) != ClientResourceProfile.of(target)
    }
}
