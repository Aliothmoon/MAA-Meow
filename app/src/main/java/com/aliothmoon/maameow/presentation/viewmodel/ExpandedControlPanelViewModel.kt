package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.data.model.TaskType
import com.aliothmoon.maameow.data.preferences.TaskConfigState
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.RuntimeLogCenter
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.domain.usecase.BuildTaskParamsUseCase
import com.aliothmoon.maameow.overlay.OverlayController
import com.aliothmoon.maameow.presentation.view.panel.FloatingPanelState
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogConfirmAction
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogType
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogUiState
import com.aliothmoon.maameow.presentation.view.panel.PanelTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


class ExpandedControlPanelViewModel(
    val resourceDataManager: ResourceDataManager,
    val taskConfig: TaskConfigState,
    private val application: Context,
    private val buildTaskParams: BuildTaskParamsUseCase,
    private val compositionService: MaaCompositionService,
    private val overlayController: OverlayController,
    private val runtimeLogCenter: RuntimeLogCenter
) : ViewModel() {

    private val _state = MutableStateFlow(FloatingPanelState())
    val state: StateFlow<FloatingPanelState> = _state.asStateFlow()
    val runtimeLogs: StateFlow<List<LogItem>> = runtimeLogCenter.logs

    init {
        viewModelScope.launch {
            overlayController.signal.collect { endState ->
                val message = when (endState) {
                    MaaExecutionState.ERROR -> "任务异常终止，详细信息请查看日志"
                    else -> "任务已结束，详细信息请查看日志"
                }
                Timber.d("Overlay result received: $endState")
                showDialog(
                    if (endState == MaaExecutionState.ERROR) {
                        PanelDialogUiState(
                            type = PanelDialogType.ERROR,
                            title = "提示",
                            message = message,
                            confirmText = "知道了",
                            confirmAction = PanelDialogConfirmAction.GO_LOG_AND_STOP
                        )
                    } else {
                        PanelDialogUiState(
                            type = PanelDialogType.SUCCESS,
                            title = "任务完成",
                            message = message,
                            confirmText = "查看日志",
                            confirmAction = PanelDialogConfirmAction.GO_LOG
                        )
                    }
                )
            }
        }
    }


    fun onTaskEnableChange(type: TaskType, enabled: Boolean) {
        viewModelScope.launch {
            taskConfig.updateTaskEnabled(type, enabled)
                .onSuccess {
                    Timber.d("Updated %s enabled: %s", type.displayName, enabled)
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to update task enabled: ${e.message}")
                }
        }
    }

    fun onTaskMove(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            taskConfig.reorderTasks(fromIndex, toIndex)
                .onSuccess {
                    Timber.d("Moved task from %d to %d", fromIndex, toIndex)
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to reorder tasks: ${e.message}")
                }
        }
    }

    fun onSelectedTaskChange(taskType: TaskType) {
        _state.update { it.copy(currentTaskType = taskType) }
        Timber.d("Selected task: %s", taskType.displayName)
    }

    fun onTabChange(tab: PanelTab) {
        _state.update { it.copy(currentTab = tab) }
        Timber.d("Selected tab: %s", tab.displayName)
    }

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

    fun onClearLogs() {
        runtimeLogCenter.clearRuntimeLogs()
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

        // 打印任务 JSON 列表
        Timber.i("=== Task JSON List (%d tasks) ===", taskParams.size)
        taskParams.forEachIndexed { index, params ->
            Timber.i("[%d] Type: %s", index, params.type.value)
            Timber.i("    Params: %s", params.params)
        }
        Timber.i("=== End Task JSON List ===")
        viewModelScope.launch {
            val result = compositionService.start(taskParams)
            val message = when (result) {
                is MaaCompositionService.StartResult.Success ->
                    "Maa启动成功"

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
            if (result is MaaCompositionService.StartResult.Success) {
                // 成功时用 Toast 简短提示
                withContext(Dispatchers.Main) {
                    Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
                }
            } else {
                // 失败时通过 StateFlow 通知 UI 展示 OverlayDialog
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
}
