package com.aliothmoon.maameow.domain.service

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.domain.models.NotificationImage
import com.aliothmoon.maameow.domain.notification.LiveAction
import com.aliothmoon.maameow.domain.notification.LiveCategory
import com.aliothmoon.maameow.domain.notification.LiveNotifyIds
import com.aliothmoon.maameow.domain.notification.LiveSession
import com.aliothmoon.maameow.domain.notification.LiveSessionCoordinator
import com.aliothmoon.maameow.domain.service.MaaCompositionService.StopOrigin
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.resolve
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/** 聚合实况结果、队列卡片和外推 */
class MaaNotificationCenter(
    context: Context,
    private val eventNotifier: MaaEventNotifier,
    private val externalService: ExternalNotificationService,
    private val settings: NotificationSettingsManager,
    private val liveCoordinator: LiveSessionCoordinator,
    private val frameSnapshotter: FrameSnapshotter,
) {
    private val appContext = context.applicationContext

    /** 同步跨进程截图，没开或没有能发图的渠道时不截 */
    fun captureCompletionScreenshot(): NotificationImage? {
        val wanted = settings.sendOnComplete.value &&
                settings.attachScreenshot.value &&
                externalService.imageChannelEnabled.value
        return if (wanted) frameSnapshotter.captureJpeg()?.let(::NotificationImage) else null
    }

    fun notifyAllTasksCompleted(summary: String, screenshot: NotificationImage? = null) {
        val title = appContext.getString(R.string.notification_event_all_tasks_completed)
        publishResult(title, summary, timeoutSec = 120)
        if (settings.sendOnComplete.value) {
            externalService.sendWithLogs("所有任务已完成", summary, screenshot)
        }
    }

    /** 掉线 / 游戏退出与到达时长上限都走停止流程，不能都报成手动停止 */
    fun notifyTaskStopped(origin: StopOrigin) {
        val title = appContext.getString(R.string.notification_event_task_stopped)
        val text = appContext.getString(
            when (origin) {
                StopOrigin.USER -> R.string.notification_event_task_stopped_text
                StopOrigin.CALLBACK -> R.string.notification_event_task_aborted_text
                StopOrigin.RUN_DURATION_LIMIT -> R.string.notification_event_task_time_limit_text
            }
        )
        // 非手动停止多半没人看着，留久一点
        val timeoutSec = if (origin == StopOrigin.USER) 15 else 120
        publishResult(title, text, timeoutSec)
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

    /** 不设超时，Shizuku 回来时撤 */
    fun notifyShizukuDown(afterBoot: Boolean) {
        val title = appContext.getString(R.string.notification_shizuku_down_title)
        val text = appContext.getString(
            if (afterBoot) R.string.notification_shizuku_down_after_boot
            else R.string.notification_shizuku_down_stopped
        )
        liveCoordinator.publishStandalone(
            resultSession(title, text, timeoutSec = null, isError = true, sessionId = LiveNotifyIds.SHIZUKU_DOWN_SESSION)
                .copy(actions = listOf(LiveAction.OpenShizuku))
        )
    }

    fun withdrawShizukuDown() {
        liveCoordinator.withdrawStandalone(LiveNotifyIds.SHIZUKU_DOWN_SESSION)
    }

    /**
     * 定时 / 外部触发没跑起来，多半没人看着界面，只能靠通知
     * 不设超时，免得凌晨失败、早上已经消失；跳过量大且多在预期内，不外推
     * [replacesStartFailure]：启动阶段已发过不带策略名的「任务出错」，撤掉只留这条
     */
    fun notifyLaunchNotStarted(
        name: String,
        result: ExecutionResult,
        reason: UiText?,
        replacesStartFailure: Boolean = false,
    ) = try {
        publishLaunchNotStarted(name, result, reason, replacesStartFailure)
    } catch (e: Exception) {
        // 调用方多在收尾路径上，通知失败不能把流程带崩
        Timber.w(e, "Launch notification failed: %s", name)
    }

    private fun publishLaunchNotStarted(
        name: String,
        result: ExecutionResult,
        reason: UiText?,
        replacesStartFailure: Boolean,
    ) {
        if (replacesStartFailure) {
            liveCoordinator.withdrawResult()
        }
        val skipped = result.isSkipped
        val title = appContext.getString(
            if (skipped) R.string.notification_schedule_skipped else R.string.notification_schedule_failed
        )
        val detail = reason?.resolve(appContext)
            ?: appContext.getString(R.string.schedule_result_failed_start)
        val text = appContext.getString(R.string.notification_schedule_detail, name, detail)
        liveCoordinator.publishStandalone(
            resultSession(title, text, timeoutSec = null, isError = !skipped, sessionId = LiveNotifyIds.LAUNCH_SESSION)
        )
        if (!skipped) {
            pushExternal(settings.sendOnError, title, text)
        }
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
        timeoutSec: Int?,
        isError: Boolean,
        sessionId: String = LiveNotifyIds.RESULT_SESSION,
    ) = LiveSession(
        sessionId = sessionId,
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
