package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.data.model.TaskTypeInfo
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.data.model.TaskParamProvider
import com.aliothmoon.maameow.domain.usecase.PrepareTaskStartUseCase
import com.aliothmoon.maameow.domain.usecase.TaskStartAcknowledgement
import com.aliothmoon.maameow.domain.usecase.TaskStartContext
import com.aliothmoon.maameow.domain.usecase.TaskStartDecision
import com.aliothmoon.maameow.domain.usecase.TaskStartMode
import com.aliothmoon.maameow.overlay.OverlayController
import com.aliothmoon.maameow.presentation.view.panel.FloatingPanelState
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogConfirmAction
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogType
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogUiState
import com.aliothmoon.maameow.presentation.view.panel.PanelTab
import com.aliothmoon.maameow.utils.i18n.resolve
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


class ExpandedControlPanelViewModel(
    val chainState: TaskChainState,
    private val application: Context,
    private val prepareTaskStart: PrepareTaskStartUseCase,
    private val compositionService: MaaCompositionService,
    private val overlayController: OverlayController,
    private val sessionLogger: MaaSessionLogger
) : ViewModel() {

    private val _state = MutableStateFlow(FloatingPanelState())
    val state: StateFlow<FloatingPanelState> = _state.asStateFlow()
    val runtimeLogs: StateFlow<List<LogItem>> = sessionLogger.logs
    private var pendingStartContext: TaskStartContext? = null

    init {
        viewModelScope.launch {
            overlayController.signal.collect { endState ->
                Timber.d("Overlay result received: $endState")
                showDialog(application.createExecutionEndDialog(endState))
            }
        }
    }

    fun onNodeEnabledChange(nodeId: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { chainState.setNodeEnabled(nodeId, enabled) }
                .onSuccess {
                    Timber.d("Updated node %s enabled: %s", nodeId, enabled)
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to update node enabled: ${e.message}")
                }
        }
    }

    fun onNodeMove(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            runCatching { chainState.reorderNodes(fromIndex, toIndex) }
                .onSuccess {
                    Timber.d("Moved node from %d to %d", fromIndex, toIndex)
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to reorder nodes: ${e.message}")
                }
        }
    }

    fun onNodeSelected(nodeId: String) {
        _state.update { it.copy(selectedNodeId = nodeId, isAddingTask = false) }
        Timber.d("Selected node: %s", nodeId)
    }

    fun onToggleEditMode() {
        _state.update { it.copy(isEditMode = !it.isEditMode, isAddingTask = false, isProfileMode = false) }
        Timber.d("Edit mode toggled: %s", _state.value.isEditMode)
    }

    fun onToggleProfileMode() {
        _state.update { it.copy(isProfileMode = !it.isProfileMode, isEditMode = false, isAddingTask = false) }
        Timber.d("Profile mode toggled: %s", _state.value.isProfileMode)
    }

    fun onSwitchProfile(profileId: String) {
        viewModelScope.launch {
            chainState.switchProfile(profileId)
            // 切换后清除选中状态
            _state.update { it.copy(selectedNodeId = null) }
        }
    }

    fun onCreateProfile() {
        viewModelScope.launch {
            chainState.createProfile()
            _state.update { it.copy(selectedNodeId = null) }
        }
    }

    fun onDeleteProfile(profileId: String) {
        viewModelScope.launch {
            chainState.deleteProfile(profileId)
            _state.update { it.copy(selectedNodeId = null) }
        }
    }

    fun onRenameProfile(profileId: String, name: String) {
        viewModelScope.launch {
            chainState.renameProfile(profileId, name)
        }
    }

    fun onDuplicateProfile(profileId: String) {
        viewModelScope.launch {
            chainState.duplicateProfile(profileId)
        }
    }

    fun onReorderProfile(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            runCatching { chainState.reorderProfiles(fromIndex, toIndex) }
                .onFailure { e -> Timber.e(e, "Failed to reorder profile: ${e.message}") }
        }
    }

    fun onToggleAddingTask() {
        _state.update { it.copy(isAddingTask = !it.isAddingTask, selectedNodeId = null) }
        Timber.d("Adding task mode toggled: %s", _state.value.isAddingTask)
    }

    fun onAddNode(typeInfo: TaskTypeInfo) {
        viewModelScope.launch {
            val nodeId = chainState.addNode(typeInfo)
            _state.update { it.copy(isAddingTask = false, selectedNodeId = nodeId) }
        }
    }

    fun onRemoveNode(nodeId: String) {
        viewModelScope.launch {
            chainState.removeNode(nodeId)
            if (_state.value.selectedNodeId == nodeId) {
                _state.update { it.copy(selectedNodeId = null) }
            }
        }
    }

    fun onRenameNode(nodeId: String, newName: String) {
        viewModelScope.launch {
            chainState.renameNode(nodeId, newName)
        }
    }

    fun onNodeConfigChange(nodeId: String, config: TaskParamProvider) {
        viewModelScope.launch {
            chainState.updateNodeConfig(nodeId, config)
        }
    }

    fun onTabChange(tab: PanelTab) {
        _state.update { it.copy(currentTab = tab) }
        Timber.d("Selected tab: %s", tab.name)
    }

    private fun showDialog(dialog: PanelDialogUiState) {
        _state.update { it.copy(dialog = dialog) }
    }

    fun onDialogDismiss() {
        pendingStartContext = null
        _state.update { it.copy(dialog = null) }
    }

    fun onDialogConfirm() {
        when (state.value.dialog?.confirmAction) {
            PanelDialogConfirmAction.DISMISS_ONLY -> {
                onDialogDismiss()
            }

            PanelDialogConfirmAction.CONFIRM_PENDING_START -> {
                val pendingContext = pendingStartContext
                _state.update { it.copy(dialog = null) }
                pendingStartContext = null
                if (pendingContext != null) {
                    launchManualStart(
                        pendingContext.acknowledged(
                            TaskStartAcknowledgement.GAME_NOT_RUNNING_WITHOUT_WAKE_UP
                        )
                    )
                }
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
        sessionLogger.clearRuntimeLogs()
    }

    fun onStartTasks() {
        launchManualStart(TaskStartContext(mode = TaskStartMode.MANUAL))
    }

    private fun launchManualStart(context: TaskStartContext) {
        viewModelScope.launch {
            val plan = when (
                val decision = prepareTaskStart(
                    chain = chainState.chain.value,
                    context = context,
                )
            ) {
                is TaskStartDecision.Ready -> {
                    pendingStartContext = null
                    decision.plan
                }

                is TaskStartDecision.Blocked -> {
                    pendingStartContext = null
                    val message = application.resolveTaskStartDecisionMessage(decision)
                    Timber.w("Validation failed: %s", message.resolve(application))
                    showDialog(application.createStartBlockedDialog(message))
                    return@launch
                }

                is TaskStartDecision.RequiresConfirmation -> {
                    pendingStartContext = context
                    showDialog(
                        application.createStartWarningDialog(
                            application.resolveTaskStartDecisionMessage(decision)
                        )
                    )
                    return@launch
                }
            }

            Timber.i("=== Task JSON List (%d tasks) ===", plan.params.size)
            plan.params.forEachIndexed { index, params ->
                Timber.i("[%d] Type: %s", index, params.type.value)
                Timber.i("    Params: %s", params.params)
            }
            Timber.i("=== End Task JSON List ===")

            val result = compositionService.start(
                tasks = plan.params,
                clientType = plan.clientType,
            )
            val message = application.formatStartResult(result)
            if (result is MaaCompositionService.StartResult.Success) {
                // 成功时用 Toast 简短提示
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        application,
                        message.resolve(application),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                // 失败时通过 StateFlow 通知 UI 展示 OverlayDialog
                Timber.w("Start failed: %s", message.resolve(application))
                showDialog(application.createStartFailedDialog(message))
            }
        }
    }
}
