package com.aliothmoon.maameow.maa.callback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 任务链运行状态
 */
enum class TaskRunStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    ERROR
}

/**
 * 单条任务链的运行信息
 */
data class TaskRunInfo(
    val taskId: Int,
    val taskChain: String,
    val status: TaskRunStatus,
    val nodeId: String? = null,
)

/**
 * 跟踪每条任务链的运行状态（taskId → status）。
 *
 * - 任务 append 时 [register] 注册为 PENDING
 * - 回调到达时通过 [updateStatus] 推进状态
 * - 全部完成或停止时 [clear]
 *
 * [tasks] StateFlow 供 UI 消费。
 */
class TaskChainStatusTracker {

    private val _tasks = MutableStateFlow<List<TaskRunInfo>>(emptyList())
    val tasks: StateFlow<List<TaskRunInfo>> = _tasks.asStateFlow()

    private val taskMap = LinkedHashMap<Int, TaskRunInfo>()

    fun register(taskId: Int, taskChain: String, nodeId: String? = null) {
        taskMap[taskId] = TaskRunInfo(taskId, taskChain, TaskRunStatus.PENDING, nodeId)
        emit()
    }

    fun updateStatus(taskId: Int, status: TaskRunStatus) {
        taskMap.computeIfPresent(taskId) { _, info -> info.copy(status = status) }
        emit()
    }

    fun getNodeId(taskId: Int): String? = taskMap[taskId]?.nodeId

    fun clear() {
        taskMap.clear()
        emit()
    }

    private fun emit() {
        _tasks.value = taskMap.values.toList()
    }
}
