package com.aliothmoon.maameow.maa.callback

import android.content.Context
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.achievement.AchievementEvents
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.models.NotificationImage
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.FightDropsRefresher
import com.aliothmoon.maameow.domain.service.MaaNotificationCenter
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import com.aliothmoon.maameow.utils.i18n.resolve
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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

    /** taskId → 任务链开始的 nanoTime，完成时换算用时 */
    private val chainStartNanos = ConcurrentHashMap<Int, Long>()

    /**
     * TaskChainStart (10001): 任务链开始
     */
    fun onTaskChainStart(details: JSONObject) {
        val taskId = details.getIntValue("taskid", 0)
        subTaskHandler.clearThemeTarget(taskId)
        statusTracker.updateStatus(taskId, TaskRunStatus.IN_PROGRESS)
        chainStartNanos[taskId] = System.nanoTime()

        refreshDropsIfNeeded(taskId)

        val taskName = resolveTaskName(details)
        sessionLogger.append("${str("StartTask")}$taskName", LogLevel.TRACE)
    }

    private fun clearSessionScopedState() {
        chainStartNanos.clear()
        statusTracker.clear()
        dropsRefresher.clear()
    }

    /**
     * 本轮主任务队列出错的任务名，与 resolveTaskName 同源；statusTracker 已按 taskId 记过，不另起登记
     * slot 由 Analyze 注入，链外路径（工具箱 / 作业 / 牛杂）为 null，各有独立归属，不计入
     */
    private fun failedTaskNames(): List<String> =
        statusTracker.tasks.value
            .filter { it.slot != null && it.status == TaskRunStatus.ERROR }
            .map { it.logName?.resolve(appContext) ?: str(it.taskChain) }

    private fun resolveTaskName(details: JSONObject): String =
        statusTracker.getLogName(details.getIntValue("taskid", 0))?.resolve(appContext)
            ?: str(details.getString("taskchain") ?: "Unknown")

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

            is FightDropsRefresher.RefreshOutcome.Updated -> {
                sessionLogger.append(
                    appContext.getString(
                        R.string.runlog_depot_plan_inventory_insufficient,
                        outcome.logLabel,
                        outcome.dropName,
                        outcome.current,
                        outcome.target,
                        outcome.need,
                    ),
                    LogLevel.INFO,
                )
                outcome.logLabel to outcome.applied
            }

            is FightDropsRefresher.RefreshOutcome.SanityInsufficient -> {
                sessionLogger.append(
                    appContext.getString(
                        R.string.runlog_depot_plan_sanity_insufficient,
                        outcome.logLabel,
                        outcome.estimatedSanity,
                        outcome.apCost,
                    ),
                    LogLevel.INFO,
                )
                outcome.logLabel to outcome.applied
            }
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
        val taskId = details.getIntValue("taskid", 0)
        subTaskHandler.clearThemeTarget(taskId)
        statusTracker.updateStatus(taskId, TaskRunStatus.ERROR)
        chainStartNanos.remove(taskId)

        val taskchain = details.getString("taskchain") ?: "Unknown"
        val taskName = resolveTaskName(details)
        // details.error 为 Core 侧 TaskExceptionKind 名（如 OutOfMemory），普通识别错误无此字段
        val message = if (exceptionKind(details) == "OutOfMemory") {
            str("OutOfMemoryError", taskName)
        } else {
            "${str("TaskError")}$taskName"
        }
        sessionLogger.append(message, LogLevel.ERROR)
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
        subTaskHandler.clearThemeTarget(taskId)
        statusTracker.updateStatus(taskId, TaskRunStatus.COMPLETED)
        dropsRefresher.onTaskCompleted(taskId)

        val taskchain = details.getString("taskchain") ?: "Unknown"
        val taskName = resolveTaskName(details)
        // 对齐上游 #18433：完成日志带上本任务链用时
        val taskTime = chainStartNanos.remove(taskId)
            ?.let { str("TaskTime", formatTaskDuration(System.nanoTime() - it)) }
            .orEmpty()
        sessionLogger.append("${str("CompleteTask")}$taskName$taskTime", LogLevel.SUCCESS)

        // 任一仓库识别（库存保持自带的或独立的数据更新）都会纠正缓存，复查预检时跳过的计划
        if (taskchain == "Depot") {
            dropsRefresher.reviewSkippedPlans().forEach {
                sessionLogger.append(
                    appContext.getString(
                        R.string.runlog_depot_plan_stale_skipped,
                        it.taskName, it.no, it.dropName, it.current, it.target,
                    ),
                    LogLevel.WARNING,
                )
            }
        }

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
    fun onAllTasksCompleted(asStopped: Boolean = false, screenshot: NotificationImage? = null) {
        // 名单挂在 statusTracker 上，clearSessionScopedState 会清掉，先取快照
        val failedTaskNames = failedTaskNames()
        clearSessionScopedState()

        // 手动停止本就不是「全部完成」，标题不跟着改，出错清单另起一段给出
        val hasTaskErrors = failedTaskNames.isNotEmpty()
        val titledWithErrors = hasTaskErrors && !asStopped
        val headline = buildString {
            append(
                if (titledWithErrors) {
                    str("TaskCompletedWithErrors", failedTaskNames.joinToString(", "))
                } else {
                    str("AllTasksComplete")
                }
            )

            // 任务总耗时
            val startMillis = sessionLogger.sessionStartTimeMillis
            if (startMillis > 0) {
                val elapsed = System.currentTimeMillis() - startMillis
                achievementReporter.reportAllTasksCompleted(elapsed)
                val h = elapsed / 3_600_000
                val m = (elapsed % 3_600_000) / 60_000
                val s = (elapsed % 60_000) / 1_000
                append(" (")
                if (h > 0) append("${h}h ")
                if (h > 0 || m > 0) append("${m}m ")
                append("${s}s)")
            } else {
                achievementReporter.reportAllTasksCompleted()
            }
        }
        val sanityReport = subTaskHandler.lastSanitySnapshot?.let(::buildSanityReport)

        val message = listOfNotNull(headline, sanityReport).joinToString("\n")
        if (titledWithErrors) {
            // 标题连同出错任务名标红，理智报告留在下一段默认色
            sessionLogger.append(headline, LogLevel.ERROR)
            sanityReport?.let { sessionLogger.append(it, LogLevel.MESSAGE) }
        } else {
            sessionLogger.append(message, if (asStopped) LogLevel.INFO else LogLevel.SUCCESS)
        }

        if (asStopped && hasTaskErrors) {
            sessionLogger.append(
                failedTaskNames.joinToString("\n", prefix = str("TaskErrorSummaryTitle") + "\n"),
                LogLevel.ERROR,
            )
        }
        if (!asStopped) {
            // 出错任务名已在标题里，通知正文不再重复清单
            notificationCenter.notifyAllTasksCompleted(message, screenshot)
        }

        callbackScope.launch {
            taskChainState.clearRecruitUseExpeditedFlags()
        }
    }

    private fun buildSanityReport(snapshot: SubTaskHandler.SanitySnapshot): String = buildString {
        append(str("CurrentSanity", snapshot.current, snapshot.max))
        if (snapshot.current >= snapshot.max) return@buildString

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

        append("\n")
        append(
            str(
                "SanityRecovery",
                recoveryTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                remainStr
            )
        )
        // TODO: 延迟定时提醒（理智恢复前 6 分钟推送通知）
    }

    /** Core 写在 details.details.error；WPF 读的是根级 error，两处都兼容 */
    private fun exceptionKind(details: JSONObject): String? =
        details.getJSONObject("details")?.getString("error") ?: details.getString("error")

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

/** 格式同上游，如 0h 3m 25s；小时不按天折回 */
internal fun formatTaskDuration(nanos: Long): String {
    val total = TimeUnit.NANOSECONDS.toSeconds(nanos).coerceAtLeast(0)
    return "${total / 3600}h ${total % 3600 / 60}m ${total % 60}s"
}
