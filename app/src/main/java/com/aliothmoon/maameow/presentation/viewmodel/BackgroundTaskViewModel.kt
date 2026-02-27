package com.aliothmoon.maameow.presentation.viewmodel

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.data.model.TaskType
import com.aliothmoon.maameow.data.preferences.TaskConfigState
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.RuntimeLogCenter
import com.aliothmoon.maameow.domain.usecase.BuildTaskParamsUseCase
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.manager.RemoteServiceManager.useRemoteService
import com.aliothmoon.maameow.presentation.state.BackgroundTaskState

import com.aliothmoon.maameow.presentation.view.panel.PanelDialogConfirmAction
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogType
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogUiState
import com.aliothmoon.maameow.presentation.view.panel.PanelTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

class BackgroundTaskViewModel(
    val taskConfig: TaskConfigState,
    private val buildTaskParams: BuildTaskParamsUseCase,
    private val compositionService: MaaCompositionService,
    private val runtimeLogCenter: RuntimeLogCenter,
) : ViewModel() {

    private val _state = MutableStateFlow(BackgroundTaskState())
    val state: StateFlow<BackgroundTaskState> = _state.asStateFlow()
    val runtimeLogs: StateFlow<List<LogItem>> = runtimeLogCenter.logs

    private val surfaceRef = AtomicReference<Surface>()

    init {
        observeServiceState()
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            var wasConnected =
                RemoteServiceManager.state.value is RemoteServiceManager.ServiceState.Connected
            RemoteServiceManager.state.collect { state ->
                val isConnected = state is RemoteServiceManager.ServiceState.Connected
                if (!wasConnected && isConnected) {
                    surfaceRef.get()?.let { setRemoteSurface(it) }
                }
                wasConnected = isConnected
            }
        }
    }

    private fun setRemoteSurface(surface: Surface?) {
        Timber.d("setRemoteSurface: %s", surface)
        runCatching {
            runBlocking {
                useRemoteService { it.setMonitorSurface(surface) }
            }
        }.onFailure {
            Timber.w(it, "setMonitorSurface failed")
        }
    }

    fun onTaskEnableChange(taskType: TaskType, enabled: Boolean) {
        viewModelScope.launch {
            taskConfig.updateTaskEnabled(taskType, enabled)
                .onFailure { e ->
                    Timber.e(e, "Failed to update task enabled: ${e.message}")
                }
        }
    }

    fun onTaskMove(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            taskConfig.reorderTasks(fromIndex, toIndex)
                .onFailure { e ->
                    Timber.e(e, "Failed to reorder tasks: ${e.message}")
                }
        }
    }

    fun onSelectedTaskChange(taskType: TaskType) {
        _state.update { it.copy(currentTaskType = taskType) }
    }

    fun onSurfaceAvailable(surfaceTexture: SurfaceTexture) {
        val surface = Surface(surfaceTexture)
        if (RemoteServiceManager.state.value !is RemoteServiceManager.ServiceState.Connected) {
            return
        }
        surfaceRef.set(surface)
        setRemoteSurface(surface)
    }

    fun onSurfaceDestroyed() {
        if (RemoteServiceManager.state.value !is RemoteServiceManager.ServiceState.Connected) {
            return
        }
        setRemoteSurface(null)
        surfaceRef.getAndSet(null)?.release()
    }

    fun onToggleFullscreenMonitor() {
        _state.update { it.copy(isFullscreenMonitor = !it.isFullscreenMonitor) }
    }

    fun onTabChange(tab: PanelTab) {
        _state.update { it.copy(currentTab = tab) }
    }

    fun onStartTasks() {
        val tasks = taskConfig.taskList.value
            .filter { it.isEnabled }
            .sortedBy { it.order }

        if (tasks.isEmpty()) {
            Timber.w("No tasks enabled")
            showDialog(
                PanelDialogUiState(
                    type = PanelDialogType.WARNING,
                    title = "提示",
                    message = "请先选择要执行的任务",
                    confirmText = "知道了",
                    confirmAction = PanelDialogConfirmAction.DISMISS_ONLY
                )
            )
            return
        }

        val taskParams = buildTaskParams()
        viewModelScope.launch {
            val result = compositionService.start(taskParams)
            val message = when (result) {
                is MaaCompositionService.StartResult.Success -> null

                is MaaCompositionService.StartResult.ResourceError ->
                    "资源加载失败，请尝试重新初始化资源"

                is MaaCompositionService.StartResult.InitializationError -> when (result.phase) {
                    MaaCompositionService.StartResult.InitializationError.InitPhase.CREATE_INSTANCE ->
                        "MaaCore 初始化失败，请重启应用"

                    MaaCompositionService.StartResult.InitializationError.InitPhase.SET_TOUCH_MODE ->
                        "触控模式设置失败，请重启应用"
                }

                is MaaCompositionService.StartResult.PortraitOrientationError ->
                    "当前屏幕为竖屏，请切换为横屏后启动"

                is MaaCompositionService.StartResult.ConnectionError -> when (result.phase) {
                    MaaCompositionService.StartResult.ConnectionError.ConnectPhase.DISPLAY_MODE ->
                        "显示模式设置失败，请重试"

                    MaaCompositionService.StartResult.ConnectionError.ConnectPhase.VIRTUAL_DISPLAY ->
                        "虚拟屏幕启动失败，请检查 Shizuku 权限"

                    MaaCompositionService.StartResult.ConnectionError.ConnectPhase.MAA_CONNECT ->
                        "连接 MaaCore 超时，请重试"
                }

                is MaaCompositionService.StartResult.StartError ->
                    "MaaCore 启动失败，请检查任务配置"
            }

            if (message != null) {
                Timber.w("Start failed: $result")
                showDialog(
                    PanelDialogUiState(
                        type = PanelDialogType.ERROR,
                        title = "启动失败",
                        message = message,
                        confirmText = "查看日志",
                        confirmAction = PanelDialogConfirmAction.GO_LOG
                    )
                )
            }
        }
    }

    fun onStopTasks() {
        viewModelScope.launch {
            compositionService.stop()
        }
    }

    fun onClearLogs() {
        runtimeLogCenter.clearRuntimeLogs()
    }

    // ==================== Dialog ====================

    private fun showDialog(dialog: PanelDialogUiState) {
        _state.update { it.copy(dialog = dialog) }
    }

    fun onDialogDismiss() {
        _state.update { it.copy(dialog = null) }
    }

    fun onDialogConfirm() {
        when (state.value.dialog?.confirmAction) {
            PanelDialogConfirmAction.DISMISS_ONLY -> {
                onDialogDismiss()
            }

            PanelDialogConfirmAction.GO_LOG -> {
                onTabChange(PanelTab.LOG)
                onDialogDismiss()
            }

            PanelDialogConfirmAction.GO_LOG_AND_STOP -> {
                onTabChange(PanelTab.LOG)
                onDialogDismiss()
                viewModelScope.launch {
                    compositionService.stop()
                }
            }

            null -> Unit
        }
    }
}
