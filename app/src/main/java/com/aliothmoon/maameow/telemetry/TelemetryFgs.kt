package com.aliothmoon.maameow.telemetry

import com.aliothmoon.maameow.domain.service.TaskExecutionService
import io.sentry.SentryEvent

/** 前台服务没按时 startForeground 的崩溃：补上服务各节点距崩溃多久，分清是没进服务还是进了没挂 */
internal object TelemetryFgs {

    // 12+ 是 ForegroundServiceDidNotStartInTimeException，9–11 是 RemoteServiceException，信息相同
    private const val DID_NOT_START = "did not then call Service.startForeground"

    // 信息里的 ServiceRecord 带组件名
    private const val TASK_SERVICE = "TaskExecutionService"

    fun enrich(event: SentryEvent) {
        if (event.exceptions?.any { isTaskServiceTimeout(it.value) } != true) return
        TaskExecutionService.fgsTimeline().forEach { (key, value) -> event.setExtra("fgs.$key", value) }
    }

    /** 定时服务也走 startForegroundService，时间线只属于任务服务 */
    internal fun isTaskServiceTimeout(message: String?): Boolean =
        message != null && DID_NOT_START in message && TASK_SERVICE in message
}
