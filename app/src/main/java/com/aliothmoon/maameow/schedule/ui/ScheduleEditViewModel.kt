package com.aliothmoon.maameow.schedule.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.ScheduleHealthIssue
import com.aliothmoon.maameow.schedule.model.ScheduleHealthLogic
import com.aliothmoon.maameow.schedule.model.ScheduleHealthSnapshot
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import com.aliothmoon.maameow.schedule.model.ScheduleType
import com.aliothmoon.maameow.schedule.service.ScheduleAlarmManager
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

data class ScheduleEditUiState(
    val isNew: Boolean = true,
    val strategyId: String? = null,
    val name: String = "",
    val scheduleType: ScheduleType = ScheduleType.FIXED_TIME,
    // FIXED_TIME
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val executionTimes: List<LocalTime> = emptyList(),
    // INTERVAL
    val startTimeMs: Long? = null,
    val intervalDays: Int = 0,
    val intervalHours: Int = 0,
    // 通用
    val profiles: List<TaskProfile> = emptyList(),
    val selectedProfileId: String? = null,
    val forceStart: Boolean = false,
    val autoScreenSaver: Boolean = false,
    val autoSleepAfterTask: Boolean = false,
    val skipAutoSleepIfAwake: Boolean = false,
    val closeGameAfterTask: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    /** 空列表 = 无需向导 */
    val wizardPending: List<ScheduleHealthIssue> = emptyList(),
    val errorMessage: UiText? = null
)

/** 「任务结束后关闭游戏」当前实际会不会生效 */
enum class CloseGameEffect {
    ForegroundInactive,
    GlobalOverride,
    StrategyActive,
    Inactive,
}

