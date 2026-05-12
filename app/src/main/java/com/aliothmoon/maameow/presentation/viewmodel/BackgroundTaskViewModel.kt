package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.data.model.TaskTypeInfo
import com.aliothmoon.maameow.data.model.TaskParamProvider
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.domain.usecase.PrepareTaskStartUseCase
import com.aliothmoon.maameow.overlay.screensaver.HardwareScreenOffManager
import com.aliothmoon.maameow.domain.usecase.TaskStartAcknowledgement
import com.aliothmoon.maameow.domain.usecase.TaskStartContext
import com.aliothmoon.maameow.domain.usecase.TaskStartDecision
import com.aliothmoon.maameow.domain.usecase.TaskStartMode
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.presentation.state.BackgroundTaskState
import com.aliothmoon.maameow.presentation.state.PreviewTouchMarker
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogConfirmAction
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogType
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogUiState
import com.aliothmoon.maameow.presentation.view.panel.PanelTab
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.resolve
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.ScheduledExecutionRequest
import com.aliothmoon.maameow.schedule.service.ScheduledLaunchCoordinator
import com.aliothmoon.maameow.schedule.service.ScheduleTriggerLogger
import kotlinx.coroutines.flow.drop
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

class BackgroundTaskViewModel(
    val chainState: TaskChainState,
    private val prepareTaskStart: PrepareTaskStartUseCase,
    private val compositionService: MaaCompositionService,
    private val sessionLogger: MaaSessionLogger,
    private val appSettingsManager: AppSettingsManager,
    private val hardwareScreenOffManager: HardwareScreenOffManager,
    scheduleRepository: ScheduleStrategyRepository,
    triggerLogger: ScheduleTriggerLogger,
    private val application: Context,
) : ViewModel() {

    val coordinator = ScheduledLaunchCoordinator(
        scope = viewModelScope,
        scheduleRepository = scheduleRepository,
        compositionService = compositionService,
        appSettingsManager = appSettingsManager,
        chainState = chainState,
        triggerLogger = triggerLogger,
    )

    private val _state = MutableStateFlow(BackgroundTaskState())
    val state: StateFlow<BackgroundTaskState> = _state.asStateFlow()
    val logs: StateFlow<List<LogItem>> = sessionLogger.logs

    private val surfaceRef = AtomicReference<Surface>()

    private val _isGameMuted = MutableStateFlow(false)
    val isGameMuted: StateFlow<Boolean> = _isGameMuted.asStateFlow()

    private val touchPreviewController = TouchPreviewController(viewModelScope)
    val markers: StateFlow<List<PreviewTouchMarker>> = touchPreviewController.markers
    private var pendingStart: PendingStart? = null

    private data class PendingStart(
        val context: TaskStartContext,
        val request: ScheduledExecutionRequest? = null,
    )

    init {
        Timber.i("BackgroundTaskViewModel inited")
        observeServiceState()
        observeTaskEnd()
        observeTouchPreviewToggle()
    }

    private fun observeTouchPreviewToggle() {
        viewModelScope.launch {
            appSettingsManager.showTouchPreview.collect { enabled ->
                touchPreviewController.onTouchCallbackChange(enabled)
            }
        }
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            RemoteServiceManager.state
                .drop(1)
                .collect { state ->
                    when (state) {
                        // 服务重连
                        is RemoteServiceManager.ServiceState.Connected -> {
                            onServiceReconnected(state.service)
                        }

                        is RemoteServiceManager.ServiceState.Error -> {
                            touchPreviewController.onClear()
                        }

                        else -> Unit
                    }
                }
        }
    }

    fun onServiceReconnected(srv: RemoteService) {
        if (surfaceRef.get() != null) {
            onMonitorSurfaceChanged(srv)
        }
        val enabled = appSettingsManager.showTouchPreview.value
        touchPreviewController.onTouchCallbackChange(enabled)
    }

    private fun observeTaskEnd() {
        viewModelScope.launch {
            var prev = compositionService.state.value
            compositionService.state.collect { current ->
                // 仅在任务自然结束（RUNNING → IDLE/ERROR）时关闭游戏；
                // 手动停止走 RUNNING → STOPPING → IDLE，prev 为 STOPPING 不会匹配，
                // 这是预期行为：手动停止说明用户可能还要继续操作，不应自动关闭游戏。
                if (prev == MaaExecutionState.RUNNING
                    && (current == MaaExecutionState.IDLE || current == MaaExecutionState.ERROR)
                    && appSettingsManager.closeAppOnTaskEnd.value
                ) {
                    Timber.i("Task ended (%s), auto closing app", current)
                    compositionService.stopVirtualDisplay()
                }
                prev = current
            }
        }
    }


    // ==================== Scheduled Launch ====================

    fun onScheduledLaunch(request: ScheduledExecutionRequest) {
        coordinator.onLaunch(request)
    }

    fun onScheduledCountdownCancel() {
        coordinator.onCancel()
    }

    fun onScheduledStartNow() {
        coordinator.onStartNow()
    }

    fun onScheduledExecutionPageReady(requestId: String) {
        coordinator.onPageReady(requestId) { request ->
            _state.update {
                it.copy(
                    current = PanelTab.TASKS,
                    selectedNodeId = null,
                    isAddingTask = false,
                    isEditMode = false,
                    isProfileMode = false,
                )
            }
            startTasksInternal(
                request = request,
                context = TaskStartContext(mode = TaskStartMode.SCHEDULED),
            )?.resolve(application)
        }
    }

    // ==================== Surface ====================

    private fun onMonitorSurfaceChanged(
        service: RemoteService? = RemoteServiceManager.getInstanceOrNull()
    ) {
        val remote = service ?: return
        val surface = surfaceRef.get()
        Timber.d("onMonitorSurfaceChanged: surface=%s", surface)
        runCatching {
            remote.setMonitorSurface(surface)
        }.onFailure {
            Timber.w(it, "setMonitorSurface failed")
        }
    }

    fun onSurfaceAvailable(surface: Surface) {
        surfaceRef.set(surface)
        onMonitorSurfaceChanged()
    }

    fun onSurfaceDestroyed() {
        val surface = surfaceRef.getAndSet(null)
        onMonitorSurfaceChanged()
        surface?.release()
    }

    // ==================== Touch Input ====================

    fun onTouchDown(x: Int, y: Int) {
        runCatching {
            RemoteServiceManager.getInstanceOrNull()?.touchDown(x, y)
        }.onFailure {
            Timber.e(it, "touchDown failed at ($x, $y)")
        }
    }

    fun onTouchMove(x: Int, y: Int) {
        runCatching {
            RemoteServiceManager.getInstanceOrNull()?.touchMove(x, y)
        }.onFailure {
            Timber.e(it, "touchMove failed at ($x, $y)")
        }
    }

    fun onTouchUp(x: Int, y: Int) {
        runCatching {
            RemoteServiceManager.getInstanceOrNull()?.touchUp(x, y)
        }.onFailure {
            Timber.e(it, "touchUp failed at ($x, $y)")
        }
    }

    fun onScreenOff() {
        runCatching {
            hardwareScreenOffManager.activate()
        }.onFailure {
            Timber.e(it, "onScreenOff failed")
        }
    }

    // ==================== Task Chain ====================

    fun onNodeEnabledChange(nodeId: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { chainState.setNodeEnabled(nodeId, enabled) }
                .onFailure { e ->
                    Timber.e(e, "Failed to update node enabled: ${e.message}")
                }
        }
    }

    fun onNodeMove(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            runCatching { chainState.reorderNodes(fromIndex, toIndex) }
                .onFailure { e ->
                    Timber.e(e, "Failed to reorder nodes: ${e.message}")
                }
        }
    }

    fun onNodeSelected(nodeId: String) {
        _state.update { it.copy(selectedNodeId = nodeId, isAddingTask = false) }
    }

    fun onToggleEditMode() {
        _state.update {
            it.copy(
                isEditMode = !it.isEditMode,
                isAddingTask = false,
                isProfileMode = false
            )
        }
        Timber.d("Edit mode toggled: %s", _state.value.isEditMode)
    }

    fun onToggleProfileMode() {
        _state.update {
            it.copy(
                isProfileMode = !it.isProfileMode,
                isEditMode = false,
                isAddingTask = false
            )
        }
        Timber.d("Profile mode toggled: %s", _state.value.isProfileMode)
    }

    fun onSwitchProfile(profileId: String) {
        viewModelScope.launch {
            chainState.switchProfile(profileId)
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

    // ==================== UI State ====================

    fun onToggleFullscreenMonitor() {
        _state.update { it.copy(isFullscreenMonitor = !it.isFullscreenMonitor) }
    }

    fun onTabChange(tab: PanelTab) {
        _state.update { it.copy(current = tab) }
    }

    // ==================== Task Execution ====================

    fun onStartTasks() {
        launchManualStart(TaskStartContext(mode = TaskStartMode.MANUAL))
    }

    private fun launchManualStart(context: TaskStartContext) {
        viewModelScope.launch {
            val message = startTasksInternal(context = context)
            if (message != null && state.value.dialog == null) {
                showStartFailedDialog(message)
            }
        }
    }

    private suspend fun doSwitchProfile(request: ScheduledExecutionRequest?) {
        if (request != null && chainState.activeProfileId.value != request.profileId) {
            chainState.switchProfile(request.profileId)
        }
    }

    private suspend fun startTasksInternal(
        request: ScheduledExecutionRequest? = null,
        context: TaskStartContext,
    ): UiText? {
        doSwitchProfile(request)

        val plan = when (
            val decision = prepareTaskStart(
                chain = chainState.chain.value,
                context = context,
            )
        ) {
            is TaskStartDecision.Ready -> {
                pendingStart = null
                decision.plan
            }

            is TaskStartDecision.Blocked -> {
                pendingStart = null
                val message = application.resolveTaskStartDecisionMessage(decision)
                Timber.w("Validation failed: %s", message.resolve(application))
                if (request != null) {
                    showStartFailedDialog(message)
                } else {
                    showDialog(application.createStartBlockedDialog(message))
                }
                return message
            }

            is TaskStartDecision.RequiresConfirmation -> {
                pendingStart = PendingStart(context, request)
                val message = application.resolveTaskStartDecisionMessage(decision)
                showDialog(application.createStartWarningDialog(message))
                return message
            }
        }

        val result = compositionService.start(
            tasks = plan.params,
            clientType = plan.clientType,
        ) {
            if (request != null) {
                sessionLogger.appendAndWait(
                    application.getString(
                        R.string.task_start_triggered_by_schedule,
                        request.strategyName,
                    ),
                )
            }
        }
        if (result is MaaCompositionService.StartResult.Success) {
            if (appSettingsManager.muteOnGameLaunch.value) {
                onMuteGameSound(plan.clientType)
            }
            chainState.grantGameBatteryExemption(plan.clientType)
        }

        val message = application.resolveTaskStartFailureMessage(result)
        if (message != null) {
            Timber.w("Start failed: %s", message.resolve(application))
            if (request != null) {
                showStartFailedDialog(message)
            }
            return message
        }
        return null
    }

    fun onStopTasks() {
        viewModelScope.launch {
            compositionService.stop()
        }
    }

    fun onClearLogs() {
        sessionLogger.clearRuntimeLogs()
    }

    fun onToggleGameSound() {
        if (_isGameMuted.value) {
            onUnmuteGameSound(chainState.getClientTypeOrNull())
        } else {
            onMuteGameSound(chainState.getClientTypeOrNull())
        }
    }

    private fun onMuteGameSound(clientType: String?) {
        clientType?.let {
            val pkg = Packages[it] ?: return
            RemoteServiceManager.getInstanceOrNull()
                ?.setPlayAudioOpAllowed(pkg, false)
            _isGameMuted.value = true
        }
    }

    private fun onUnmuteGameSound(clientType: String?) {
        clientType?.let {
            val pkg = Packages[it] ?: return
            RemoteServiceManager.getInstanceOrNull()
                ?.setPlayAudioOpAllowed(pkg, true)
            _isGameMuted.value = false
        }
    }

    private fun showStartFailedDialog(message: UiText) {
        showDialog(application.createStartFailedDialog(message))
    }

    // ==================== Dialog ====================

    private fun showDialog(dialog: PanelDialogUiState) {
        _state.update { it.copy(dialog = dialog) }
    }

    fun onDialogDismiss() {
        pendingStart = null
        _state.update { it.copy(dialog = null) }
    }

    fun onDialogConfirm() {
        when (state.value.dialog?.confirmAction) {
            PanelDialogConfirmAction.DISMISS_ONLY -> {
                onDialogDismiss()
            }

            PanelDialogConfirmAction.CONFIRM_PENDING_START -> {
                val pending = pendingStart
                _state.update { it.copy(dialog = null) }
                pendingStart = null
                if (pending != null) {
                    val acked = pending.context.acknowledged(
                        TaskStartAcknowledgement.GAME_NOT_RUNNING_WITHOUT_WAKE_UP
                    )
                    viewModelScope.launch {
                        val message = startTasksInternal(
                            request = pending.request,
                            context = acked,
                        )
                        if (message != null && state.value.dialog == null) {
                            showStartFailedDialog(message)
                        }
                    }
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

    override fun onCleared() {
        coordinator.cancel()
        touchPreviewController.onClear()
        super.onCleared()
    }
}
