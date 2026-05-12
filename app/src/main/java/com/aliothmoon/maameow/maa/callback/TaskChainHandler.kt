package com.aliothmoon.maameow.maa.callback

import android.content.Context
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.service.MaaNotificationCenter
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import com.aliothmoon.maameow.maa.AsstMsg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 处理 TaskChain 级别回调（msg 10000-10004 + AllTasksCompleted=3）
 */
class TaskChainHandler(
    applicationContext: Context,
    private val sessionLogger: MaaSessionLogger,
    private val statusTracker: TaskChainStatusTracker,
    private val notificationCenter: MaaNotificationCenter,
    private val subTaskHandler: SubTaskHandler,
    private val taskChainState: TaskChainState,
) {
    // 回调路径用于 suspend 的 TaskChainState 更新；独立于任一生命周期
    private val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val resources = applicationContext.resources
    private val packageName = applicationContext.packageName

    /**
     * 处理 TaskChain 回调消息
     */
    fun handle(msg: AsstMsg, details: JSONObject) {
        val taskId = details.getIntValue("taskid", 0)
        when (msg) {
            AsstMsg.TaskChainStart -> {
                statusTracker.updateStatus(taskId, TaskRunStatus.IN_PROGRESS)
                handleTaskChainStart(details)
            }

            AsstMsg.TaskChainCompleted -> {
                statusTracker.updateStatus(taskId, TaskRunStatus.COMPLETED)
                handleTaskChainCompleted(details)
            }

            AsstMsg.TaskChainError -> {
                statusTracker.updateStatus(taskId, TaskRunStatus.ERROR)
                handleTaskChainError(details)
            }

            AsstMsg.TaskChainExtraInfo -> handleTaskChainExtraInfo(details)

            AsstMsg.TaskChainStopped -> {
                statusTracker.clear()
                handleTaskChainStopped(details)
            }

            AsstMsg.AllTasksCompleted -> {
                statusTracker.clear()
                handleAllTasksCompleted()
            }

            else -> Timber.w("TaskChainHandler received unexpected msg: $msg")
        }
    }

    /**
     * TaskChainError (10000): 任务链错误
     */
    private fun handleTaskChainError(details: JSONObject) {
        val taskchain = details.getString("taskchain") ?: "Unknown"
        val taskName = str(taskchain)
        sessionLogger.append("${str("TaskError")}$taskName", LogLevel.ERROR)
        notificationCenter.notifyTaskError(taskName)
    }

    /**
     * TaskChainStart (10001): 任务链开始
     */
    private fun handleTaskChainStart(details: JSONObject) {
        val taskchain = details.getString("taskchain") ?: "Unknown"
        val taskName = str(taskchain)
        sessionLogger.append("${str("StartTask")}$taskName", LogLevel.TRACE)
    }

    /**
     * TaskChainCompleted (10002): 任务链完成
     */
    private fun handleTaskChainCompleted(details: JSONObject) {
        val taskchain = details.getString("taskchain") ?: "Unknown"
        val taskName = str(taskchain)
        sessionLogger.append("${str("CompleteTask")}$taskName", LogLevel.SUCCESS)

        if (taskchain == "Infrast") {
            val taskId = details.getIntValue("taskid", 0)
            val nodeId = statusTracker.getNodeId(taskId)
            if (nodeId != null) {
                callbackScope.launch {
                    val result = taskChainState.incrementCustomInfrastPlanSelect(nodeId)
                        ?: return@launch
                    val (newIndex, newName) = result
                    sessionLogger.append(
                        str("CustomInfrastPlanIndexAutoSwitch"),
                        LogLevel.MESSAGE
                    )
                    sessionLogger.append(
                        newName ?: "Plan ${('A' + newIndex)}",
                        LogLevel.MESSAGE
                    )
                }
            }
        }
    }

    /**
     * TaskChainExtraInfo (10003): 任务链额外信息
     */
    private fun handleTaskChainExtraInfo(details: JSONObject) {
        val what = details.getString("what")
        when (what) {
            "RoutingRestart" -> {
                val why = details.getString("why")
                if (why == "TooManyBattlesAhead") {
                    val cost = details.getString("node_cost") ?: "?"
                    sessionLogger.append(
                        str("RoutingRestartTooManyBattles", cost),
                        LogLevel.WARNING
                    )
                } else {
                    Timber.d("TaskChainExtraInfo RoutingRestart with unhandled why=$why")
                }
            }

            else -> {
                Timber.d("TaskChainExtraInfo unhandled what=$what, details=$details")
            }
        }
    }

    /**
     * TaskChainStopped (10004): 任务链停止（用户手动停止）
     */
    private fun handleTaskChainStopped(details: JSONObject) {
        sessionLogger.append(str("TaskStopped"), LogLevel.INFO)
    }

    /**
     * AllTasksCompleted (3): 所有任务完成
     * 附带任务总耗时和理智恢复时间信息
     */
    private fun handleAllTasksCompleted() {
        val sb = StringBuilder(str("AllTasksComplete", ""))

        // 任务总耗时
        val startMillis = sessionLogger.sessionStartTimeMillis
        if (startMillis > 0) {
            val elapsed = System.currentTimeMillis() - startMillis
            val h = elapsed / 3_600_000
            val m = (elapsed % 3_600_000) / 60_000
            val s = (elapsed % 60_000) / 1_000
            val timeStr = buildString {
                if (h > 0) append("${h}h ")
                if (h > 0 || m > 0) append("${m}m ")
                append("${s}s")
            }
            sb.append(" ($timeStr)")
        }

        // 理智恢复时间
        val snapshot = subTaskHandler.lastSanitySnapshot
        if (snapshot != null) {
            sb.append("\n")
            sb.append(str("CurrentSanity", snapshot.current, snapshot.max))

            if (snapshot.current < snapshot.max) {
                val recoveryMinutes = (snapshot.max - snapshot.current) * 6L
                val recoveryMillis = snapshot.reportTimeMillis + recoveryMinutes * 60_000
                val recoveryTime = Instant.ofEpochMilli(recoveryMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                val remainMinutes = ((recoveryMillis - System.currentTimeMillis()) / 60_000)
                    .coerceAtLeast(0)
                val rh = remainMinutes / 60
                val rm = remainMinutes % 60
                val remainStr = buildString {
                    if (rh > 0) append("${rh}h ")
                    append("${rm}m")
                }

                sb.append("\n")
                sb.append(str("SanityRecovery",
                    recoveryTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                    remainStr
                ))
                // TODO: 延迟定时提醒（理智恢复前 6 分钟推送通知）
            }
        }

        val message = sb.toString()
        sessionLogger.append(message, LogLevel.SUCCESS)
        notificationCenter.notifyAllTasksCompleted(message)
    }

    /**
     * 辅助方法：获取 i18n 字符串（无参数）
     */
    private fun str(key: String): String {
        return MaaStringRes.getString(resources, packageName, key)
    }

    /**
     * 辅助方法：获取 i18n 字符串（带参数）
     */
    private fun str(key: String, vararg args: Any): String {
        return MaaStringRes.getString(resources, packageName, key, *args)
    }
}