class ScheduleEditViewModel(
    private val repository: ScheduleStrategyRepository,
    private val taskChainState: TaskChainState,
    private val scheduleAlarmManager: ScheduleAlarmManager,
    private val permissionManager: PermissionManager,
    appSettings: AppSettingsManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ScheduleEditUiState())
    val state: StateFlow<ScheduleEditUiState> = _state.asStateFlow()

    /** 与 LaunchPipeline 的判定同源，改一处必须改另一处 */
    val closeGameEffect: StateFlow<CloseGameEffect> = combine(
        _state,
        appSettings.runMode,
        appSettings.closeAppOnTaskEnd,
    ) { state, runMode, globalOn ->
        when {
            runMode != RunMode.BACKGROUND -> CloseGameEffect.ForegroundInactive
            globalOn -> CloseGameEffect.GlobalOverride
            state.closeGameAfterTask -> CloseGameEffect.StrategyActive
            else -> CloseGameEffect.Inactive
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CloseGameEffect.Inactive)

    private var strategyId: String? = null
    private var existingStrategy: ScheduleStrategy? = null

    /** 加载已有策略（编辑模式），或初始化默认选择（新建模式） */
    fun loadStrategy(context: Context, id: String?) {
        viewModelScope.launch {
            // 等待 Profile 数据加载完成
            taskChainState.isLoaded.filter { it }.first()
            val profiles = taskChainState.profiles.value

            if (id != null) {
                repository.isLoaded.filter { it }.first()

                val strategy = repository.getById(id)
                if (strategy != null) {
                    strategyId = id
                    existingStrategy = strategy
                    val totalMinutes = strategy.intervalMinutes ?: 0
                    _state.value = ScheduleEditUiState(
                        isNew = false,
                        strategyId = id,
                        name = strategy.name,
                        scheduleType = strategy.scheduleType,
                        daysOfWeek = strategy.daysOfWeek,
                        executionTimes = strategy.executionTimes,
                        startTimeMs = strategy.startTimeMs,
                        intervalDays = totalMinutes / (24 * 60),
                        intervalHours = (totalMinutes % (24 * 60)) / 60,
                        profiles = profiles,
                        selectedProfileId = strategy.profileId,
                        forceStart = strategy.forceStart,
                        autoScreenSaver = strategy.autoScreenSaver,
                        autoSleepAfterTask = strategy.autoSleepAfterTask,
                        skipAutoSleepIfAwake = strategy.skipAutoSleepIfAwake,
                        closeGameAfterTask = strategy.closeGameAfterTask,
                    )
                    return@launch
                }
            }
            // 新建策略 — 默认选中当前活跃 Profile
            existingStrategy = null
            val defaultName = context.getString(
                R.string.schedule_default_name,
                repository.strategies.value.size + 1
            )
            _state.value = ScheduleEditUiState(
                name = defaultName,
                profiles = profiles,
                selectedProfileId = taskChainState.profileId.value.ifEmpty { profiles.firstOrNull()?.id },
            )
        }
    }

    fun onNameChanged(name: String) {
        _state.update { it.copy(name = name) }
    }

    fun onScheduleTypeChanged(type: ScheduleType) {
        _state.update { it.copy(scheduleType = type) }
    }

    fun onStartTimeChanged(epochMs: Long) {
        _state.update { it.copy(startTimeMs = epochMs) }
    }

    fun onIntervalDaysChanged(days: Int) {
        _state.update { it.copy(intervalDays = days.coerceAtLeast(0)) }
    }

    fun onIntervalHoursChanged(hours: Int) {
        _state.update { it.copy(intervalHours = hours.coerceIn(0, 23)) }
    }

    fun onSelectProfile(profileId: String) {
        _state.update { it.copy(selectedProfileId = profileId) }
    }

    fun onToggleAllDays() {
        _state.update { state ->
            val allSelected = DayOfWeek.entries.all { it in state.daysOfWeek }
            state.copy(daysOfWeek = if (allSelected) emptySet() else DayOfWeek.entries.toSet())
        }
    }

    fun onToggleDay(day: DayOfWeek) {
        _state.update { state ->
            val newDays = if (day in state.daysOfWeek) {
                state.daysOfWeek - day
            } else {
                state.daysOfWeek + day
            }
            state.copy(daysOfWeek = newDays)
        }
    }

    fun onAddTime(time: LocalTime) {
        _state.update { state ->
            if (time !in state.executionTimes) {
                state.copy(executionTimes = (state.executionTimes + time).sorted())
            } else {
                state
            }
        }
    }

    fun onRemoveTime(time: LocalTime) {
        _state.update { state ->
            state.copy(executionTimes = state.executionTimes - time)
        }
    }

    fun onForceStartChanged(value: Boolean) {
        _state.update { it.copy(forceStart = value) }
    }

    fun onAutoScreenSaverChanged(value: Boolean) {
        _state.update { it.copy(autoScreenSaver = value) }
    }

    fun onAutoSleepAfterTaskChanged(value: Boolean) {
        _state.update { it.copy(autoSleepAfterTask = value) }
    }

    fun onSkipAutoSleepIfAwakeChanged(value: Boolean) {
        _state.update { it.copy(skipAutoSleepIfAwake = value) }
    }

    fun onCloseGameAfterTaskChanged(value: Boolean) {
        _state.update { it.copy(closeGameAfterTask = value) }
    }

    fun onReplaceTime(old: LocalTime, new: LocalTime) {
        _state.update { state ->
            val updated =
                state.executionTimes.map { if (it == old) new else it }.distinct().sorted()
            state.copy(executionTimes = updated)
        }
    }

    fun onSave(context: Context) {
        val current = _state.value
        if (current.name.isBlank()) {
            _state.update { it.copy(errorMessage = uiTextOf(R.string.schedule_error_name_required)) }
            return
        }
        if (current.selectedProfileId == null) {
            _state.update { it.copy(errorMessage = uiTextOf(R.string.schedule_error_profile_required)) }
            return
        }
        when (current.scheduleType) {
            ScheduleType.FIXED_TIME -> {
                if (current.daysOfWeek.isEmpty()) {
                    _state.update { it.copy(errorMessage = uiTextOf(R.string.schedule_error_days_required)) }
                    return
                }
                if (current.executionTimes.isEmpty()) {
                    _state.update { it.copy(errorMessage = uiTextOf(R.string.schedule_error_times_required)) }
                    return
                }
            }

            ScheduleType.INTERVAL -> {
                if (current.startTimeMs == null) {
                    _state.update { it.copy(errorMessage = uiTextOf(R.string.schedule_error_start_time_required)) }
                    return
                }
                val totalMinutes = current.intervalDays * 24 * 60 + current.intervalHours * 60
                if (totalMinutes < 60) {
                    _state.update { it.copy(errorMessage = uiTextOf(R.string.schedule_error_interval_too_short)) }
                    return
                }
            }
        }

        _state.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            runCatching {
                val intervalMinutes = if (current.scheduleType == ScheduleType.INTERVAL) {
                    current.intervalDays * 24 * 60 + current.intervalHours * 60
                } else null

                val strategy = existingStrategy?.copy(
                    name = current.name.trim(),
                    scheduleType = current.scheduleType,
                    daysOfWeek = current.daysOfWeek,
                    executionTimes = current.executionTimes,
                    startTimeMs = current.startTimeMs,
                    intervalMinutes = intervalMinutes,
                    profileId = current.selectedProfileId,
                    forceStart = current.forceStart,
                    autoScreenSaver = current.autoScreenSaver,
                    autoSleepAfterTask = current.autoSleepAfterTask,
                    skipAutoSleepIfAwake = current.skipAutoSleepIfAwake,
                    closeGameAfterTask = current.closeGameAfterTask,
                ) ?: ScheduleStrategy(
                    id = strategyId ?: UUID.randomUUID().toString(),
                    name = current.name.trim(),
                    enabled = true,
                    scheduleType = current.scheduleType,
                    daysOfWeek = current.daysOfWeek,
                    executionTimes = current.executionTimes,
                    startTimeMs = current.startTimeMs,
                    intervalMinutes = intervalMinutes,
                    profileId = current.selectedProfileId,
                    forceStart = current.forceStart,
                    autoScreenSaver = current.autoScreenSaver,
                    autoSleepAfterTask = current.autoSleepAfterTask,
                    skipAutoSleepIfAwake = current.skipAutoSleepIfAwake,
                    closeGameAfterTask = current.closeGameAfterTask,
                )

                if (current.isNew) {
                    repository.add(strategy)
                } else {
                    repository.update(strategy)
                }

                scheduleAlarmManager.cancel(strategy.id)
                scheduleAlarmManager.scheduleNext(strategy)

                // 检查关键权限
                refreshPermissionChecks()

                _state.update {
                    it.copy(
                        isSaving = false,
                        saveSuccess = true,
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = uiTextOf(
                            R.string.schedule_error_save_failed,
                            e.message ?: ""
                        )
                    )
                }
            }
        }
    }

    /** 悬浮窗仅在策略勾选屏保时纳入，免得向导走完又被健康卡 nag 一遍 */
    fun refreshPermissionChecks() {
        // 先刷新，避免依赖 onResume 回调顺序
        permissionManager.refresh()
        val permissions = permissionManager.permissions
        _state.update {
            it.copy(
                wizardPending = ScheduleHealthLogic.wizardItems(
                    ScheduleHealthSnapshot(
                        backendGranted = permissions.remoteAccessGranted,
                        batteryWhitelist = permissions.batteryWhitelist,
                        notification = permissions.notification,
                        exactAlarmAllowed = scheduleAlarmManager.canScheduleExact(),
                        overlayGranted = permissions.overlay,
                        overlayNeeded = it.autoScreenSaver,
                    )
                )
            )
        }
    }

    fun onDismissError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
