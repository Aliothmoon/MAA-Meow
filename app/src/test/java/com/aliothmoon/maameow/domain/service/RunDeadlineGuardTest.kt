package com.aliothmoon.maameow.domain.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunDeadlineGuardTest {

    private val minute = 60_000L

    /** 虚拟时间 + 可手动跳变的偏移，模拟深睡期间 elapsedRealtime 前进而协程未被调度 */
    private var clockOffset = 0L

    private fun TestScope.guard() = RunDeadlineGuard(
        scope = backgroundScope,
        clock = { testScheduler.currentTime + clockOffset },
    )

    @Test
    fun firesOnceAtDeadline() = runTest {
        val guard = guard()
        var fired = 0
        guard.arm(1) { fired++ }
        runCurrent()
        assertEquals(testScheduler.currentTime + minute, guard.deadline.value)

        advanceTimeBy(minute - 1)
        runCurrent()
        assertEquals(0, fired)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, fired)
        assertNull(guard.deadline.value)

        advanceTimeBy(10 * minute)
        runCurrent()
        assertEquals(1, fired)
    }

    @Test
    fun disarmPreventsFiring() = runTest {
        val guard = guard()
        var fired = 0
        guard.arm(1) { fired++ }
        advanceTimeBy(30_000)
        guard.disarm()
        assertNull(guard.deadline.value)

        advanceTimeBy(5 * minute)
        runCurrent()
        assertEquals(0, fired)
    }

    @Test
    fun rearmReplacesPreviousDeadline() = runTest {
        val guard = guard()
        val fired = mutableListOf<Int>()
        guard.arm(1) { fired += 1 }
        advanceTimeBy(30_000)
        guard.arm(2) { fired += 2 }

        advanceTimeBy(minute)
        runCurrent()
        assertTrue(fired.isEmpty())

        advanceTimeBy(minute)
        runCurrent()
        assertEquals(listOf(2), fired)
    }

    @Test
    fun disarmInsideCallbackDoesNotCancelIt() = runTest {
        // 回调里的 stop 会走到 finishStop → disarm，不能把正在执行的停止打断
        val guard = guard()
        var completed = false
        guard.arm(1) {
            guard.disarm()
            delay(1_000)
            completed = true
        }
        advanceTimeBy(minute + 2_000)
        runCurrent()
        assertTrue(completed)
    }

    @Test
    fun clockJumpIsCaughtOnNextTick() = runTest {
        val guard = guard()
        var fired = 0
        guard.arm(30) { fired++ }
        runCurrent()

        clockOffset = 30 * minute
        advanceTimeBy(minute + 1)
        runCurrent()
        assertEquals(1, fired)
    }
}
