package com.aliothmoon.maameow.remote.internal

import android.view.Display
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeKeyguardSupportTest {

    @Test
    fun awaitBouncerReady_waitsFullSettleWhenLocked() {
        var now = 0L
        val retries = mutableListOf<Pair<DismissEvent, Boolean>>()
        val outcome = awaitBouncerReady(
            initialSettleMs = 2_000L,
            maxTotalMs = 5_000L,
            pollIntervalMs = 100L,
            clockMs = { now },
            sleep = { now += it },
            isKeyguardLocked = { true },
            consumeRetryableDismiss = { null },
            retryDismiss = { true },
            onRetry = { e, ok -> retries += e to ok },
        )
        assertEquals(SettleOutcome.READY_TO_INJECT, outcome)
        assertEquals(2_000L, now)
        assertEquals(0, retries.size)
    }

    @Test
    fun awaitBouncerReady_returnsImmediatelyWhenUnlockedDuringSettle() {
        var now = 0L
        val outcome = awaitBouncerReady(
            initialSettleMs = 2_000L,
            maxTotalMs = 5_000L,
            pollIntervalMs = 100L,
            clockMs = { now },
            sleep = { now += it },
            isKeyguardLocked = { now < 400L },
            consumeRetryableDismiss = { null },
            retryDismiss = { true },
            onRetry = { _, _ -> },
        )
        assertEquals(SettleOutcome.ALREADY_UNLOCKED, outcome)
        assertEquals(400L, now)
    }

    @Test
    fun awaitBouncerReady_retriesDismissOnceOnCancelledAndExtendsSettle() {
        var now = 0L
        var pendingEvent: DismissEvent? = null
        val retries = mutableListOf<Pair<DismissEvent, Boolean>>()
        val outcome = awaitBouncerReady(
            initialSettleMs = 1_200L,
            maxTotalMs = 5_000L,
            pollIntervalMs = 100L,
            redismissDelayMs = 300L,
            retrySettleMs = 1_200L,
            clockMs = { now },
            sleep = {
                now += it
                if (now == 500L) pendingEvent = DismissEvent.CANCELLED
                if (now == 1_000L) pendingEvent = DismissEvent.ERROR
            },
            isKeyguardLocked = { true },
            consumeRetryableDismiss = { pendingEvent.also { pendingEvent = null } },
            retryDismiss = { true },
            onRetry = { e, ok -> retries += e to ok },
        )
        assertEquals(SettleOutcome.READY_TO_INJECT, outcome)
        assertEquals(listOf(DismissEvent.CANCELLED to true), retries)
        // 500ms 触发取消 + 300ms 缓冲 + 1200ms 补等 = 2000ms
        assertEquals(2_000L, now)
    }

    @Test
    fun awaitBouncerReady_capsTotalTimeAtMaxTotalMs() {
        var now = 0L
        var fired = false
        val outcome = awaitBouncerReady(
            initialSettleMs = 4_500L,
            maxTotalMs = 5_000L,
            pollIntervalMs = 100L,
            redismissDelayMs = 300L,
            retrySettleMs = 1_200L,
            clockMs = { now },
            sleep = { now += it },
            isKeyguardLocked = { true },
            consumeRetryableDismiss = {
                if (!fired && now >= 4_200L) {
                    fired = true
                    DismissEvent.ERROR
                } else {
                    null
                }
            },
            retryDismiss = { true },
            onRetry = { _, _ -> },
        )
        assertEquals(SettleOutcome.READY_TO_INJECT, outcome)
        assertEquals(5_000L, now)
    }

    @Test
    fun awaitBouncerReady_returnsUnsupportedWhenKeyguardStateUnavailable() {
        var now = 0L
        val outcome = awaitBouncerReady(
            initialSettleMs = 2_000L,
            maxTotalMs = 5_000L,
            clockMs = { now },
            sleep = { now += it },
            isKeyguardLocked = { null },
            consumeRetryableDismiss = { null },
            retryDismiss = { true },
            onRetry = { _, _ -> },
        )
        assertEquals(SettleOutcome.UNSUPPORTED, outcome)
        assertEquals(0L, now)
    }

    @Test
    fun parseWindowFocusSummary_extractsFocusAndPolicyWithoutLeakingTitle() {
        val policy = """
            WINDOW MANAGER POLICY STATE (dumpsys window policy)
              mAwake=true mScreenOnEarly=true mScreenOnFully=true
              mKeyguardDrawComplete=true mWindowManagerDrawComplete=true
              screenState=SCREEN_STATE_ON
              isStatusBarKeyguard=true
              mShowingDream=false mKeyguardOccluded=false
        """.trimIndent()
        val displays = """
            WINDOW MANAGER DISPLAY CONTENTS (dumpsys window displays)
              Display: mDisplayId=0 (organized)
              mCurrentFocus=Window{3a9f10c u0 NotificationShade 敏感标题不应该出现}
              mFocusedApp=ActivityRecord{123 u0 com.aliothmoon.maameow/.MainActivity}
        """.trimIndent()

        val summary = parseWindowFocusSummary(policy, displays)
        assertEquals(
            "focus=NotificationShade, mAwake=true, mScreenOnFully=true, screenState=SCREEN_STATE_ON, isStatusBarKeyguard=true, mShowingDream=false, mKeyguardOccluded=false",
            summary,
        )
    }

    @Test
    fun parseWindowFocusSummary_handlesNullFocusAndComponentWindow() {
        val displays = "  mCurrentFocus=Window{abcd123 u0 com.miui.aod/com.miui.aod.AntiMistouch}"
        assertEquals(
            "focus=com.miui.aod/com.miui.aod.AntiMistouch",
            parseWindowFocusSummary("", displays),
        )
        assertEquals("focus=null", parseWindowFocusSummary("", "  mCurrentFocus=null"))
        assertNull(parseWindowFocusSummary("", ""))
    }

    @Test
    fun displayStateName_mapsKnownAndUnknownStates() {
        assertEquals("UNKNOWN", displayStateName(Display.STATE_UNKNOWN))
        assertEquals("OFF", displayStateName(Display.STATE_OFF))
        assertEquals("ON", displayStateName(Display.STATE_ON))
        assertEquals("DOZE", displayStateName(Display.STATE_DOZE))
        assertEquals("DOZE_SUSPEND", displayStateName(Display.STATE_DOZE_SUSPEND))
        assertEquals("STATE_99", displayStateName(99))
    }
}
