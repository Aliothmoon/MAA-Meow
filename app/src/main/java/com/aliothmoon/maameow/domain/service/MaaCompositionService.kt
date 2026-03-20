package com.aliothmoon.maameow.domain.service

import android.content.Context
import com.alibaba.fastjson2.JSON
import com.aliothmoon.maameow.MaaCoreCallback
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.maa.AsstMsg
import com.aliothmoon.maameow.maa.MaaInstanceOptions.ANDROID
import com.aliothmoon.maameow.maa.MaaInstanceOptions.TOUCH_MODE
import com.aliothmoon.maameow.maa.callback.MaaCallbackDispatcher
import com.aliothmoon.maameow.maa.callback.MaaExecutionStateHolder
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.manager.RemoteServiceManager.useRemoteService
import com.aliothmoon.maameow.utils.Misc
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

class MaaCompositionService(
    private val applicationContext: Context,
    private val resourceLoader: MaaResourceLoader,
    private val appSettings: AppSettingsManager,
    private val unifiedStateDispatcher: UnifiedStateDispatcher,
    private val runtimeLogCenter: RuntimeLogCenter,
    private val activityManager: ActivityManager,
    private val appWatchdog: AppWatchdog,
    private val taskChainState: TaskChainState,
) : MaaExecutionStateHolder {

    private val _state = MutableStateFlow(MaaExecutionState.IDLE)
    val state: StateFlow<MaaExecutionState> = _state.asStateFlow()

    private val defaultResolution = DefaultDisplayConfig.Resolution(
        DefaultDisplayConfig.WIDTH, DefaultDisplayConfig.HEIGHT, DefaultDisplayConfig.DPI
    )
    private val _displayResolution = MutableStateFlow(defaultResolution)
    val displayResolution: StateFlow<DefaultDisplayConfig.Resolution> = _displayResolution.asStateFlow()

    override fun reportRunState(state: MaaExecutionState) {
        setRunState(state)
    }

    private fun setRunState(state: MaaExecutionState) {
        _state.value = state
        when (state) {
            MaaExecutionState.STARTING -> TaskExecutionService.start(applicationContext)
            MaaExecutionState.IDLE, MaaExecutionState.ERROR -> TaskExecutionService.stop(applicationContext)
            else -> {}
        }
    }

    private val callbackDispatcher: MaaCallbackDispatcher by inject(MaaCallbackDispatcher::class.java)

    private val scope = CoroutineScope(Dispatchers.IO)

    private val connectDeferred = AtomicReference<CompletableDeferred<Boolean>?>()

    sealed class StartResult {
        data class Success(val version: String) : StartResult()

        /** 资源加载失败（网络/IO/解压） */
        data class ResourceError(
            val exception: Throwable? = null
        ) : StartResult()

        /** MaaCore 实例初始化失败（创建实例、设置选项） */
        data class InitializationError(
            val phase: InitPhase,
        ) : StartResult() {
            enum class InitPhase {
                CREATE_INSTANCE,
                SET_TOUCH_MODE,
            }
        }

        /** 显示/连接层失败（虚拟屏幕、连接） */
        data class ConnectionError(
            val phase: ConnectPhase,
        ) : StartResult() {
            enum class ConnectPhase {
                DISPLAY_MODE,
                VIRTUAL_DISPLAY,
                MAA_CONNECT,
            }
        }

        /** MaaCore 运行时启动失败 */
        data object StartError : StartResult()

        /** 前台模式下检测到竖屏（高 > 宽），需要横屏才能运行 */
        data object PortraitOrientationError : StartResult()
    }

    sealed class StopResult {
        object Success : StopResult()
        object Failed : StopResult()
    }


    init {
        scope.launch {
            unifiedStateDispatcher.serviceDiedEvent.collect {
                appWatchdog.stopWatching()
                setRunState(MaaExecutionState.ERROR)
                runtimeLogCenter.completeSessionAndWait(
                    "SERVICE_DIED",
                    "MAA服务异常终止",
                    LogLevel.ERROR
                )
            }
        }

        scope.launch {
            appWatchdog.appDiedEvent.collect { packageName ->
                Timber.w("App watchdog detected app died: %s", packageName)
                runtimeLogCenter.appendAndWait(
                    "游戏进程未启动或被异常关闭($packageName)",
                    LogLevel.WARNING
                )
            }
        }
    }

    fun handleCallback(msg: Int, json: String?) {
        onAsyncConnectCallback(msg, json)
        callbackDispatcher.dispatch(msg, json)
    }

    val callback = object : MaaCoreCallback.Stub() {
        override fun onCallback(msg: Int, json: String?) = handleCallback(msg, json)
    }

    private fun onAsyncConnectCallback(msg: Int, json: String?) {
        if (msg == AsstMsg.AsyncCallInfo.value) {
            val deferred = connectDeferred.get() ?: return
            val obj = JSON.parseObject(json)
            val details = obj.getJSONObject("details")
            if (details != null) {
                val ret = details.getBooleanValue("ret", false)
                deferred.complete(ret)
            }
        }
    }

    suspend fun start(tasks: List<MaaTaskParams>, clientType: String = taskChainState.getClientType(), onSessionStarted: (suspend () -> Unit)? = null): StartResult {
        setRunState(MaaExecutionState.STARTING)
        val taskNames = tasks.map { it.type.value }
        runtimeLogCenter.startSession(taskNames)
        onSessionStarted?.invoke()
        runtimeLogCenter.appendAndWait("开始执行任务，共 ${tasks.size} 项", LogLevel.INFO)
        return withContext(Dispatchers.IO) {
            activityManager.runIfDirty {
                resourceLoader.load()
            }
            val loaded = resourceLoader.ensureLoaded()
            if (loaded.isFailure) {
                setRunState(MaaExecutionState.ERROR)
                runtimeLogCenter.appendAndWait("资源加载失败", LogLevel.ERROR)
                runtimeLogCenter.endSessionAndWait("RESOURCE_ERROR")
                return@withContext StartResult.ResourceError(loaded.exceptionOrNull())
            }
            val mode = appSettings.runMode.value
            if (mode == RunMode.FOREGROUND) {
                val (width, height) = Misc.getScreenSize(applicationContext)
                if (height > width) {
                    setRunState(MaaExecutionState.ERROR)
                    runtimeLogCenter.appendAndWait("当前为竖屏，无法在前台模式运行", LogLevel.ERROR)
                    runtimeLogCenter.endSessionAndWait("PORTRAIT")
                    return@withContext StartResult.PortraitOrientationError
                }
            }

            return@withContext useRemoteService {
                val maa = it.maaCoreService
                if (!maa.hasInstance()) {
                    val ret = maa.CreateInstance(callback)
                    if (!ret) {
                        setRunState(MaaExecutionState.ERROR)
                        runtimeLogCenter.appendAndWait("创建 MaaCore 实例失败", LogLevel.ERROR)
                        runtimeLogCenter.endSessionAndWait("CREATE_INSTANCE_ERROR")
                        return@useRemoteService StartResult.InitializationError(StartResult.InitializationError.InitPhase.CREATE_INSTANCE)
                    }
                    if (!maa.SetInstanceOption(TOUCH_MODE, ANDROID)) {
                        setRunState(MaaExecutionState.ERROR)
                        runtimeLogCenter.appendAndWait("设置触控模式失败", LogLevel.ERROR)
                        runtimeLogCenter.endSessionAndWait("SET_TOUCH_MODE_ERROR")
                        return@useRemoteService StartResult.InitializationError(StartResult.InitializationError.InitPhase.SET_TOUCH_MODE)
                    }
                }


                if (!it.setVirtualDisplayMode(mode.displayMode)) {
                    setRunState(MaaExecutionState.ERROR)
                    runtimeLogCenter.appendAndWait("设置显示模式失败", LogLevel.ERROR)
                    runtimeLogCenter.endSessionAndWait("DISPLAY_MODE_ERROR")
                    return@useRemoteService StartResult.ConnectionError(StartResult.ConnectionError.ConnectPhase.DISPLAY_MODE)
                }
                val resolution = resolveAndSetResolution(it, mode, clientType)
                val displayId = it.startVirtualDisplay()
                if (displayId == -1) {
                    setRunState(MaaExecutionState.ERROR)
                    runtimeLogCenter.appendAndWait("启动虚拟显示失败", LogLevel.ERROR)
                    runtimeLogCenter.endSessionAndWait("VIRTUAL_DISPLAY_ERROR")
                    return@useRemoteService StartResult.ConnectionError(StartResult.ConnectionError.ConnectPhase.VIRTUAL_DISPLAY)
                }
                val config = when (mode) {
                    RunMode.FOREGROUND -> {
                        val (width, height) = Misc.getScreenSize(applicationContext)
                        buildConnectConfig(width, height, displayId)
                    }

                    RunMode.BACKGROUND -> {
                        buildConnectConfig(
                            resolution.width,
                            resolution.height,
                            displayId
                        )
                    }
                }

                val deferred = CompletableDeferred<Boolean>()
                connectDeferred.set(deferred)
                maa.AsyncConnect("", "Android", config, false)
                val ret = withTimeoutOrNull(2000) {
                    deferred.await()
                }
                connectDeferred.set(null)
                if (ret != true) {
                    setRunState(MaaExecutionState.ERROR)
                    runtimeLogCenter.appendAndWait("启动 MaaCore 超时或失败", LogLevel.ERROR)
                    runtimeLogCenter.endSessionAndWait("MAA_CONNECT_ERROR")
                    return@useRemoteService StartResult.ConnectionError(StartResult.ConnectionError.ConnectPhase.MAA_CONNECT)
                }

                tasks.forEach { t ->
                    runtimeLogCenter.appendToFileOnly("[TaskParams] ${t.type.value}: ${t.params}")
                    maa.AppendTask(t.type.value, t.params)
                }
                if (!maa.Start()) {
                    setRunState(MaaExecutionState.ERROR)
                    runtimeLogCenter.appendAndWait("MaaCore 启动失败", LogLevel.ERROR)
                    runtimeLogCenter.endSessionAndWait("START_ERROR")
                    return@useRemoteService StartResult.StartError
                }
                setRunState(MaaExecutionState.RUNNING)
                appWatchdog.startWatching()
                runtimeLogCenter.appendAndWait("任务开始运行", LogLevel.SUCCESS)
                return@useRemoteService StartResult.Success(maa.GetVersion())
            }
        }
    }

    suspend fun startCopilot(task: MaaTaskParams, clientType: String = taskChainState.getClientType()): StartResult {
        setRunState(MaaExecutionState.STARTING)
        runtimeLogCenter.startSession(listOf(task.type.value))
        runtimeLogCenter.appendAndWait("开始执行自动战斗", LogLevel.INFO)
        return withContext(Dispatchers.IO) {
            activityManager.runIfDirty {
                resourceLoader.load()
            }
            val loaded = resourceLoader.ensureLoaded()
            if (loaded.isFailure) {
                setRunState(MaaExecutionState.ERROR)
                runtimeLogCenter.appendAndWait("资源加载失败", LogLevel.ERROR)
                runtimeLogCenter.endSessionAndWait("RESOURCE_ERROR")
                return@withContext StartResult.ResourceError(loaded.exceptionOrNull())
            }
            val mode = appSettings.runMode.value
            if (mode == RunMode.FOREGROUND) {
                val (width, height) = Misc.getScreenSize(applicationContext)
                if (height > width) {
                    setRunState(MaaExecutionState.ERROR)
                    runtimeLogCenter.appendAndWait("当前为竖屏,无法在前台模式运行", LogLevel.ERROR)
                    runtimeLogCenter.endSessionAndWait("PORTRAIT")
                    return@withContext StartResult.PortraitOrientationError
                }
            }

            return@withContext useRemoteService {
                val maa = it.maaCoreService
                if (!maa.hasInstance()) {
                    val ret = maa.CreateInstance(callback)
                    if (!ret) {
                        setRunState(MaaExecutionState.ERROR)
                        runtimeLogCenter.appendAndWait("创建 MaaCore 实例失败", LogLevel.ERROR)
                        runtimeLogCenter.endSessionAndWait("CREATE_INSTANCE_ERROR")
                        return@useRemoteService StartResult.InitializationError(StartResult.InitializationError.InitPhase.CREATE_INSTANCE)
                    }
                    if (!maa.SetInstanceOption(TOUCH_MODE, ANDROID)) {
                        setRunState(MaaExecutionState.ERROR)
                        runtimeLogCenter.appendAndWait("设置触控模式失败", LogLevel.ERROR)
                        runtimeLogCenter.endSessionAndWait("SET_TOUCH_MODE_ERROR")
                        return@useRemoteService StartResult.InitializationError(StartResult.InitializationError.InitPhase.SET_TOUCH_MODE)
                    }
                }

                if (!it.setVirtualDisplayMode(mode.displayMode)) {
                    setRunState(MaaExecutionState.ERROR)
                    runtimeLogCenter.appendAndWait("设置显示模式失败", LogLevel.ERROR)
                    runtimeLogCenter.endSessionAndWait("DISPLAY_MODE_ERROR")
                    return@useRemoteService StartResult.ConnectionError(StartResult.ConnectionError.ConnectPhase.DISPLAY_MODE)
                }
                val resolution = resolveAndSetResolution(it, mode, clientType)
                val displayId = it.startVirtualDisplay()
                if (displayId == -1) {
                    setRunState(MaaExecutionState.ERROR)
                    runtimeLogCenter.appendAndWait("启动虚拟显示失败", LogLevel.ERROR)
                    runtimeLogCenter.endSessionAndWait("VIRTUAL_DISPLAY_ERROR")
                    return@useRemoteService StartResult.ConnectionError(StartResult.ConnectionError.ConnectPhase.VIRTUAL_DISPLAY)
                }
                val config = when (mode) {
                    RunMode.FOREGROUND -> {
                        val (width, height) = Misc.getScreenSize(applicationContext)
                        buildConnectConfig(width, height, displayId)
                    }

                    RunMode.BACKGROUND -> {
                        buildConnectConfig(
                            resolution.width,
                            resolution.height,
                            displayId
                        )
                    }
                }

                val deferred = CompletableDeferred<Boolean>()
                connectDeferred.set(deferred)
                maa.AsyncConnect("", "Android", config, false)
                val ret = withTimeoutOrNull(2000) {
                    deferred.await()
                }
                connectDeferred.set(null)
                if (ret != true) {
                    setRunState(MaaExecutionState.ERROR)
                    runtimeLogCenter.appendAndWait("启动 MaaCore 超时或失败", LogLevel.ERROR)
                    runtimeLogCenter.endSessionAndWait("MAA_CONNECT_ERROR")
                    return@useRemoteService StartResult.ConnectionError(StartResult.ConnectionError.ConnectPhase.MAA_CONNECT)
                }

                runtimeLogCenter.appendToFileOnly("[TaskParams] ${task.type.value}: ${task.params}")
                maa.AppendTask(task.type.value, task.params)
                if (!maa.Start()) {
                    setRunState(MaaExecutionState.ERROR)
                    runtimeLogCenter.appendAndWait("MaaCore 启动失败", LogLevel.ERROR)
                    runtimeLogCenter.endSessionAndWait("START_ERROR")
                    return@useRemoteService StartResult.StartError
                }
                setRunState(MaaExecutionState.RUNNING)
                appWatchdog.startWatching()
                runtimeLogCenter.appendAndWait("自动战斗开始运行", LogLevel.SUCCESS)
                return@useRemoteService StartResult.Success(maa.GetVersion())
            }
        }
    }

    private fun resolveAndSetResolution(
        service: RemoteService,
        mode: RunMode,
        clientType: String
    ): DefaultDisplayConfig.Resolution {
        val resolution = if (mode == RunMode.BACKGROUND) {
            val r = DefaultDisplayConfig.resolveResolution(clientType)
            service.setVirtualDisplayResolution(r.width, r.height, r.dpi)
            Timber.i("Virtual display resolution: %dx%d@%d for client=%s", r.width, r.height, r.dpi, clientType)
            r
        } else {
            DefaultDisplayConfig.Resolution(
                DefaultDisplayConfig.WIDTH,
                DefaultDisplayConfig.HEIGHT,
                DefaultDisplayConfig.DPI
            )
        }
        _displayResolution.value = resolution
        return resolution
    }

    fun buildConnectConfig(width: Int, height: Int, displayId: Int): String {
        return buildJsonObject {
            put("screen_resolution", buildJsonObject {
                put("width", width)
                put("height", height)
            })
            put("display_id", displayId)
            if (displayId != 0) {
                put("force_stop", true)
            }
        }.toString()
    }

    suspend fun stop(): StopResult {
        return useRemoteService { service ->
            val maa = service.maaCoreService
            when {
                !maa.Running() || maa.Stop() -> StopResult.Success
                else -> StopResult.Failed
            }.also {
                setRunState(MaaExecutionState.IDLE)
                val status = if (it is StopResult.Success) "STOPPED" else "STOP_FAILED"
                runtimeLogCenter.append(
                    "任务停止，状态: $status",
                    if (it is StopResult.Success) LogLevel.INFO else LogLevel.ERROR
                )
                runtimeLogCenter.endSession(status)
            }
        }
    }

    suspend fun stopVirtualDisplay() {
        appWatchdog.stopWatching()
        _displayResolution.value = defaultResolution
        useRemoteService { it.stopVirtualDisplay() }
    }
}
