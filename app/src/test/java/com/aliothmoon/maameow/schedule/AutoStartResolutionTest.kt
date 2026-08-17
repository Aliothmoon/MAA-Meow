package com.aliothmoon.maameow.schedule

import com.aliothmoon.maameow.schedule.service.AutoStartResolution
import com.aliothmoon.maameow.schedule.service.AutoStartTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoStartResolutionTest {

    // ===== select: 厂商 Intent 兜底链解析 =====

    @Test
    fun `oem page resolvable - prefer first resolvable in candidate order`() {
        val target = AutoStartResolution.select(
            resolvableOemIds = listOf("vivo", "huawei"),
            knownRestrictiveManufacturer = false,
        )
        assertEquals(AutoStartTarget.Oem("vivo"), target)
    }

    @Test
    fun `known manufacturer but nothing resolvable - fallback to app details`() {
        val target = AutoStartResolution.select(
            resolvableOemIds = emptyList(),
            knownRestrictiveManufacturer = true,
        )
        assertEquals(AutoStartTarget.AppDetails, target)
    }

    @Test
    fun `unknown manufacturer and nothing resolvable - no guidance`() {
        assertNull(
            AutoStartResolution.select(
                resolvableOemIds = emptyList(),
                knownRestrictiveManufacturer = false,
            )
        )
    }

    @Test
    fun `unknown manufacturer with resolvable oem page - still guide`() {
        val target = AutoStartResolution.select(
            resolvableOemIds = listOf("xiaomi"),
            knownRestrictiveManufacturer = false,
        )
        assertEquals(AutoStartTarget.Oem("xiaomi"), target)
    }

    // ===== shouldRemindByBootId: BOOT_COUNT 语义 =====

    @Test
    fun `bootId - first run reminds`() {
        assertTrue(AutoStartResolution.shouldRemindByBootId("boot-1", null))
    }

    @Test
    fun `bootId - same boot cycle does not remind again`() {
        assertFalse(AutoStartResolution.shouldRemindByBootId("boot-1", "boot-1"))
    }

    @Test
    fun `bootId - new boot cycle reminds once`() {
        assertTrue(AutoStartResolution.shouldRemindByBootId("boot-2", "boot-1"))
    }

    @Test
    fun `bootId - unavailable id never reminds`() {
        assertFalse(AutoStartResolution.shouldRemindByBootId(null, null))
        assertFalse(AutoStartResolution.shouldRemindByBootId(null, "boot-1"))
    }

    // ===== shouldRemindByUptime: elapsedRealtime 语义(读不到 BOOT_COUNT 时兜底) =====

    @Test
    fun `uptime - first run reminds`() {
        assertTrue(AutoStartResolution.shouldRemindByUptime(currentUptimeMs = 100, lastRemindedUptimeMs = null))
    }

    @Test
    fun `uptime - same boot (monotonic increase) does not remind`() {
        assertFalse(AutoStartResolution.shouldRemindByUptime(currentUptimeMs = 5000, lastRemindedUptimeMs = 1000))
        assertFalse(AutoStartResolution.shouldRemindByUptime(currentUptimeMs = 1000, lastRemindedUptimeMs = 1000))
    }

    @Test
    fun `uptime - reboot (value falls back) reminds`() {
        assertTrue(AutoStartResolution.shouldRemindByUptime(currentUptimeMs = 50, lastRemindedUptimeMs = 900000))
    }

    // ===== shouldRemind: 适配层统一入口(token 不可用回退 uptime) =====

    @Test
    fun `combined - token available wins over uptime`() {
        // token 相同轮已提醒(uptime 也显示提醒过):不提醒
        assertFalse(
            AutoStartResolution.shouldRemind(
                neverRemind = false,
                currentBootToken = "5",
                lastRemindedBootToken = "5",
                currentUptimeMs = 100,
                lastRemindedUptimeMs = 50,
            )
        )
        // token 新周期(uptime 相同轮):提醒
        assertTrue(
            AutoStartResolution.shouldRemind(
                neverRemind = false,
                currentBootToken = "6",
                lastRemindedBootToken = "5",
                currentUptimeMs = 100,
                lastRemindedUptimeMs = 50,
            )
        )
    }

    @Test
    fun `combined - token unavailable falls back to uptime semantics`() {
        // token 读不到时,即使 token 状态"看似已提醒过"(null == null),也按 uptime 决策:
        // uptime 显示同轮已提醒 → 不提醒
        assertFalse(
            AutoStartResolution.shouldRemind(
                neverRemind = false,
                currentBootToken = null,
                lastRemindedBootToken = null,
                currentUptimeMs = 5000,
                lastRemindedUptimeMs = 1000,
            )
        )
        // uptime 显示重启过 → 提醒
        assertTrue(
            AutoStartResolution.shouldRemind(
                neverRemind = false,
                currentBootToken = null,
                lastRemindedBootToken = "5",
                currentUptimeMs = 50,
                lastRemindedUptimeMs = 900000,
            )
        )
        // uptime 从未提醒过 → 提醒
        assertTrue(
            AutoStartResolution.shouldRemind(
                neverRemind = false,
                currentBootToken = null,
                lastRemindedBootToken = null,
                currentUptimeMs = 100,
                lastRemindedUptimeMs = null,
            )
        )
    }

    @Test
    fun `combined - never remind overrides both semantics`() {
        // 用户选过「不再提醒」：新启动周期也不提醒
        assertFalse(
            AutoStartResolution.shouldRemind(
                neverRemind = true,
                currentBootToken = "6",
                lastRemindedBootToken = "5",
                currentUptimeMs = 100,
                lastRemindedUptimeMs = 50,
            )
        )
        // token 读不到时同样一票否决
        assertFalse(
            AutoStartResolution.shouldRemind(
                neverRemind = true,
                currentBootToken = null,
                lastRemindedBootToken = null,
                currentUptimeMs = 100,
                lastRemindedUptimeMs = null,
            )
        )
    }
}
