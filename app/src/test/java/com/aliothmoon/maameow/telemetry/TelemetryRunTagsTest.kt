package com.aliothmoon.maameow.telemetry

import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryRunTagsTest {

    @Test
    fun `Shizuku 身份按可用、授权、uid 依次判`() {
        assertEquals("unavailable", TelemetryRunTags.shizukuIdentity(available = false, granted = false, uid = null))
        assertEquals("not_granted", TelemetryRunTags.shizukuIdentity(available = true, granted = false, uid = 2000))
        assertEquals("unknown", TelemetryRunTags.shizukuIdentity(available = true, granted = true, uid = null))
        assertEquals("root", TelemetryRunTags.shizukuIdentity(available = true, granted = true, uid = 0))
        assertEquals("adb", TelemetryRunTags.shizukuIdentity(available = true, granted = true, uid = 2000))
    }

    @Test
    fun `Shizuku binder 按本进程收到与断开的先后判`() {
        assertEquals("never_received", TelemetryShizuku.binderState(receivedAt = null, deadAt = null))
        assertEquals("alive", TelemetryShizuku.binderState(receivedAt = 100, deadAt = null))
        assertEquals("dead", TelemetryShizuku.binderState(receivedAt = 100, deadAt = 200))
        // 断过又回来了
        assertEquals("alive", TelemetryShizuku.binderState(receivedAt = 300, deadAt = 200))
    }
}
