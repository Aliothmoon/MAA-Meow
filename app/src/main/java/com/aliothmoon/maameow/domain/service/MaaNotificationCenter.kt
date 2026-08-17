package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.notification.NotificationSettingsManager

/**
 * 聚合系统通知 (MaaEventNotifier) 和外部推送 (ExternalNotificationService)，
 * 为回调 Handler 提供统一的通知 API，避免通知逻辑分散到多个类中。
 */
class MaaNotificationCenter(
    private val eventNotifier: MaaEventNotifier,
    private val externalService: ExternalNotificationService,
    private val settings: NotificationSettingsManager,
) {

    /** 全部任务完成 */
    fun notifyAllTasksCompleted(summary: String) {
        eventNotifier.notifyAllTasksCompleted(summary)
        if (settings.sendOnComplete.value) {
            externalService.sendWithLogs("所有任务已完成", summary)
        }
    }

    /** 任务链出错 */
    fun notifyTaskError(taskName: String) {
        eventNotifier.notifyTaskError(taskName)
        if (settings.sendOnError.value) {
            externalService.send("任务出错", "任务链 $taskName 执行失败")
        }
    }

    /** 子任务失败 */
    fun notifySubTaskFailure(message: String) {
        eventNotifier.notifySubTaskFailure(message)
    }

    /** 需要玩家接手的任务事件（如奇象巡展发现未收录奇象），外推走「完成」开关 */
    fun notifyHandoverRequired(title: String, content: String) {
        eventNotifier.notifyEvent(title, content)
        if (settings.sendOnComplete.value) {
            externalService.send(title, content)
        }
    }

    /** 公招稀有 Tag */
    fun notifyRecruitSpecialTag(tag: String) {
        eventNotifier.notifyRecruitSpecialTag(tag)
    }

    /** 公招小车 Tag */
    fun notifyRecruitRobotTag(tag: String) {
        eventNotifier.notifyRecruitRobotTag(tag)
    }

    /** 公招高星 */
    fun notifyRecruitHighRarity(level: Int) {
        eventNotifier.notifyRecruitHighRarity(level)
    }

    /** MAA 服务意外终止 */
    fun notifyServiceDied() {
        if (settings.sendOnServiceDied.value) {
            externalService.send("服务异常", "MAA 服务意外终止")
        }
    }
}
