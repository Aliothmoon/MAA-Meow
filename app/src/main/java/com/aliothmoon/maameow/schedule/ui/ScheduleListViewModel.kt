package com.aliothmoon.maameow.schedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.model.TaskSequenceConfig
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import com.aliothmoon.maameow.schedule.model.ScheduleTargetKind
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
    val sequenceConfigs: List<TaskSequenceConfig> = emptyList(),
    val isLoading: Boolean = false
)

class ScheduleListViewModel(
    private val repository: ScheduleStrategyRepository,
    private val taskChainState: TaskChainState,
    private val alarmManager: ScheduleAlarmManager,
) : ViewModel() {
    private val _state = MutableStateFlow(ScheduleListUiState())
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
            taskChainState.sequenceConfigs.collect { configs ->
                _state.update { it.copy(sequenceConfigs = configs) }
            }
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

    fun resolveTargetLabel(strategy: ScheduleStrategy): String? {
        return when (strategy.targetKind) {
            ScheduleTargetKind.PROFILE ->
                _state.value.profiles.find { it.id == strategy.profileId }?.name
            ScheduleTargetKind.SEQUENCE ->
                _state.value.sequenceConfigs.find { it.id == strategy.sequenceConfigId }?.name
        }
    }
}
