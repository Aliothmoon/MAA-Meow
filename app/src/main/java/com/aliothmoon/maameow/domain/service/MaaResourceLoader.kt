package com.aliothmoon.maameow.domain.service

import android.os.Process
import com.aliothmoon.maameow.MaaCoreService
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.manager.LogcatServiceManager
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.manager.RemoteServiceManager.useRemoteService
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.resolveSelectedLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

class MaaResourceLoader(
    private val pathConfig: MaaPathConfig,
    private val appSettings: AppSettingsManager,
    private val chainState: TaskChainState,
    private val itemHelper: ItemHelper,
    private val resourceDataManager: ResourceDataManager,
    private val activityManager: ActivityManager
) {
    // 换档重启期间抑制 reset()，主动解绑会触发 onServiceDisconnected
    private val fullReloadInProgress = AtomicBoolean(false)

    private val loadMutex = Mutex()

    /** 当前提权进程已加载资源的客户端，null = 该进程还没被任何资源档污染 */
    @Volatile
    private var loadedClientType: String? = null

    sealed class State {
        data object NotLoaded : State()
        data class Loading(val message: String = "") : State()
        data class Reloading(val message: String = "") : State()
        data object Ready : State()
        /**
         * @param permanent true = 资源文件缺失，重试无意义，需用户手动重新初始化；
         *                  false = IPC/IO 临时失败，ensureLoaded() 可再次尝试加载。
         */
        data class Failed(val message: String, val permanent: Boolean = false) : State()
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
            doLoadDepsInfo(clientType)
        } catch (e: Exception) {
            Timber.e(e, "doLoadDepsInfo error")
        }

        return try {
            if (restartNeeded) {
                restartRemoteServiceForProfileSwitch(previousClientType, clientType)
            }
            // 下发 LoadResource 即视为污染，中途失败也不例外
            loadedClientType = clientType
            withContext(Dispatchers.IO) {
                useRemoteService { srv ->
                    srv.setup(pathConfig.rootDir, appSettings.debugMode.value)
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
                                    pathConfig.rootDir
                                )
                            }.onFailure { Timber.w(it, "LogcatService startCapture failed") }
                        }
                    }

                    val maa = srv.maaCoreService
                    val isGlobal = resourceProfileOf(clientType).isNotEmpty()

                    copyTasksJson(pathConfig.cacheResourceDir)

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

                    if (isGlobal) {
                        copyTasksJson(pathConfig.globalCacheResourceDir(clientType).absolutePath)
                    }

                    followUps.forEach { loadResIfExists(maa, it) }

                    if (appSettings.tasksOverrideEnabled.value) {
                        loadResIfExists(maa, pathConfig.overridesDir)
                    }

                    _state.value = State.Ready
                    Result.success(Unit)
                }
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
        withTimeoutOrNull(SERVICE_RESTART_TIMEOUT_MS) {
            RemoteServiceManager.state.first { it !is RemoteServiceManager.ServiceState.Connected }
        }
        // destroy 是 oneway，给旧进程留点退出窗口
        delay(SERVICE_RESTART_SETTLE_MS)
    }

    private suspend fun doLoadDepsInfo(clientType: String) {
        val displayLanguage = ResourceDataManager.displayLanguageCode(
            resolveSelectedLanguage(appSettings.language.value)
        )
        withTimeout(30_000) {
            withContext(Dispatchers.IO) {
                listOf(
                    async { resourceDataManager.load(clientType, displayLanguage) },
                    async { itemHelper.load() },
                    async { activityManager.load(clientType) }
                )
            }.awaitAll()
        }
    }

    private fun loadResIfExists(maa: MaaCoreService, parentDir: String): Boolean {
        val resDir = File(parentDir, "resource")
        if (!resDir.exists()) {
            Timber.d("Resource directory not found, skipping: ${resDir.absolutePath}")
            return true
        }
        return maa.LoadResource(parentDir).also { ok ->
            if (ok) Timber.i("LoadResource succeeded: $parentDir")
            else Timber.w("LoadResource failed: $parentDir")
        }
    }

    /** 资源档与已加载的不一致时重新加载，必要时连带重启进程 */
    suspend fun ensureLoaded(clientType: String = chainState.clientType): Result<Unit> {
        return when (val s = _state.value) {
            is State.Ready -> ensureProfile(clientType)
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
        if (loaded != null && resourceProfileOf(loaded) == resourceProfileOf(clientType)) {
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
        _state.value = State.NotLoaded
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

    /**
     * Copy tasks.json to tasks/tasks.json (compatible with new directory structure)
     */
    private fun copyTasksJson(resourcePath: String) {
        try {
            val src = File(resourcePath, "tasks.json")
            if (!src.exists()) return
            val destDir = File(resourcePath, "tasks").apply { mkdirs() }
            val dest = File(destDir, "tasks.json")
            if (dest.exists() && dest.length() == src.length() && dest.lastModified() >= src.lastModified()) {
                return
            }
            src.copyTo(dest, overwrite = true)
            Timber.d("copyTasksJson: ${src.absolutePath} -> ${dest.absolutePath}")
        } catch (e: Exception) {
            Timber.w(e, "copyTasksJson failed: $resourcePath")
        }
    }

    companion object {
        private const val SERVICE_RESTART_TIMEOUT_MS = 5_000L
        private const val SERVICE_RESTART_SETTLE_MS = 500L

        /** 只加载 base 资源的客户端 */
        private val BASE_ONLY_CLIENT_TYPES = setOf("", "Official", "Bilibili")

        /** 官服/B服/空共用 base 归一为空串，各 global 客户端各自一档 */
        internal fun resourceProfileOf(clientType: String): String =
            if (clientType in BASE_ONLY_CLIENT_TYPES) "" else clientType

        /** 换档必须换进程，global 之间互切也算 */
        internal fun requiresServiceRestart(loaded: String?, target: String): Boolean =
            loaded != null && resourceProfileOf(loaded) != resourceProfileOf(target)
    }
}
