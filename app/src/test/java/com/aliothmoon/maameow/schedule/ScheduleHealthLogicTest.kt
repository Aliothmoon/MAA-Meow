package com.aliothmoon.maameow.schedule

import com.aliothmoon.maameow.schedule.model.ScheduleHealthIssue
import com.aliothmoon.maameow.schedule.model.ScheduleHealthLogic
import com.aliothmoon.maameow.schedule.model.ScheduleHealthSnapshot
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleHealthLogicTest {

    private fun snapshot(
        backendGranted: Boolean = true,
        batteryWhitelist: Boolean = true,
        notification: Boolean = true,
        exactAlarmAllowed: Boolean = true,
        overlayGranted: Boolean = true,
        overlayNeeded: Boolean = false,
    ) = ScheduleHealthSnapshot(
        backendGranted = backendGranted,
        batteryWhitelist = batteryWhitelist,
        notification = notification,
        exactAlarmAllowed = exactAlarmAllowed,
        overlayGranted = overlayGranted,
        overlayNeeded = overlayNeeded,
    )

    @Test
    fun `all green - no issues`() {
        assertTrue(ScheduleHealthLogic.failingIssues(snapshot()).isEmpty())
    }

    @Test
    fun `each single failure maps to its own issue`() {
        assertEquals(
            listOf(ScheduleHealthIssue.BACKEND),
            ScheduleHealthLogic.failingIssues(snapshot(backendGranted = false)),
        )
        assertEquals(
            listOf(ScheduleHealthIssue.BATTERY),
            ScheduleHealthLogic.failingIssues(snapshot(batteryWhitelist = false)),
        )
        assertEquals(
            listOf(ScheduleHealthIssue.NOTIFICATION),
            ScheduleHealthLogic.failingIssues(snapshot(notification = false)),
        )
        assertEquals(
            listOf(ScheduleHealthIssue.EXACT_ALARM),
            ScheduleHealthLogic.failingIssues(snapshot(exactAlarmAllowed = false)),
        )
        assertEquals(
            listOf(ScheduleHealthIssue.OVERLAY),
            ScheduleHealthLogic.failingIssues(snapshot(overlayNeeded = true, overlayGranted = false)),
        )
    }

    @Test
    fun `multiple failures - aggregated in stable order`() {
        val issues = ScheduleHealthLogic.failingIssues(
            snapshot(
                backendGranted = false,
                notification = false,
                exactAlarmAllowed = false,
                overlayNeeded = true,
                overlayGranted = false,
            )
        )
        assertEquals(
            listOf(
                ScheduleHealthIssue.BACKEND,
                ScheduleHealthIssue.EXACT_ALARM,
                ScheduleHealthIssue.NOTIFICATION,
                ScheduleHealthIssue.OVERLAY,
            ),
            issues,
        )
    }

    // ===== wizardItems: 保存后向导复用同一判定，仅剔除 BACKEND =====

    @Test
    fun `wizardItems - drops backend but keeps the rest in order`() {
        val snapshot = snapshot(
            backendGranted = false,
            batteryWhitelist = false,
            notification = false,
            exactAlarmAllowed = false,
            overlayNeeded = true,
            overlayGranted = false,
        )
        assertEquals(
            listOf(
                ScheduleHealthIssue.BATTERY,
                ScheduleHealthIssue.EXACT_ALARM,
                ScheduleHealthIssue.NOTIFICATION,
                ScheduleHealthIssue.OVERLAY,
            ),
            ScheduleHealthLogic.wizardItems(snapshot),
        )
    }

    @Test
    fun `wizardItems - backend alone yields no wizard`() {
        assertTrue(ScheduleHealthLogic.wizardItems(snapshot(backendGranted = false)).isEmpty())
    }

    @Test
    fun `overlay granted or not needed - no overlay issue`() {
        // 不需要屏保:悬浮窗未授予也不提示
        assertTrue(
            ScheduleHealthLogic.failingIssues(snapshot(overlayNeeded = false, overlayGranted = false)).isEmpty()
        )
        // 需要但已授予:不提示
        assertTrue(
            ScheduleHealthLogic.failingIssues(snapshot(overlayNeeded = true, overlayGranted = true)).isEmpty()
        )
    }

    // ===== overlayNeeded: 策略推导 =====

    private fun strategy(enabled: Boolean, autoScreenSaver: Boolean) = ScheduleStrategy(
        name = "test",
        profileId = "p",
        enabled = enabled,
        autoScreenSaver = autoScreenSaver,
    )

    @Test
    fun `overlayNeeded - only enabled strategies with screen saver count`() {
        assertTrue(ScheduleHealthLogic.overlayNeeded(listOf(strategy(enabled = true, autoScreenSaver = true))))
        // 禁用的策略勾了屏保不算
        assertFalse(ScheduleHealthLogic.overlayNeeded(listOf(strategy(enabled = false, autoScreenSaver = true))))
        // 启用的策略没勾屏保不算
        assertFalse(ScheduleHealthLogic.overlayNeeded(listOf(strategy(enabled = true, autoScreenSaver = false))))
        assertFalse(ScheduleHealthLogic.overlayNeeded(emptyList()))
    }

    @Test
    fun `overlayNeeded - mixed list picks the enabled one`() {
        val strategies = listOf(
            strategy(enabled = false, autoScreenSaver = true),
            strategy(enabled = true, autoScreenSaver = true),
        )
        assertTrue(ScheduleHealthLogic.overlayNeeded(strategies))
    }
}
