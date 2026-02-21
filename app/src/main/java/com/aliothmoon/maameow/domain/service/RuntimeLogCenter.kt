package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.constant.LogConfig
import com.aliothmoon.maameow.data.log.TaskLogWriter
import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.data.model.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class RuntimeLogCenter(
    private val taskLogWriter: TaskLogWriter?
) {
    private val serialDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val serialScope = CoroutineScope(SupervisorJob() + serialDispatcher)

    private val _logs = MutableStateFlow<List<LogItem>>(emptyList())
    val logs: StateFlow<List<LogItem>> = _logs.asStateFlow()

    suspend fun startSession(taskNames: List<String>): Boolean = withContext(serialDispatcher) {
        _logs.value = emptyList()
        taskLogWriter?.startSession(taskNames) ?: true
    }

    fun append(content: String, level: LogLevel = LogLevel.MESSAGE, tooltip: String? = null) {
        append(LogItem(content = content, level = level, tooltip = tooltip))
    }

    fun append(logItem: LogItem) {
        serialScope.launch {
            appendInternal(logItem)
        }
    }

    suspend fun appendAndWait(
        content: String,
        level: LogLevel = LogLevel.MESSAGE,
        tooltip: String? = null
    ) {
        appendAndWait(LogItem(content = content, level = level, tooltip = tooltip))
    }

    suspend fun appendAndWait(logItem: LogItem) {
        withContext(serialDispatcher) {
            appendInternal(logItem)
        }
    }

    fun clearRuntimeLogs() {
        serialScope.launch {
            _logs.value = emptyList()
        }
    }

    suspend fun clearRuntimeLogsAndWait() {
        withContext(serialDispatcher) {
            _logs.value = emptyList()
        }
    }

    fun endSession(status: String = "COMPLETED") {
        serialScope.launch {
            taskLogWriter?.endSession(status)
        }
    }

    suspend fun endSessionAndWait(status: String = "COMPLETED") {
        withContext(serialDispatcher) {
            taskLogWriter?.endSession(status)
        }
    }

    private suspend fun appendInternal(logItem: LogItem) {
        val current = _logs.value
        val trimmed = if (current.size >= LogConfig.MAX_LOG_COUNT) {
            current.drop(current.size - LogConfig.MAX_LOG_COUNT + 1)
        } else {
            current
        }
        _logs.value = trimmed + logItem
        taskLogWriter?.appendLog(logItem)
    }
}
