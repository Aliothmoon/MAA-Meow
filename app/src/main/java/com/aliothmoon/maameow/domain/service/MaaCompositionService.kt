package com.aliothmoon.maameow.domain.service

import android.content.Context
import com.alibaba.fastjson2.JSON
import com.aliothmoon.maameow.MaaCoreCallback
import com.aliothmoon.maameow.MaaCoreService
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
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
import com.aliothmoon.maameow.maa.callback.SubTaskHandler
import com.aliothmoon.maameow.maa.callback.TaskChainStatusTracker
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.manager.RemoteServiceManager.useRemoteService
import com.aliothmoon.maameow.utils.Misc
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    private val context: Context,
    private val resourceLoader: MaaResourceLoader,
    private val appSettings: AppSettingsManager,
    private val unifiedStateDispatcher: UnifiedStateDispatcher,
    private val sessionLogger: MaaSessionLogger,
    private val activityManager: ActivityManager,
    private val appWatchdog: AppWatchdog,
    private val taskChainState: TaskChainState,
    private val subTaskHandler: SubTaskHandler,
    private val taskChainStatusTracker: TaskChainStatusTracker,
    private val notificationService: ExternalNotificationService,
    private val notificationSettings: NotificationSettingsManager,
) : MaaExecutionStateHolder {

    private val _state = MutableStateFlow(MaaExecutionState.IDLE)
    val state: StateFlow<MaaExecutionState> = _state.asStateFlow()

    private val defaultResolution = DefaultDisplayConfig.Resolution(
        DefaultDisplayConfig.WIDTH, DefaultDisplayConfig.HEIGHT, DefaultDisplayConfig.DPI
    )
    private val _displayResolution = MutableStateFlow(defaultResolution)
    val displayResolution: StateFlow<DefaultDisplayConfig.Resolution> =
        _displayResolution.asStateFlow()

    override fun reportRunState(state: MaaExecutionState) {
        setRunState(state)
    }

    private fun setRunState(state: MaaExecutionState) {
        _state.value = state
        when (state) {
            MaaExecutionState.STARTING ->
                TaskExecutionService.start(context)

            MaaExecutionState.IDLE, MaaExecutionState.ERROR ->
                TaskExecutionService.stop(context)

            else -> {}
        }
    }

    private val callbackDispatcher: MaaCallbackDispatcher by inject(MaaCallbackDispatcher::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        data object Success : StopResult()
        data object Failed : StopResult()
    }


    init {
        scope.launch {
            unifiedStateDispatcher.serviceDiedEvent.collect {
                appWatchdog.stopWatching()
                setRunState(MaaExecutionState.ERROR)
                sessionLogger.completeSessionAndWait(
                    "SERVICE_DIED",
                    "MAA服务异常终止",
                    LogLevel.ERROR
                )
                if (notificationSettings.sendOnServiceDied.value) {
                    notificationService.send("服务异常", "MAA 服务意外终止")
                }
            }
        }

        scope.launch {
            appWatchdog.appDiedEvent.collect { packageName ->
                Timber.w("App watchdog detected app died: %s", packageName)
                sessionLogger.appendAndWait(
                    "游戏进程未启动或被异常关闭($packageName)",
                    LogLevel.WARNING
                )
            }
        }
    }

    fun handleCallback(msg: Int, json: String?) {
        if (onAsyncConnectCallback(msg, json)) return
        callbackDispatcher.dispatch(msg, json)
    }

    val callback = object : MaaCoreCallback.Stub() {
        override fun onCallback(msg: Int, json: String?) = handleCallback(msg, json)
    }

    private fun onAsyncConnectCallback(msg: Int, json: String?): Boolean {
        if (msg != AsstMsg.AsyncCallInfo.value) return false
        val deferred = connectDeferred.get() ?: return true
        val obj = JSON.parseObject(json)
        val details = obj.getJSONObject("details")
        if (details != null) {
            val ret = details.getBooleanValue("ret", false)
            deferred.complete(ret)
        }
        return true
    }

    suspend fun start(
        tasks: List<MaaTaskParams>,
        clientType: String = taskChainState.getClientType(),
        onSessionStarted: (suspend () -> Unit)? = null
    ): StartResult = executeStart(
        tasks = tasks,
        clientType = clientType,
        startMessage = "开始执行任务，共 ${tasks.size} 项",
        successMessage = "任务开始运行",
        onSessionStarted = onSessionStarted,
    )

    suspend fun startCopilot(
        tasks: List<MaaTaskParams>,
        clientType: String = taskChainState.getClientType()
    ): StartResult = executeStart(
        tasks = tasks,
        clientType = clientType,
        startMessage = "开始执行自动战斗",
        successMessage = "自动战斗开始运行",
    )

    private suspend fun failStart(
        message: String, sessionStatus: String, result: StartResult
    ): StartResult {
        setRunState(MaaExecutionState.ERROR)
        sessionLogger.appendAndWait(message, LogLevel.ERROR)
        sessionLogger.endSessionAndWait(sessionStatus)
        return result
    }

    private suspend fun checkPreconditions(mode: RunMode): StartResult? {
        activityManager.runIfDirty { resourceLoader.load() }
        val loaded = resourceLoader.ensureLoaded()
        if (loaded.isFailure) {
            return failStart(
                "资源加载失败", "RESOURCE_ERROR",
                StartResult.ResourceError(loaded.exceptionOrNull())
            )
        }
        if (mode == RunMode.FOREGROUND) {
            val (width, height) = Misc.getScreenSize(context)
            if (height > width) {
                return failStart(
                    "当前为竖屏，无法在前台模式运行", "PORTRAIT",
                    StartResult.PortraitOrientationError
                )
            }
        }
        return null
    }

    private suspend fun ensureMaaInstance(maa: MaaCoreService): StartResult? {
        if (maa.hasInstance()) return null
        if (!maa.CreateInstance(callback)) {
            return failStart(
                "创建 MaaCore 实例失败", "CREATE_INSTANCE_ERROR",
                StartResult.InitializationError(StartResult.InitializationError.InitPhase.CREATE_INSTANCE)
            )
        }
        if (!maa.SetInstanceOption(TOUCH_MODE, ANDROID)) {
            return failStart(
                "设置触控模式失败", "SET_TOUCH_MODE_ERROR",
                StartResult.InitializationError(StartResult.InitializationError.InitPhase.SET_TOUCH_MODE)
            )
        }
        return null
    }

    private suspend fun asyncConnect(maa: MaaCoreService, config: String): StartResult? {
        val deferred = CompletableDeferred<Boolean>()
        connectDeferred.set(deferred)
        maa.AsyncConnect("", "Android", config, false)
        val ret = withTimeoutOrNull(2000) { deferred.await() }
        connectDeferred.set(null)
        if (ret != true) {
            return failStart(
                "启动 MaaCore 超时或失败", "MAA_CONNECT_ERROR",
                StartResult.ConnectionError(StartResult.ConnectionError.ConnectPhase.MAA_CONNECT)
            )
        }
        return null
    }

    private suspend fun setupDisplayAndConnect(
        service: RemoteService, maa: MaaCoreService, mode: RunMode, clientType: String
    ): StartResult? {
        if (!service.setVirtualDisplayMode(mode.displayMode))
            return failStart(
                "设置显示模式失败", "DISPLAY_MODE_ERROR",
                StartResult.ConnectionError(StartResult.ConnectionError.ConnectPhase.DISPLAY_MODE)
            )
        val displayId = service.startVirtualDisplay()
        if (displayId == -1)
            return failStart(
                "启动虚拟显示失败", "VIRTUAL_DISPLAY_ERROR",
                StartResult.ConnectionError(StartResult.ConnectionError.ConnectPhase.VIRTUAL_DISPLAY)
            )
        val config = when (mode) {
            RunMode.FOREGROUND -> {
                val (w, h) = Misc.getScreenSize(context)
                buildConnectConfig(w, h, displayId)
            }

            RunMode.BACKGROUND -> {
                val r = resolveAndSetResolution(service, clientType)
                buildConnectConfig(r.width, r.height, displayId)
            }
        }
        return asyncConnect(maa, config)
    }

    private suspend fun appendTasksAndStart(
        maa: MaaCoreService,
        tasks: List<MaaTaskParams>,
        successMessage: String,
    ): StartResult {
        taskChainStatusTracker.clear()
        tasks.forEach { t ->
            sessionLogger.appendToFileOnly("[TaskParams] ${t.type.value}: ${t.params}")
            val taskId = maa.AppendTask(t.type.value, t.params)
            if (taskId > 0) {
                taskChainStatusTracker.register(taskId, t.type.value)
            }
        }
        if (!maa.Start()) {
            return failStart("MaaCore 启动失败", "START_ERROR", StartResult.StartError)
        }
        setRunState(MaaExecutionState.RUNNING)
        appWatchdog.startWatching()
        sessionLogger.appendAndWait(successMessage, LogLevel.SUCCESS)
        return StartResult.Success(maa.GetVersion())
    }

    private suspend fun executeStart(
        tasks: List<MaaTaskParams>,
        clientType: String,
        startMessage: String,
        successMessage: String,
        onSessionStarted: (suspend () -> Unit)? = null,
    ): StartResult {
        setRunState(MaaExecutionState.STARTING)
        sessionLogger.startSession(tasks.map { it.type.value })
        subTaskHandler.resetSessionState()
        onSessionStarted?.invoke()
        sessionLogger.appendAndWait(startMessage, LogLevel.INFO)

        val mode = appSettings.runMode.value
        return withContext(Dispatchers.IO) {
            checkPreconditions(mode)?.let { return@withContext it }

            useRemoteService { service ->
                val maa = service.maaCoreService
                ensureMaaInstance(maa)?.let { return@useRemoteService it }

                setupDisplayAndConnect(
                    service,
                    maa,
                    mode,
                    clientType
                )?.let { return@useRemoteService it }
                appendTasksAndStart(maa, tasks, successMessage)
            }
        }
    }

    private fun resolveAndSetResolution(
        service: RemoteService,
        clientType: String
    ): DefaultDisplayConfig.Resolution {
        val r = DefaultDisplayConfig.resolveResolution(clientType)
        service.setVirtualDisplayResolution(r.width, r.height, r.dpi)
        Timber.i(
            "Virtual display resolution: %dx%d@%d for client=%s",
            r.width,
            r.height,
            r.dpi,
            clientType
        )
        _displayResolution.value = r
        return r
    }

    private fun buildConnectConfig(width: Int, height: Int, displayId: Int): String {
        return buildJsonObject {
            put("library_path", "libbridge.so")
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
                sessionLogger.append(
                    "任务停止，状态: $status",
                    if (it is StopResult.Success) LogLevel.INFO else LogLevel.ERROR
                )
                sessionLogger.endSession(status)
            }
        }
    }

    suspend fun stopVirtualDisplay() {
        appWatchdog.stopWatching()
        _displayResolution.value = defaultResolution
        useRemoteService { it.stopVirtualDisplay() }
    }
}
