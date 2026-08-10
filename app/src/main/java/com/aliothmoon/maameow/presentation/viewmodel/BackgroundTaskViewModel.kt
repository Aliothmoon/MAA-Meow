package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.data.model.TaskParamProvider
import com.aliothmoon.maameow.data.model.TaskTypeInfo
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.launch.LaunchPipeline
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.service.ScheduleAlarmManager
import com.aliothmoon.maameow.domain.launch.LaunchRequest
import com.aliothmoon.maameow.domain.launch.LaunchSession
import com.aliothmoon.maameow.domain.launch.LaunchUserEvent
import com.aliothmoon.maameow.domain.launch.toCountdownState
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.GameMuteCoordinator
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.domain.usecase.PrepareTaskStartUseCase
import com.aliothmoon.maameow.domain.usecase.TaskStartContext
import com.aliothmoon.maameow.domain.usecase.TaskStartDecision
import com.aliothmoon.maameow.domain.usecase.TaskStartMode
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.presentation.state.BackgroundTaskState
import com.aliothmoon.maameow.presentation.state.PreviewTouchMarker
import com.aliothmoon.maameow.presentation.state.UiEffect
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogConfirmAction
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogUiState
import com.aliothmoon.maameow.presentation.view.panel.PanelTab
import com.aliothmoon.maameow.schedule.model.CountdownState
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.resolve
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class BackgroundTaskViewModel(
    val chainState: TaskChainState,
    private val prepareTaskStart: PrepareTaskStartUseCase,
    private val compositionService: MaaCompositionService,
    private val sessionLogger: MaaSessionLogger,
    private val appSettingsManager: AppSettingsManager,
    private val pathConfig: MaaPathConfig,
    private val achievementReporter: AchievementReporter,
    private val gameMuteCoordinator: GameMuteCoordinator,
    private val launchPipeline: LaunchPipeline,
    private val scheduleRepository: ScheduleStrategyRepository,
    private val scheduleAlarmManager: ScheduleAlarmManager,
    private val application: Context,
) : ViewModel() {

    val launchSession: StateFlow<LaunchSession> = launchPipeline.session
    val launchEffects = launchPipeline.effects
    val countdownState: StateFlow<CountdownState> = launchPipeline.session
        .map { it.toCountdownState() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CountdownState.Idle)

    /**
     * 导航用：仅 [LaunchSession.InFlight.presentUi] 为 true（后台 Dialog 倒计时）时置位
     * 前台无倒计时不导航，避免强行拉回主 Tab
     */
    val pendingNavigateRequestId: StateFlow<String?> = launchPipeline.session
        .map { session ->
            when (session) {
                is LaunchSession.InFlight -> {
                    if (!session.presentUi) null
                    else when (session.phase) {
                        is LaunchSession.Phase.Counting,
                        LaunchSession.Phase.Preparing,
                        LaunchSession.Phase.Starting -> session.request.requestId
                        else -> null
                    }
                }
                LaunchSession.Idle -> null
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _state = MutableStateFlow(BackgroundTaskState())
    val state: StateFlow<BackgroundTaskState> = _state.asStateFlow()
    val logs: StateFlow<List<LogItem>> = sessionLogger.logs

    private val surfaceRef = AtomicReference<Surface>()

    val isGameMuted: StateFlow<Boolean> = gameMuteCoordinator.isMuted

    // 调试截图结果（已本地化的提示文案），供 UI 以 Toast 展示
    private val _screenshotMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val screenshotMessage: SharedFlow<String> = _screenshotMessage.asSharedFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

    private val touchPreviewController = TouchPreviewController(viewModelScope)
    val markers: StateFlow<List<PreviewTouchMarker>> = touchPreviewController.markers
    private var pendingStart: PendingStart? = null
    /** 任务链：当前段启动成功后，剩余待执行的用户配置 ID。 */
    private var pendingSequenceProfileIds: List<String> = emptyList()
    private var sequenceTotal: Int = 0
    private var sequenceUserStop: Boolean = false

    private data class PendingStart(
        val context: TaskStartContext,
    )

    init {
        Timber.i("BackgroundTaskViewModel inited")
        observeServiceState()
        observeTaskEnd()
        observeTouchPreviewToggle()
        observeDefaultTaskSelection()
    }

    /**
     * 首次进入 / 选中失效时默认打开任务链第一项，避免右侧一直停在空占位。
     * 新增任务、配置管理模式下不自动改写选中。
     */
    private fun observeDefaultTaskSelection() {
        viewModelScope.launch {
            combine(chainState.chain, _state) { nodes, ui ->
                resolveTaskPanelSelectedNodeId(
                    nodes = nodes,
                    selectedNodeId = ui.selectedNodeId,
                    isAddingTask = ui.isAddingTask,
                    isProfileMode = ui.isProfileMode,
                )
            }
                .distinctUntilChanged()
                .collect { resolved ->
                    if (_state.value.selectedNodeId != resolved) {
                        _state.update { it.copy(selectedNodeId = resolved) }
                    }
                }
        }
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
                // 用户/外部停止：清空任务链队列，避免 STOPPING→IDLE 误开下一段
                if (current == MaaExecutionState.STOPPING || sequenceUserStop) {
                    if (pendingSequenceProfileIds.isNotEmpty()) {
                        Timber.i("Clearing pending sequence profiles on stop")
                        pendingSequenceProfileIds = emptyList()
                        sequenceTotal = 0
                    }
                }
                // 任务链：自然结束（RUNNING → IDLE）后切换下一个用户配置
                if (prev == MaaExecutionState.RUNNING
                    && current == MaaExecutionState.IDLE
                    && !sequenceUserStop
                    && pendingSequenceProfileIds.isNotEmpty()
                ) {
                    val remaining = pendingSequenceProfileIds
                    pendingSequenceProfileIds = emptyList()
                    viewModelScope.launch {
                        startNextSequenceProfile(remaining)
                    }
                    prev = current
                    return@collect
                }
                if (prev == MaaExecutionState.RUNNING && current == MaaExecutionState.ERROR) {
                    if (pendingSequenceProfileIds.isNotEmpty()) {
                        Timber.w(
                            "Sequence segment ERROR; drop %d remaining profile(s)",
                            pendingSequenceProfileIds.size,
                        )
                        pendingSequenceProfileIds = emptyList()
                        sequenceTotal = 0
                    }
                }
                // 仅在任务自然结束（RUNNING → IDLE/ERROR）时关闭游戏；
                // 手动停止走 RUNNING → STOPPING → IDLE，prev 为 STOPPING 不会匹配。
                // 任务链还有下一段时不关游戏。
                if (prev == MaaExecutionState.RUNNING
                    && (current == MaaExecutionState.IDLE || current == MaaExecutionState.ERROR)
                    && appSettingsManager.closeAppOnTaskEnd.value
                    && pendingSequenceProfileIds.isEmpty()
                    && sequenceTotal == 0
                ) {
                    Timber.i("Task ended (%s), auto closing app", current)
                    _effects.send(UiEffect.toast(R.string.bg_toast_auto_closed_on_end))
                    compositionService.stopVirtualDisplay()
                }
                prev = current
            }
        }
    }


    // ==================== Scheduled Launch ====================

    fun onExternalLaunch(request: LaunchRequest) {
        launchPipeline.execute(request)
    }

    fun onScheduledCountdownCancel() {
        launchPipeline.submit(LaunchUserEvent.Cancel)
    }

    fun onScheduledStartNow() {
        launchPipeline.submit(LaunchUserEvent.StartNow)
    }

    fun onNavigateForScheduledLaunch() {
        _state.update {
            it.copy(
                current = PanelTab.TASKS,
                selectedNodeId = null,
                isAddingTask = false,
                isEditMode = false,
                isProfileMode = false,
            )
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
        // 硬件熄屏：仅下发一次关闭物理屏幕的指令，无状态、幂等（再点必发，不会卡死）。
        // 启用该功能时 MainActivity 始终持有 FLAG_KEEP_SCREEN_ON 保持系统唤醒、不锁屏；
        // 屏幕恢复由系统在用户唤醒时处理，会话结束/服务销毁时由 PowerController 的 flag 兜底。
        val service = RemoteServiceManager.getInstanceOrNull()
        if (service == null) {
            Timber.w("onScreenOff skipped: remote service unavailable")
            return
        }
        runCatching { service.setDisplayPower(false) }
            .onFailure { Timber.e(it, "onScreenOff failed") }
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
            chainState.removeProfile(profileId)
            // PROFILE 定时解绑 + 可能因此变空的 SEQUENCE 定时一并消毒
            val detached = scheduleRepository.detachProfileConfig(profileId)
            val emptied = scheduleRepository.sanitizeInvalidTargets(
                profiles = chainState.profiles.value,
                sequenceConfigs = chainState.sequenceConfigs.value,
            )
            (detached + emptied).distinct().forEach { strategyId ->
                scheduleAlarmManager.cancel(strategyId)
            }
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

    fun onDuplicateNode(nodeId: String) {
        viewModelScope.launch {
            val newId = chainState.duplicateNode(nodeId)
            if (newId.isNotEmpty()) {
                _state.update { it.copy(selectedNodeId = newId) }
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

    // ==================== Profile Sequence ====================
    fun onAddProfilesToSequence(profileIds: List<String>) {
        viewModelScope.launch {
            chainState.addProfilesToSequence(profileIds)
        }
    }
    fun onRemoveSequenceEntry(entryId: String) {
        viewModelScope.launch {
            chainState.removeSequenceEntry(entryId)
        }
    }
    fun onReorderSequence(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            runCatching { chainState.reorderSequence(fromIndex, toIndex) }
                .onFailure { e -> Timber.e(e, "Failed to reorder sequence: ${e.message}") }
        }
    }
    fun onSetProfileSequenceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            chainState.setProfileSequenceEnabled(enabled)
        }
    }
    fun onSwitchSequenceConfig(configId: String) {
        viewModelScope.launch {
            chainState.switchSequenceConfig(configId)
        }
    }
    fun onCreateSequenceConfig() {
        viewModelScope.launch {
            chainState.createSequenceConfig()
        }
    }
    fun onRenameSequenceConfig(configId: String, name: String) {
        viewModelScope.launch {
            chainState.renameSequenceConfig(configId, name)
        }
    }
    fun onDeleteSequenceConfig(configId: String) {
        viewModelScope.launch {
            chainState.deleteSequenceConfig(configId)
            // 删除任务链后：禁用并解绑引用它的定时策略，同时取消已注册闹钟
            val detached = scheduleRepository.detachSequenceConfig(configId)
            detached.forEach { strategyId ->
                scheduleAlarmManager.cancel(strategyId)
            }
        }
    }

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

    /**
     * 手动启动。
     * 启用任务链时：按序 switch 用户配置，跑完一个再接下一个（不是 flatMap 拼节点）。
     * 定时仍走 [LaunchPipeline] + [StartTaskChainUseCase]。
     */
    private suspend fun startTasksInternal(
        context: TaskStartContext,
        continueProfileIds: List<String>? = null,
    ): UiText? {
        sequenceUserStop = false
        val profileIds = continueProfileIds ?: chainState.resolveSequentialProfileIds()
        if (profileIds.isEmpty()) {
            pendingSequenceProfileIds = emptyList()
            sequenceTotal = 0
            return startSingleProfile(context = context)
        }

        if (continueProfileIds == null) {
            sequenceTotal = profileIds.size
        }
        val head = profileIds.first()
        val rest = profileIds.drop(1)
        if (chainState.profileId.value != head) {
            chainState.switchProfile(head)
        }
        if (chainState.profileId.value != head) {
            pendingSequenceProfileIds = emptyList()
            sequenceTotal = 0
            val message = uiTextOf(R.string.schedule_log_profile_missing)
            showStartFailedDialog(message)
            return message
        }
        val total = sequenceTotal.coerceAtLeast(profileIds.size)
        val index = (total - profileIds.size + 1).coerceAtLeast(1)
        val profileName = chainState.profiles.value.find { it.id == head }?.name ?: head
        if (total > 1 && continueProfileIds == null) {
            _effects.send(
                UiEffect.toast(
                    application.getString(
                        R.string.task_start_toast_sequence_profiles,
                        total,
                    ),
                ),
            )
        }
        val err = startSingleProfile(
            context = context,
            sequenceIndex = index,
            sequenceTotalCount = total,
            profileName = profileName,
        )
        if (err != null) {
            pendingSequenceProfileIds = emptyList()
            sequenceTotal = 0
            return err
        }
        pendingSequenceProfileIds = rest
        return null
    }

    private suspend fun startSingleProfile(
        context: TaskStartContext,
        sequenceIndex: Int = 1,
        sequenceTotalCount: Int = 1,
        profileName: String? = null,
    ): UiText? {
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
                showDialog(application.createStartBlockedDialog(message))
                return message
            }
            is TaskStartDecision.RequiresConfirmation -> {
                pendingStart = PendingStart(context.acknowledged(decision.acknowledgement))
                val message = application.resolveTaskStartDecisionMessage(decision)
                showDialog(application.createStartWarningDialog(message))
                return message
            }
        }
        val muteRequested = appSettingsManager.muteOnGameLaunch.value
        if (muteRequested && !gameMuteCoordinator.mute(plan.clientType)) {
            _effects.send(UiEffect.toast(R.string.bg_toast_mute_failed))
        }
        val resolvedName = profileName
            ?: chainState.profiles.value.find { it.id == chainState.profileId.value }?.name
            ?: chainState.profileId.value
        val enabledNames = chainState.chain.value
            .filter { it.enabled }
            .map { it.name }
            .joinToString("、")
            .ifEmpty { "-" }
        val result = compositionService.start(
            tasks = plan.params,
            clientType = plan.clientType,
            preflightLogs = plan.logs,
        ) {
            // 会话首条：配置名 + 进度 + 本段任务名
            sessionLogger.appendAndWait(
                application.getString(
                    R.string.runlog_sequence_profile_start,
                    sequenceIndex,
                    sequenceTotalCount.coerceAtLeast(sequenceIndex),
                    resolvedName,
                    plan.params.size,
                    enabledNames,
                ),
            )
        }
        if (result is MaaCompositionService.StartResult.Success) {
            achievementReporter.reportTaskStarted(
                taskCount = plan.params.size,
                launchesGame = plan.launchesGame,
                gameAliveBeforeStart = plan.gameAliveBeforeStart,
            )
        }
        val message = application.resolveTaskStartFailureMessage(result)
        if (message != null) {
            Timber.w("Start failed: %s", message.resolve(application))
            return message
        }
        return null
    }

    private suspend fun startNextSequenceProfile(remaining: List<String>) {
        if (remaining.isEmpty() || sequenceUserStop) {
            pendingSequenceProfileIds = emptyList()
            sequenceTotal = 0
            return
        }
        val total = sequenceTotal.coerceAtLeast(remaining.size)
        val index = (total - remaining.size + 1).coerceAtLeast(1)
        val nextId = remaining.first()
        val nextName = chainState.profiles.value.find { it.id == nextId }?.name ?: nextId
        Timber.i(
            "Sequence: starting profile %d/%d id=%s name=%s; %d after this",
            index,
            total,
            nextId,
            nextName,
            remaining.size - 1,
        )
        _effects.send(
            UiEffect.toast(
                application.getString(
                    R.string.task_start_toast_next_sequence_profile,
                    index,
                    total,
                    nextName,
                ),
            ),
        )
        // 上一段自然结束后 Core 已 IDLE；关虚拟显示再续跑，给 force_stop/重连留空档
        runCatching { compositionService.stopVirtualDisplay() }
            .onFailure { Timber.w(it, "stopVirtualDisplay before next sequence profile failed") }
        withTimeoutOrNull(15_000L) {
            compositionService.state.first {
                it == MaaExecutionState.IDLE || it == MaaExecutionState.ERROR
            }
        }
        delay(800)
        if (sequenceUserStop) {
            pendingSequenceProfileIds = emptyList()
            sequenceTotal = 0
            return
        }
        val message = startTasksInternal(
            context = TaskStartContext(mode = TaskStartMode.MANUAL),
            continueProfileIds = remaining,
        )
        if (message != null) {
            Timber.w("Next sequence profile failed: %s", message.resolve(application))
            if (state.value.dialog == null) {
                showStartFailedDialog(message)
            }
            pendingSequenceProfileIds = emptyList()
            sequenceTotal = 0
        }
    }

    fun onStopTasks() {
        sequenceUserStop = true
        pendingSequenceProfileIds = emptyList()
        sequenceTotal = 0
        achievementReporter.reportTaskStopped()
        viewModelScope.launch {
            compositionService.stop()
        }
    }

    fun onClearLogs() {
        sessionLogger.clearRuntimeLogs()
    }

    fun onToggleGameSound() {
        viewModelScope.launch {
            val ok = gameMuteCoordinator.toggle(chainState.clientType)
            if (!ok) {
                _effects.send(UiEffect.toast(R.string.bg_toast_mute_failed))
            }
        }
    }

    /**
     * 调试用：请求远端进程抓取当前帧缓冲并保存 PNG 到 {rootDir}/debug/screenshots，
     * 结果通过 [screenshotMessage] 反馈给 UI。
     *
     * 由远端（shell 进程）直接落盘——它对 userDir/debug 有写权限（同 logcat 抓取），
     * 避免跨进程读取 ashmem 被 SELinux 拒绝。
     */
    fun onCaptureDebugScreenshot() {
        viewModelScope.launch(Dispatchers.IO) {
            val savedName = runCatching {
                RemoteServiceManager.getInstanceOrNull()
                    ?.captureFramePng(pathConfig.debugScreenshotsDir)
                    ?.let { File(it).name }
            }.onFailure { Timber.e(it, "captureDebugScreenshot failed") }
                .getOrNull()
            val message = savedName
                ?.let { application.getString(R.string.bg_toast_screenshot_saved, it) }
                ?: application.getString(R.string.bg_toast_screenshot_failed)
            _screenshotMessage.tryEmit(message)
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
                    viewModelScope.launch {
                        val message = startTasksInternal(context = pending.context)
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
        touchPreviewController.onClear()
        super.onCleared()
    }
}
