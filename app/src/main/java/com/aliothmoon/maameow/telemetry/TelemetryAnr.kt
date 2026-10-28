package com.aliothmoon.maameow.telemetry

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.hints.AbnormalExit
import io.sentry.util.HintUtils

/**
 * Sentry 的 ANR 事件只带线程栈，主线程空闲时看不出是哪个组件超时
 * 按 ANR 时间戳从 ApplicationExitInfo 找回系统记下的原因补上
 */
internal object TelemetryAnr {

    const val KIND_TAG = "anr.kind"
    const val DESCRIPTION_EXTRA = "anr.description"
    private const val MAX_DESCRIPTION_LENGTH = 500

    fun enrich(context: Context, event: SentryEvent, hint: Hint) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val exit = HintUtils.getSentrySdkHint(hint) as? AbnormalExit ?: return
        if (exit.mechanism()?.startsWith("anr") != true) return
        val timestamp = exit.timestamp() ?: return
        val description = context.getSystemService(ActivityManager::class.java)
            ?.getHistoricalProcessExitReasons(context.packageName, 0, 0)
            ?.firstOrNull { it.reason == ApplicationExitInfo.REASON_ANR && it.timestamp == timestamp }
            ?.description
        event.setTag(KIND_TAG, kindOf(description))
        description?.let { event.setExtra(DESCRIPTION_EXTRA, it.take(MAX_DESCRIPTION_LENGTH)) }
    }

    /** 文案取自 AOSP 各超时点；用户在 ANR 弹窗点关闭时前面会多一段 `user request after error:` */
    fun kindOf(description: String?): String = when {
        description.isNullOrBlank() -> "unknown"
        "Input dispatching timed out" in description -> "input"
        "did not then call Service.startForeground" in description -> "fgs_start"
        "executing service" in description -> "service"
        "Broadcast of" in description -> "broadcast"
        "ContentProvider not responding" in description -> "provider"
        else -> "other"
    }
}
