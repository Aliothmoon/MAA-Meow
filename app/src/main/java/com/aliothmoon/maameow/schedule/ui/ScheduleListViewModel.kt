package com.aliothmoon.maameow.schedule.ui

import android.app.Application
import android.app.KeyguardManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.preferences.UnlockGestureStore
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.models.UnlockCredential
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.ScheduleHealthIssue
import com.aliothmoon.maameow.schedule.model.ScheduleHealthLogic
import com.aliothmoon.maameow.schedule.model.ScheduleHealthSnapshot
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import com.aliothmoon.maameow.schedule.service.ScheduleAlarmManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

data class ScheduleListUiState(
    val strategies: List<ScheduleStrategy> = emptyList(),
    val profiles: List<TaskProfile> = emptyList(),
    val isLoading: Boolean = false,
    /** 系统是否允许精确闹钟，未允许时暂停注册 */
    val exactAlarmAllowed: Boolean = true,
    /** 系统有没有精确闹钟开关页（API 31+）；没有就别摆那个入口 */
    val exactAlarmConfigurable: Boolean = false,
    /** 空列表 = 全部通过，健康卡隐藏 */
    val healthIssues: List<ScheduleHealthIssue> = emptyList(),
    val startupBackend: RemoteBackend = RemoteBackend.SHIZUKU,
)

class ScheduleListViewModel(
    private val app: Application,
    private val repository: ScheduleStrategyRepository,
    private val taskChainState: TaskChainState,
    private val alarmManager: ScheduleAlarmManager,
    private val permissionManager: PermissionManager,
    appSettingsManager: AppSettingsManager,
    unlockGestureStore: UnlockGestureStore,
) : ViewModel() {

    /**
     * 精确闹钟没有系统级 Flow，只能从设置页回来时重读
     *
     * 用值本身当上游而非 tick 计数：combine 里不必再查一次 AlarmManager，值没变也不会重算
     */
    private val exactAlarmAllowed = MutableStateFlow(alarmManager.canScheduleExact())

    /** 锁屏方式无 Flow，回前台重读 */
    private val deviceSecure = MutableStateFlow(readDeviceSecure())

    private val unlockCredentialReady = combine(
        appSettingsManager.wakeUnlockType,
        appSettingsManager.wakeCredential,
        unlockGestureStore.gesture,
    ) { type, pin, gesture ->
        UnlockCredential.isReady(type, pin, hasGesture = gesture != null)
    }.distinctUntilChanged()

    private val _state = MutableStateFlow(
        ScheduleListUiState(
            exactAlarmAllowed = exactAlarmAllowed.value,
            exactAlarmConfigurable = alarmManager.hasExactAlarmToggle(),
        )
    )
    val state: StateFlow<ScheduleListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.strategies.collect { strategies ->
                _state.update { it.copy(strategies = strategies) }
            }
        }
        viewModelScope.launch {
            taskChainState.profiles.collect { profiles ->
                _state.update { it.copy(profiles = profiles) }
            }
        }
        viewModelScope.launch {
            combine(
                repository.strategies,
                permissionManager.state,
                exactAlarmAllowed,
                deviceSecure,
                unlockCredentialReady,
            ) { strategies, permissions, exactAlarm, secure, credentialReady ->
                ScheduleHealthLogic.failingIssues(
                    ScheduleHealthSnapshot(
                        backendGranted = permissions.remoteAccessGranted,
                        backendAvailable = permissions.isStartupBackendAvailable(permissions.startupBackend),
                        batteryWhitelist = permissions.batteryWhitelist,
                        notification = permissions.notification,
                        exactAlarmAllowed = exactAlarm,
                        overlayGranted = permissions.overlay,
                        overlayNeeded = ScheduleHealthLogic.overlayNeeded(strategies),
                        unlockCredentialMissing = ScheduleHealthLogic.unlockCredentialMissing(
                            deviceSecure = secure,
                            credentialReady = credentialReady,
                            strategies = strategies,
                        ),
                    )
                ) to permissions.startupBackend
            }.collect { (issues, backend) ->
                _state.update { it.copy(healthIssues = issues, startupBackend = backend) }
            }
        }
    }

    fun refreshDeviceSecure() {
        deviceSecure.value = readDeviceSecure()
    }

    private fun readDeviceSecure(): Boolean =
        app.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** 从系统设置回来后重读权限并恢复调度 */
    fun refreshExactAlarmPermission() {
        val allowed = alarmManager.canScheduleExact()
        val wasAllowed = exactAlarmAllowed.value
        exactAlarmAllowed.value = allowed
        _state.update {
            it.copy(
                exactAlarmAllowed = allowed,
                exactAlarmConfigurable = alarmManager.hasExactAlarmToggle(),
            )
        }
        if (allowed != wasAllowed) {
            alarmManager.rescheduleAll(_state.value.strategies)
        }
    }

    fun onToggleEnabled(strategyId: String, enabled: Boolean) {
        viewModelScope.launch {
            val strategy = repository.getById(strategyId) ?: return@launch
            val updated = strategy.copy(enabled = enabled)
            repository.setEnabled(strategyId, enabled)

            if (enabled) {
                alarmManager.scheduleNext(updated)
            } else {
                alarmManager.cancel(strategyId)
            }
        }
    }

    fun onDeleteStrategy(strategyId: String) {
        viewModelScope.launch {
            alarmManager.cancel(strategyId)
            repository.remove(strategyId)
        }
    }

    /** 计算策略的下次执行时间，用于 UI 显示 */
    fun getNextTriggerTime(strategy: ScheduleStrategy): String? {
        val next = alarmManager.computeNextTrigger(strategy)
        return next?.let {
            val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            it.format(formatter)
        }
    }
}
