package com.aliothmoon.maameow.maa.callback

import com.aliothmoon.maameow.maa.task.TaskSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TaskRunStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    ERROR
}

data class TaskRunInfo(
    val taskId: Int,
    val taskChain: String,
    val status: TaskRunStatus,
    val slot: TaskSlot? = null,
)

/** taskId → 运行状态；[tasks] 供 UI。 */
class TaskChainStatusTracker {

    private val _tasks = MutableStateFlow<List<TaskRunInfo>>(emptyList())
    val tasks: StateFlow<List<TaskRunInfo>> = _tasks.asStateFlow()

    private val registry = LinkedHashMap<Int, TaskRunInfo>()

    fun register(taskId: Int, taskChain: String, slot: TaskSlot? = null) {
        registry[taskId] = TaskRunInfo(taskId, taskChain, TaskRunStatus.PENDING, slot)
        emit()
    }

    fun updateStatus(taskId: Int, status: TaskRunStatus) {
        registry.computeIfPresent(taskId) { _, info -> info.copy(status = status) }
        emit()
    }

    fun getNodeId(taskId: Int): String? = registry[taskId]?.slot?.nodeId

    fun clear() {
        registry.clear()
        emit()
    }

    private fun emit() {
        _tasks.value = registry.values.toList()
    }
}
