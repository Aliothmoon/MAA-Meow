package com.aliothmoon.maameow.remote.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityUtilsTest {

    @Test
    fun startResultSuccessRange_matchesAospContract() {
        // START_SUCCESS(0) .. START_DELIVERED_TO_TOP(3) 与 0..99 区间为成功
        assertTrue(ActivityUtils.isStartResultSuccessful(0))
        assertTrue(ActivityUtils.isStartResultSuccessful(1))
        assertTrue(ActivityUtils.isStartResultSuccessful(2))
        assertTrue(ActivityUtils.isStartResultSuccessful(3))
        assertTrue(ActivityUtils.isStartResultSuccessful(99))

        // 负数错误码以及 100 (START_SWITCHES_CANCELED) / 102 (START_ABORTED) 均为失败
        assertFalse(ActivityUtils.isStartResultSuccessful(-1))
        assertFalse(ActivityUtils.isStartResultSuccessful(-94))
        assertFalse(ActivityUtils.isStartResultSuccessful(-96))
        assertFalse(ActivityUtils.isStartResultSuccessful(100))
        assertFalse(ActivityUtils.isStartResultSuccessful(101))
        assertFalse(ActivityUtils.isStartResultSuccessful(102))
    }

    @Test
    fun amStartOutput_normalAndTopDelivery_notFailure() {
        assertFalse(
            ActivityUtils.isAmStartOutputFailure(
                stdout = "Starting: Intent { act=com.aliothmoon.maameow.ACTION_SHOW_SCHEDULE_EXECUTION cmp=com.aliothmoon.maameow/.MainActivity }",
                stderr = "",
            )
        )
        assertFalse(
            ActivityUtils.isAmStartOutputFailure(
                stdout = "Starting: Intent { cmp=com.aliothmoon.maameow/.MainActivity }",
                stderr = "Warning: Activity not started, intent has been delivered to currently running top-most instance.",
            )
        )
        assertFalse(
            ActivityUtils.isAmStartOutputFailure(
                stdout = "Starting: Intent { cmp=com.aliothmoon.maameow/.MainActivity }",
                stderr = "Warning: Activity not started, its current task has been brought to the front.",
            )
        )
        // Starting 行里的 Intent 内容不应误触发 exception 匹配
        assertFalse(
            ActivityUtils.isAmStartOutputFailure(
                stdout = "Starting: Intent { act=com.example.EXCEPTION_TEST cmp=com.aliothmoon.maameow/.MainActivity }",
                stderr = "",
            )
        )
    }

    @Test
    fun amStartOutput_abortedCanceledOrError_isFailure() {
        assertTrue(
            ActivityUtils.isAmStartOutputFailure(
                stdout = "Starting: Intent { cmp=com.aliothmoon.maameow/.MainActivity }",
                stderr = "Warning: Activity not started because the current activity is being kept for the user.",
            )
        )
        assertTrue(
            ActivityUtils.isAmStartOutputFailure(
                stdout = "Starting: Intent { cmp=com.aliothmoon.maameow/.MainActivity }",
                stderr = "Error: Activity not started, unknown error code 102",
            )
        )
        assertTrue(
            ActivityUtils.isAmStartOutputFailure(
                stdout = "",
                stderr = "Error type 3\nError: Activity class {com.aliothmoon.maameow/.MainActivity} does not exist.",
            )
        )
        assertTrue(
            ActivityUtils.isAmStartOutputFailure(
                stdout = "",
                stderr = "java.lang.SecurityException: Permission Denial: starting Intent",
            )
        )
    }

    @Test
    fun confirmedOnFirstAttempt_succeedsWithoutRetry() {
        var now = 0L
        var starts = 0
        var polls = 0

        val ok = ActivityUtils.launchAndConfirm(
            targetPackage = OWN_PKG,
            startActivity = { starts++; true },
            visiblePackages = {
                polls++
                if (polls == 1) listOf("com.miui.home") else listOf(OWN_PKG)
            },
            clockMs = { now },
            sleepMs = { now += it },
        )

        assertTrue(ok)
        assertEquals(1, starts)
        assertEquals(150L, now)
    }

    @Test
    fun stuckOnDesktopFirstAttempt_retriesAndSucceeds() {
        var now = 0L
        var starts = 0
        val warns = mutableListOf<String>()

        val ok = ActivityUtils.launchAndConfirm(
            targetPackage = OWN_PKG,
            startActivity = { starts++; true },
            visiblePackages = {
                if (starts == 1) listOf("com.miui.home") else listOf(OWN_PKG)
            },
            onWarn = { warns += it },
            clockMs = { now },
            sleepMs = { now += it },
        )

        assertTrue(ok)
        assertEquals(2, starts)
        assertTrue(warns.any { it.contains("com.miui.home") })
    }

    @Test
    fun stuckOnDesktopAllAttempts_returnsFalse() {
        var now = 0L
        var starts = 0

        val ok = ActivityUtils.launchAndConfirm(
            targetPackage = OWN_PKG,
            startActivity = { starts++; true },
            visiblePackages = { listOf("com.miui.home") },
            clockMs = { now },
            sleepMs = { now += it },
        )

        assertFalse(ok)
        assertEquals(3, starts)
    }

    @Test
    fun unreadableOrEmptyForeground_acceptsLaunchLeniently() {
        var now = 0L
        assertTrue(
            ActivityUtils.launchAndConfirm(
                targetPackage = OWN_PKG,
                startActivity = { true },
                visiblePackages = { null },
                clockMs = { now },
                sleepMs = { now += it },
            )
        )
        assertTrue(
            ActivityUtils.launchAndConfirm(
                targetPackage = OWN_PKG,
                startActivity = { true },
                visiblePackages = { emptyList() },
                clockMs = { now },
                sleepMs = { now += it },
            )
        )
    }

    @Test
    fun startCallReturnsFalseFirst_retriesAndSucceeds() {
        var now = 0L
        var starts = 0

        val ok = ActivityUtils.launchAndConfirm(
            targetPackage = OWN_PKG,
            startActivity = { starts++; starts >= 2 },
            visiblePackages = { if (starts < 2) listOf("com.miui.home") else listOf(OWN_PKG) },
            clockMs = { now },
            sleepMs = { now += it },
        )

        assertTrue(ok)
        assertEquals(2, starts)
    }

    private companion object {
        const val OWN_PKG = "com.aliothmoon.maameow"
    }
}
