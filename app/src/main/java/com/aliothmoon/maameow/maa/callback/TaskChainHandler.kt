package com.aliothmoon.maameow.maa.callback

import android.content.Context
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.data.achievement.AchievementEvents
import com.aliothmoon.maameow.data.achievement.AchievementRepository

import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.FightDropsRefresher
import com.aliothmoon.maameow.domain.service.MaaNotificationCenter
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
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
    private val achievementRepository: AchievementRepository,
    private val achievementReporter: AchievementReporter,
    private val dropsRefresher: FightDropsRefresher,
) {
    // 回调路径用于 suspend 的 TaskChainState 更新；独立于任一生命周期
    private val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val resources = applicationContext.resources
    private val packageName = applicationContext.packageName
    private val appContext = applicationContext

    /**
     * TaskChainStart (10001): 任务链开始
     */
    fun onTaskChainStart(details: JSONObject) {
        val taskId = details.getIntValue("taskid", 0)
        statusTracker.updateStatus(taskId, TaskRunStatus.IN_PROGRESS)

        refreshDropsIfNeeded(taskId)

        val taskName = str(details.getString("taskchain") ?: "Unknown")
        sessionLogger.append("${str("StartTask")}$taskName", LogLevel.TRACE)
    }

    private fun clearSessionScopedState() {
        statusTracker.clear()
        dropsRefresher.clear()
    }

    private fun refreshDropsIfNeeded(taskId: Int) {
        val outcome = dropsRefresher.onTaskStarted(taskId)
        val (logLabel, applied) = when (outcome) {
            FightDropsRefresher.RefreshOutcome.Skipped -> return
            is FightDropsRefresher.RefreshOutcome.Sufficient -> {
                sessionLogger.append(
                    appContext.getString(
                        R.string.runlog_depot_plan_inventory_enough,
                        outcome.logLabel,
                        outcome.dropName,
                        outcome.current,
                        outcome.target,
                    ),
                    LogLevel.INFO,
                )
                outcome.logLabel to outcome.applied
            }

            is FightDropsRefresher.RefreshOutcome.Updated -> outcome.logLabel to outcome.applied
        }
        if (!applied) {
            sessionLogger.append(
                appContext.getString(R.string.runlog_depot_set_params_failed, logLabel),
                LogLevel.WARNING,
            )
        }
    }

    /**
     * TaskChainError (10000): 任务链错误
     */
    fun onTaskChainError(details: JSONObject) {
        statusTracker.updateStatus(details.getIntValue("taskid", 0), TaskRunStatus.ERROR)

        val taskchain = details.getString("taskchain") ?: "Unknown"
        val taskName = str(taskchain)
        sessionLogger.append("${str("TaskError")}$taskName", LogLevel.ERROR)
        notificationCenter.notifyTaskError(taskName)
        callbackScope.launch {
            achievementRepository.report {
                event = AchievementEvents.TASK_CHAIN_ERROR
                "taskchain" to taskchain
            }
        }
    }

    /**
     * TaskChainCompleted (10002): 任务链完成
     */
    fun onTaskChainCompleted(details: JSONObject) {
        val taskId = details.getIntValue("taskid", 0)
        statusTracker.updateStatus(taskId, TaskRunStatus.COMPLETED)

        val taskchain = details.getString("taskchain") ?: "Unknown"
        val taskName = str(taskchain)
        sessionLogger.append("${str("CompleteTask")}$taskName", LogLevel.SUCCESS)

        if (taskchain == "Infrast") {
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
    fun onTaskChainExtraInfo(details: JSONObject) {
        when (val what = details.getString("what")) {
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
    fun onTaskChainStopped() {
        clearSessionScopedState()
        sessionLogger.append(str("TaskStopped"), LogLevel.INFO)
        achievementReporter.reportTaskStopped()
        callbackScope.launch {
            achievementRepository.report {
                event = AchievementEvents.TASK_STOPPED
            }
        }
    }

    /**
     * AllTasksCompleted (3): 所有任务完成
     * 附带任务总耗时和理智恢复时间信息
     */
    fun onAllTasksCompleted() {
        clearSessionScopedState()

        val sb = StringBuilder(str("AllTasksComplete", ""))

        // 任务总耗时
        val startMillis = sessionLogger.sessionStartTimeMillis
        if (startMillis > 0) {
            val elapsed = System.currentTimeMillis() - startMillis
            achievementReporter.reportAllTasksCompleted(elapsed)
            val h = elapsed / 3_600_000
            val m = (elapsed % 3_600_000) / 60_000
            val s = (elapsed % 60_000) / 1_000
            val timeStr = buildString {
                if (h > 0) append("${h}h ")
                if (h > 0 || m > 0) append("${m}m ")
                append("${s}s")
            }
            sb.append(" ($timeStr)")
        } else {
            achievementReporter.reportAllTasksCompleted()
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
                sb.append(
                    str(
                        "SanityRecovery",
                        recoveryTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                        remainStr
                    )
                )
                // TODO: 延迟定时提醒（理智恢复前 6 分钟推送通知）
            }
        }

        val message = sb.toString()
        sessionLogger.append(message, LogLevel.SUCCESS)
        notificationCenter.notifyAllTasksCompleted(message)

        callbackScope.launch {
            taskChainState.clearRecruitUseExpeditedFlags()
        }
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
