package com.aliothmoon.maameow.domain.service

import android.os.IBinder
import android.os.Process
import com.aliothmoon.maameow.MaaCoreService
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.constant.MaaFiles
import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.HotUpdateSync
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.models.ClientResourceProfile
import com.aliothmoon.maameow.manager.LogcatServiceManager
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.manager.RemoteServiceManager.useRemoteService
import com.aliothmoon.maameow.remote.ResourceFiles
import com.aliothmoon.maameow.remote.SetupResult
import com.aliothmoon.maameow.utils.JsonUtils
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.resolveSelectedLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

class MaaResourceLoader(
    private val pathConfig: MaaPathConfig,
    private val appSettings: AppSettingsManager,
    private val chainState: TaskChainState,
    private val hotUpdateSync: HotUpdateSync,
    private val resourceInitService: ResourceInitService,
    private val itemHelper: ItemHelper,
    private val resourceDataManager: ResourceDataManager,
    private val activityManager: ActivityManager,
    private val coreDataPusher: CoreDataPusher,
) {
    // 换档重启期间抑制 reset()，主动解绑会触发 onServiceDisconnected
    private val fullReloadInProgress = AtomicBoolean(false)

    private val loadMutex = Mutex()

    /**
     * 依赖数据（物品/干员/活动 JSON）的加载超时。
     *
     * 只在测试里改小，免得超时用例真等 30 秒；生产路径永远走默认值。
     */
    internal var depsInfoTimeoutMs: Long = 30_000L

    /** 当前提权进程已加载资源的客户端，null = 该进程还没被任何资源档污染 */
    @Volatile
    private var loadedClientType: String? = null

    /** Ready 对应的提权进程；Shizuku 重启后的重绑不经过 Died/Disconnected，只能靠它识别换了进程 */
    @Volatile
    private var loadedBinder: IBinder? = null

    /** 最近一次加载所用的（资源版本, 热更版本, 自定义包名） */
    @Volatile
    private var loadedFiles: Triple<Int, Int, Map<String, String>>? = null

    /**
     * 本进程是否真的把「包名层」加载进 core 过。
     *
     * 不能用 `dir.exists()` 代理：写失败会删目录、外部也可能清掉，两种情况下磁盘状态都与
     * 「core 里有没有旧映射」不再对应，重试路径会误判成「不需要这一层」而直接 Ready，
     * 自定义包名就永久残留（core 等自定义包、App 拉默认包，任务连不上）。
     */
    @Volatile
    private var appliedGamePackageLayer = false

    private fun currentFiles() =
        Triple(resourceInitService.revision.value, hotUpdateSync.revision.value, appSettings.gamePackages.value)

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
        val files = currentFiles()
        loadedFiles = files
        if (!pathConfig.isResourceReady) {
            Timber.e("MaaResourceLoader.load() aborted: resource not ready (version.json missing or app version mismatch)")
            _state.value = State.Failed("资源未就绪，请重新初始化", permanent = true)
            return Result.failure(Exception("Resource not ready"))
        }
        Timber.i("MaaCore resources loading, client type=$clientType")
        try {
            doLoadDepsInfo(clientType)
        } catch (e: TimeoutCancellationException) {
            // `withTimeout` 的超时也是 CancellationException 子类：必须排在下面那支之前，
            // 否则会被当成「上层取消」原样抛出，绕过 `_state = Failed`，
            // 状态永久停在 Loading、`ensureLoaded` 的等待者永远不醒、启动互斥锁被长占
            Timber.w(e, "doLoadDepsInfo timed out, continuing without deps info")
            // 超时可能恰好发生在父协程已被取消之后：别带着已取消的协程继续去 unbind/重启 core
            currentCoroutineContext().ensureActive()
        } catch (e: CancellationException) {
            // 协程取消不能被当成「依赖数据加载失败」吞掉，否则上层取消语义就断了
            throw e
        } catch (e: Exception) {
            Timber.e(e, "doLoadDepsInfo error")
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
                // 先于投递写好，独立目录才能带上。
                // 标记要在写层**之前**取样：写层本来就会更新标记，写完之后再读，
                // 一次失败的写会自己给自己留下「core 可能持有自定义映射」的证据（磁盘满时永久硬失败）
                val markerBeforeWrite = appliedMarkerExists()
                val gamePackageLayer = writeGamePackageLayer(files.third)

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

                // 紧跟主资源，后续层照常覆盖 options。
                // 没有自定义包名时这层只是「把默认值再写一遍」，失败无妨（主配置本身就带默认映射）；
                // 有自定义包名时不能放行，否则 core 会拉起默认包。
                // 判据用原始 key：`ClientResourceProfile.of` 会把 Official/Bilibili/"" 折叠成同一档，
                // 只给 Bilibili 设覆盖、却以 Official 跑时会被误判成 active。
                val overridesActive = files.third.containsKey(clientType)
                // 映射要等所有后续层都 Load 完再发布：后续层里的 config.json 也会带 packageName，
                // 提前发布会让 App 侧拿到中间结果（与 core 最终生效的映射不一致）
                var appliedLayerNames: Map<String, String>? = null
                when (val layer = gamePackageLayer) {
                    is GamePackageLayer.Written -> {
                        if (loadResIfExists(maa, layer.dir)) {
                            appliedGamePackageLayer = true
                            // 发布推迟到所有后续层加载之后，见 appliedLayerNames 注释
                            appliedLayerNames = layer.packageNames
                            // core 确实应用了这份映射，才留下跨进程证据
                            updateAppliedMarker(layer.packageNames, overridesActive)
                        } else {
                            // 不要把 appliedGamePackageLayer 降成 false：core 里可能仍留着上一轮的映射，
                            // 这个 flag 是「本进程曾成功推进过自定义映射」的证据；跨进程还要看 `.applied`。
                            if (coreMayHoldCustomMapping(overridesActive, markerBeforeWrite)) {
                                val msg = "Failed to load game package config"
                                _state.value = State.Failed(msg)
                                Timber.e("LoadResource failed: ${layer.dir}")
                                return@useRemoteService Result.failure(Exception(msg))
                            }
                            Timber.w("Load game package layer failed, falling back to defaults")
                        }
                    }

                    GamePackageLayer.NotNeeded -> {
                        Packages.appliedMapping = emptyMap()
                        appliedGamePackageLayer = false
                    }

                    is GamePackageLayer.Failed -> when (layer.reason) {
                        // 清空覆盖后要把 core 改回基底映射，但基底读不到：core 里可能还留着自定义包名，
                        // 这种情况必须失败，否则「恢复默认包名」会静默失效
                        GamePackageLayer.Reason.RollbackNeedsBase -> {
                            val msg = "Failed to restore game package mapping"
                            _state.value = State.Failed(msg)
                            return@useRemoteService Result.failure(Exception(msg))
                        }
                        // 写盘失败只在「用户确实要自定义包名」或「本轮之前已把自定义映射推进 core」时硬失败；
                        // 否则主资源本身完好，没必要让首页报错 + 每次启动无退避重试
                        GamePackageLayer.Reason.WriteFailed -> {
                            if (coreMayHoldCustomMapping(overridesActive, markerBeforeWrite)) {
                                val msg = "Failed to write game package config"
                                _state.value = State.Failed(msg)
                                return@useRemoteService Result.failure(Exception(msg))
                            }
                            Timber.w("Write game package layer failed, continuing with resource config")
                        }
                    }
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

                // 到这一步 core 已把全部层 Load 完，再用最终生效的映射发布给 App 侧
                appliedLayerNames?.let { Packages.appliedMapping = it }

                loadedBinder = srv.asBinder()
                _state.value = State.Ready
                Result.success(Unit)
            }
        } catch (e: CancellationException) {
            // 取消不能被吞成 Failed：否则上层「本来已经不想要这次加载」会变成一次失败态，
            // 还带着已污染的 loadedClientType
            throw e
        } catch (e: Exception) {
            Timber.e(e, "MaaResourceLoader error")
            _state.value = State.Failed(e.message ?: "Resource loading exception")
            Result.failure(e)
        } finally {
            val wasSuppressing = fullReloadInProgress.getAndSet(false)
            // 抑制窗口内服务若真死了（或已换成新进程），那次 reset() 被吞了，这里补回来。
            // 只看「是否 Connected」不够：换进程后新进程加载失败时服务仍是 Connected，
            // 旧映射会活到下一次成功加载（窗口内 App 与 core 对包名的判断不一致）。
            val connectedBinder = (RemoteServiceManager.state.value as? RemoteServiceManager.ServiceState.Connected)
                ?.service?.asBinder()
            val replaced = connectedBinder != null && loadedBinder !== connectedBinder
            if (wasSuppressing && (connectedBinder == null || replaced)) {
                Timber.w("Service replaced or not connected after profile switch, dropping loaded state")
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

    /**
     * 资源相关的依赖数据（活动关卡/物品索引/干员数据）与 core 资源同步加载。
     *
     * 这些是 App 侧解析用的，与 core 的 LoadResource 无先后依赖，所以放在主资源之前并发跑：
     * 失败只记日志，不影响 core 资源加载结果。
     */
    private suspend fun doLoadDepsInfo(clientType: String) {
        val displayLanguage = appSettings.displayLanguage
        // revision 必须和 GameMetadataLoader 用同一套记账：传默认 0 会让
        // ResourceDataManager 的 cachedBase 校验失配 —— 每次 core 加载都重新解析一遍
        // 干员/物品/活动 JSON，还会把缓存键写成 revision 0
        val revision = resourceInitService.revision.value
        withTimeout(depsInfoTimeoutMs.milliseconds) {
            withContext(Dispatchers.IO) {
                listOf(
                    async { resourceDataManager.load(clientType, displayLanguage, revision) },
                    // 物品索引也按客户端档读：全球服的名字在 global/{clientType}/resource 下
                    async { itemHelper.load(clientType) },
                )
            }.awaitAll()
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

            is State.Failed -> if (s.permanent && loadedFiles == currentFiles()) {
                // 资源缺失且文件没动过，重试无意义
                Result.failure(Exception(s.message))
            } else {
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
        val sameProfile = loaded != null && ClientResourceProfile.of(loaded) == ClientResourceProfile.of(clientType)
        if (sameProfile && loadedFiles == currentFiles()) {
            return Result.success(Unit)
        }
        Timber.i("Loaded client %s (files %s) is stale for %s, reloading", loaded, loadedFiles, clientType)
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
        // 进程没了，core 侧的包名映射也就没了：不能再用「本进程应用过」和旧映射去判安装状态
        forgetAppliedMapping()
        _state.value = State.NotLoaded
    }

    /**
     * 已连上的不是加载资源的那个进程时作废 Ready，返回是否作废了。
     *
     * 只看 binder 身份、不看当前状态：进程被替换后若新进程加载失败（state=Failed），
     * 旧映射同样必须归零，否则 App 侧会继续用旧包名判「已安装」。
     */
    fun resetIfStale(service: RemoteService): Boolean {
        val loaded = loadedBinder ?: return false
        if (loaded === service.asBinder()) return false
        Timber.w("Elevated service replaced since resources were loaded, dropping loaded state")
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
        // 不在这里清 `Packages.appliedMapping`：映射的生命周期跟 core 进程走，
        // 而 core 进程还活着（invalidate 只表示「该重载了」）。清掉会让「启动游戏」这类
        // 不做 prepareResources 守卫的路径拿编译期默认包名去拉错包，且这个窗口不会自愈。
        _state.value = State.NotLoaded
    }

    /**
     * core 进程可能仍持有自定义包名映射的跨进程证据。
     *
     * 三个来源：本轮确有覆盖、本进程成功推进过该层、或上一次写层的进程留下 `.applied` 标记
     * （core 进程能活过 App 重启，只认内存 flag 会在重启后瞎判：用户清空覆盖 + 本次写盘失败时
     * 会静默 Ready，而 core 还在等旧自定义包）。
     *
     * [markerBeforeWrite] 必须是「开始写这一层之前」的快照：写层成功会刷新标记，
     * 写完之后再读会让「刚刚失败的那次写」成为它自己的证据。
     */
    private fun coreMayHoldCustomMapping(overridesActive: Boolean, markerBeforeWrite: Boolean): Boolean =
        overridesActive || appliedGamePackageLayer || markerBeforeWrite

    private fun appliedMarkerExists(): Boolean = runCatching {
        File(File(pathConfig.rootDir, MaaFiles.GAME_PACKAGE_DIR), APPLIED_MARKER).exists()
    }.getOrDefault(false)

    /**
     * core 应用成功后刷新跨进程证据。
     *
     * 没有自定义覆盖时这份映射就是基底映射：core 已被改回默认，标记的使命结束，
     * 必须删掉 —— 否则下次「清空覆盖 + 基底 config.json 读不到」会因这条旧证据硬失败。
     */
    private fun updateAppliedMarker(packageNames: Map<String, String>, overridesActive: Boolean) {
        val marker = File(File(pathConfig.rootDir, MaaFiles.GAME_PACKAGE_DIR), APPLIED_MARKER)
        runCatching {
            if (overridesActive) {
                marker.parentFile?.mkdirs()
                marker.writeText(JsonObject(packageNames.mapValues { JsonPrimitive(it.value) }).toString())
            } else if (marker.exists()) {
                marker.delete()
            }
        }.onFailure { Timber.w(it, "Update applied marker failed") }
    }

    /** 新进程没加载过任何层、core 侧也没有映射：App 侧不能再拿旧映射判「已安装」 */
    private fun forgetAppliedMapping() {
        appliedGamePackageLayer = false
        Packages.appliedMapping = emptyMap()
    }

    /**
     * 包名层必须始终可回滚。
     *
     * core 的 packageName 是「按客户端合并」语义：以前只在有 overrides 时才写这一层，
     * 于是「恢复默认包名」只是删掉本地目录、跳过这层，core 进程里旧的映射还在 ——
     * App 拉默认包、core 等自定义包，任务连不上，直到进程重启才恢复。
     * 现在始终写出「基底 + 覆盖」的完整映射，清空覆盖时 core 会被改回默认值。
     */
    private fun writeGamePackageLayer(overrides: Map<String, String>): GamePackageLayer {
        val dir = File(pathConfig.rootDir, MaaFiles.GAME_PACKAGE_DIR)
        val base = runCatching { File(pathConfig.resourceDir, "config.json").readText() }.getOrNull()
        if (base == null) {
            Timber.w("Read base config.json failed, cannot compute game package layer")
            // 故意不删旧目录：磁盘存在性不是「core 里应用过这一层」的可靠代理。
            // 跨进程的代理是标记文件 .applied：上一次成功写层的进程会留下它。
            // 没有覆盖、也没有标记 → core 侧（若还活着）用的就是基底映射，这层不需要写。
            // 有覆盖或存在标记 → 无法算出「改回基底」的映射，必须失败，否则清空覆盖会静默失效。
            val marker = runCatching { File(dir, APPLIED_MARKER).readText() }.getOrNull()
            return if (overrides.isEmpty() && marker == null && !appliedGamePackageLayer) {
                GamePackageLayer.NotNeeded
            } else {
                GamePackageLayer.Failed(GamePackageLayer.Reason.RollbackNeedsBase)
            }
        }
        val written = runCatching {
            val merged = Packages.defaults + basePackageNames(base) + overrides
            val file = File(dir, "resource/config.json")
            file.parentFile?.mkdirs()
            // 先写临时文件再 rename：写失败时保留上一次成功写入的整层（否则现场丢了，
            // 下次 base 也读不到就只剩 NotNeeded 静默路径）
            val tmp = File(file.parentFile, "config.json.tmp")
            // 标记不在这里写：它的含义是「core 可能已经把这份映射应用过」，
            // 而「写成功」离「core 应用成功」还差 prepare + LoadResource 两步。
            // 提前写会让一次失败的加载给下次留下假证据（清空覆盖 + 基底缺失时永久硬失败）。
            tmp.writeText(gamePackageConfig(base, merged))
            java.nio.file.Files.move(
                tmp.toPath(),
                file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
            GamePackageLayer.Written(dir.absolutePath, merged)
        }.onFailure {
            Timber.w(it, "Write game package layer failed")
            runCatching { File(dir, "resource/config.json.tmp").delete() }
        }.getOrNull()
        return written ?: GamePackageLayer.Failed(GamePackageLayer.Reason.WriteFailed)
    }

    /** 写包名层的三种结果：写成功 / 不需要 / 写了但失败（失败时不能悄悄当 Ready） */
    private sealed interface GamePackageLayer {
        data class Written(val dir: String, val packageNames: Map<String, String>) : GamePackageLayer
        data object NotNeeded : GamePackageLayer
        data class Failed(val reason: Reason) : GamePackageLayer

        enum class Reason {
            /** 清空覆盖后需要把 core 改回基底映射，但基底读不到 —— 硬失败 */
            RollbackNeedsBase,

            /** 写盘失败：有自定义包名或用过这层时硬失败，否则降级为警告 */
            WriteFailed,
        }
    }

    private fun copyTasksJson(resourcePath: String) {
        if (!ResourceFiles.deriveTasksJson(File(resourcePath))) Timber.w("copyTasksJson failed: $resourcePath")
    }

    companion object {
        private const val SERVICE_RESTART_TIMEOUT_MS = 5_000L
        private const val SERVICE_RESTART_SETTLE_MS = 500L

        /** 上一次成功写入包名层的映射标记（跨进程证据：core 进程可能活过 App 重启） */
        internal const val APPLIED_MARKER = ".applied"

        /** 换档必须换进程，global 之间互切也算 */
        internal fun requiresServiceRestart(loaded: String?, target: String): Boolean =
            loaded != null && ClientResourceProfile.of(loaded) != ClientResourceProfile.of(target)

        /**
         * core 解析 config.json 时 version/options/packageName/connection 缺一不可，packageName 按客户端合并。
         *
         * 只替换 packageName：`connection` 保持基底原样（基底里是 ADB / 模拟器连接模板，
         * 清空会让「后加载覆盖」语义下的所有用户丢模板）。
         */
        internal fun gamePackageConfig(base: String, packageNames: Map<String, String>): String {
            val config = JsonUtils.common.parseToJsonElement(base).jsonObject
            return JsonObject(
                config + mapOf(
                    "packageName" to JsonObject(packageNames.mapValues { JsonPrimitive(it.value) }),
                )
            ).toString()
        }

        /** 基底 config.json 自带的 packageName：资源热更可能更新过，优先于编译期常量 */
        internal fun basePackageNames(base: String): Map<String, String> =
            runCatching {
                JsonUtils.common.parseToJsonElement(base).jsonObject["packageName"]
                    ?.jsonObject
                    // 逐条判型：mapValues + jsonPrimitive 遇到非字符串会整表抛异常，
                    // 被外层 runCatching 吞成空表，等于把整套基底包名丢掉
                    ?.mapNotNull { (key, value) ->
                        val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                        // 空串/非包名的条目要丢掉：写进 config.json 会让 core 去等一个不存在的包
                        if (text != null && Packages.PACKAGE_NAME.matches(text)) key to text else null
                    }
                    ?.toMap()
            }.getOrNull().orEmpty()
    }
}
