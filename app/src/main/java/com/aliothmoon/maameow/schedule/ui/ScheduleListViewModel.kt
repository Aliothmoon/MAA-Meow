package com.aliothmoon.maameow.schedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import com.aliothmoon.maameow.schedule.service.ScheduleAlarmManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

data class ScheduleListUiState(
    val strategies: List<ScheduleStrategy> = emptyList(),
    val profiles: List<TaskProfile> = emptyList(),
    val isLoading: Boolean = false,
    /** 系统是否允许精确闹钟；否则退到 setAlarmClock，状态栏会多个闹钟图标 */
    val exactAlarmAllowed: Boolean = true,
    /** 系统有没有精确闹钟开关页（API 31+）；没有就别摆那个入口 */
    val exactAlarmConfigurable: Boolean = false,
)

class ScheduleListViewModel(
    private val repository: ScheduleStrategyRepository,
    private val taskChainState: TaskChainState,
    private val alarmManager: ScheduleAlarmManager,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ScheduleListUiState(
            exactAlarmAllowed = alarmManager.canScheduleExact(),
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
    }

    /** 设置页没有结果回调，从系统开关回来后重读；刚授权时把 setAlarmClock 换回 exact */
    fun refreshExactAlarmPermission() {
        val allowed = alarmManager.canScheduleExact()
        val wasAllowed = _state.value.exactAlarmAllowed
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
