package com.aliothmoon.maameow.schedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.schedule.model.TriggerLogEntry
import com.aliothmoon.maameow.schedule.service.ScheduleTriggerLogger
import com.aliothmoon.maameow.schedule.service.ScheduleTriggerLogger.TriggerLogSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScheduleTriggerLogViewModel(
    private val logger: ScheduleTriggerLogger,
) : ViewModel() {

    private val _summaries = MutableStateFlow<List<TriggerLogSummary>>(emptyList())
    val summaries: StateFlow<List<TriggerLogSummary>> = _summaries.asStateFlow()

    private val _detail = MutableStateFlow<List<TriggerLogEntry>>(emptyList())
    val detail: StateFlow<List<TriggerLogEntry>> = _detail.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _isLoading.value = true
            _summaries.value = logger.getLogSummaries()
            _isLoading.value = false
        }
    }

    fun onLoadDetail(fileName: String) {
        viewModelScope.launch {
            _detail.value = logger.readLogFile(fileName)
        }
    }

    fun onClearDetail() {
        _detail.value = emptyList()
    }

    fun onDeleteLog(fileName: String) {
        viewModelScope.launch {
            logger.deleteLog(fileName)
            _summaries.value = _summaries.value.filter { it.fileName != fileName }
        }
    }

    fun onClearAll() {
        viewModelScope.launch {
            logger.clearAll()
            _summaries.value = emptyList()
        }
    }
}
