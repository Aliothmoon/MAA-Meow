package com.aliothmoon.maameow.telemetry

import io.sentry.SentryBaseEvent
import io.sentry.SentryEvent

/** 前台页面的路由模板挂在 Scope 上，只留给崩溃与 ANR，其余事件发出前摘掉 */
internal object TelemetryRoute {
    const val TAG = "ui.route"

    private const val ANR_TYPE = "ApplicationNotResponding"

    fun filter(event: SentryEvent) {
        if (!event.isCrashed && event.exceptions.orEmpty().none { it.type == ANR_TYPE }) strip(event)
    }

    fun strip(event: SentryBaseEvent) = event.removeTag(TAG)
}
