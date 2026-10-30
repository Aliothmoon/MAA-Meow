package com.aliothmoon.maameow.telemetry

import android.content.Context
import android.os.SystemClock
import com.aliothmoon.maameow.constant.OFFICIAL_SHIZUKU_PACKAGE
import com.aliothmoon.maameow.manager.ShizukuManager
import io.sentry.SentryEvent

/** Shizuku 在本进程里的来去：分清是开机后没起、中途停了还是没装 */
internal object TelemetryShizuku {

    fun tags(context: Context): Map<String, String> = buildMap {
        put("shizuku.binder", binderState(ShizukuManager.binderReceivedAt, ShizukuManager.binderDeadAt))
        put("shizuku.installed", installState(context))
        ShizukuManager.lastServerUid?.let { put("shizuku.last_identity", if (it == 0) "root" else "adb") }
    }

    fun extras(): Map<String, Long> = buildMap {
        val now = SystemClock.elapsedRealtime()
        val received = ShizukuManager.binderReceivedAt
        val dead = ShizukuManager.binderDeadAt
        put("device.uptime_s", now / 1000)
        received?.let { put("shizuku.received_ago_s", (now - it) / 1000) }
        dead?.let { put("shizuku.dead_ago_s", (now - it) / 1000) }
    }

    /** 服务死亡取完证才补，Shizuku 若跟着没了这时已收到死讯：分清是 adb 那头断了还是服务单独被杀 */
    fun enrich(event: SentryEvent, context: Context) {
        tags(context).forEach { (key, value) -> event.setTag(key, value) }
        extras().forEach { (key, value) -> event.setExtra(key, value) }
    }

    /** never_received：本进程从没拿到过 binder，多半开机后就没起 */
    internal fun binderState(receivedAt: Long?, deadAt: Long?): String = when {
        receivedAt == null -> "never_received"
        deadAt != null && deadAt >= receivedAt -> "dead"
        else -> "alive"
    }

    private fun installState(context: Context): String = when {
        ShizukuManager.isSui -> "sui"
        runCatching { context.packageManager.getPackageInfo(OFFICIAL_SHIZUKU_PACKAGE, 0) }.isSuccess -> "official"
        else -> "none"
    }
}
