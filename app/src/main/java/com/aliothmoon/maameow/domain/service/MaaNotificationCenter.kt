package com.aliothmoon.maameow.domain.service

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.domain.notification.LiveCategory
import com.aliothmoon.maameow.domain.notification.LiveNotifyIds
import com.aliothmoon.maameow.domain.notification.LiveSession
import com.aliothmoon.maameow.domain.notification.LiveSessionCoordinator
import kotlinx.coroutines.flow.StateFlow

/** 聚合实况结果、队列卡片和外推 */
class MaaNotificationCenter(
    context: Context,
    private val eventNotifier: MaaEventNotifier,
    private val externalService: ExternalNotificationService,
    private val settings: NotificationSettingsManager,
    private val liveCoordinator: LiveSessionCoordinator,
) {
    private val appContext = context.applicationContext

    fun notifyAllTasksCompleted(summary: String) {
        val title = appContext.getString(R.string.notification_event_all_tasks_completed)
        publishResult(title, summary, timeoutSec = 120)
        if (settings.sendOnComplete.value) {
            externalService.sendWithLogs("所有任务已完成", summary)
        }
    }

    fun notifyTaskStopped() {
        val title = appContext.getString(R.string.notification_event_task_stopped)
        val text = appContext.getString(R.string.notification_event_task_stopped_text)
        publishResult(title, text, timeoutSec = 15)
    }

    fun notifyTaskError(taskName: String) {
        eventNotifier.notifyTaskError(taskName)
        pushExternal(settings.sendOnError, "任务出错", "任务链 $taskName 执行失败")
    }

    fun notifyStartFailed(message: String) {
        val title = appContext.getString(R.string.notification_event_task_error)
        publishResult(title, message, timeoutSec = 30, isError = true)
    }

    /** [sendExternal] 掉线这类要外推的场景置真，走任务出错开关 */
    fun notifySubTaskFailure(message: String, sendExternal: Boolean = false) {
        eventNotifier.notifySubTaskFailure(message)
        if (sendExternal) {
            pushExternal(settings.sendOnError, message, message)
        }
    }

    fun notifyHandoverRequired(title: String, content: String) {
        eventNotifier.notifyEvent(title, content)
        pushExternal(settings.sendOnComplete, title, content)
    }

    fun notifyRecruitSpecialTag(tag: String) {
        eventNotifier.notifyRecruitSpecialTag(tag)
    }

    fun notifyRecruitRobotTag(tag: String) {
        eventNotifier.notifyRecruitRobotTag(tag)
    }

    fun notifyRecruitHighRarity(level: Int) {
        eventNotifier.notifyRecruitHighRarity(level)
    }

    fun notifyServiceDied() {
        val title = appContext.getString(R.string.notification_event_service_died)
        val text = appContext.getString(R.string.notification_event_service_died_text)
        // 服务可能在空闲期挂掉，不能占用本轮运行的一次性结果闸门
        liveCoordinator.publishEvent(resultSession(title, text, timeoutSec = 180, isError = true))
        pushExternal(settings.sendOnServiceDied, "服务异常", "MAA 服务意外终止")
    }

    /** 按对应开关外推 */
    private fun pushExternal(gate: StateFlow<Boolean>, title: String, content: String) {
        if (gate.value) {
            externalService.send(title, content)
        }
    }

    private fun publishResult(
        title: String,
        text: String,
        timeoutSec: Int,
        isError: Boolean = false,
    ) {
        liveCoordinator.publishResult(
            liveCoordinator.currentToken(),
            resultSession(title, text, timeoutSec, isError),
        )
    }

    private fun resultSession(
        title: String,
        text: String,
        timeoutSec: Int,
        isError: Boolean,
    ) = LiveSession(
        sessionId = LiveNotifyIds.RESULT_SESSION,
        category = LiveCategory.RESULT,
        title = title,
        text = text,
        capsuleText = title,
        // 不设 ongoing：Android 13 及以下划不掉，进程被杀后会一直挂着
        ongoing = false,
        firstFloat = true,
        timeoutSec = timeoutSec,
        isError = isError,
    )
}
