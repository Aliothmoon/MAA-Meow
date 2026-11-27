package com.aliothmoon.maameow.domain.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RunWakeLockTest {

    private class FakeOps : RunWakeLock.WakeLockOps {
        val acquiredSlices = mutableListOf<Long>()
        var releaseCount = 0
        var failAcquire = false

        override fun acquire(timeoutMs: Long) {
            if (failAcquire) throw SecurityException("denied")
            acquiredSlices += timeoutMs
        }

        override fun release() {
            releaseCount++
        }
    }

    private fun TestScope.wakeLock(
        ops: FakeOps,
        sliceMs: Long = 30_000L,
        renewIntervalMs: Long = 20_000L,
        maxHoldMs: Long = 65_000L,
    ) = RunWakeLock(
        ops = ops,
        scope = backgroundScope,
        clock = { testScheduler.currentTime },
        sliceMs = sliceMs,
        renewIntervalMs = renewIntervalMs,
        maxHoldMs = maxHoldMs,
    )

    @Test
    fun acquireRenewsPeriodicallyAndReleasesAtMaxHoldCeiling() = runTest {
        val ops = FakeOps()
        val wl = wakeLock(ops, sliceMs = 30_000L, renewIntervalMs = 20_000L, maxHoldMs = 65_000L)

        wl.acquire()
        assertEquals(listOf(30_000L), ops.acquiredSlices)
        assertEquals(0, ops.releaseCount)

        advanceTimeBy(20_000L)
        runCurrent()
        assertEquals(listOf(30_000L, 30_000L), ops.acquiredSlices)

        advanceTimeBy(20_000L)
        runCurrent()
        assertEquals(listOf(30_000L, 30_000L, 25_000L), ops.acquiredSlices)

        advanceTimeBy(20_000L)
        runCurrent()
        assertEquals(listOf(30_000L, 30_000L, 25_000L, 5_000L), ops.acquiredSlices)

        advanceTimeBy(20_000L)
        runCurrent()
        assertEquals(1, ops.releaseCount)
        assertEquals(4, ops.acquiredSlices.size)

        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(1, ops.releaseCount)
        assertEquals(4, ops.acquiredSlices.size)
    }

    @Test
    fun releaseStopsRenewingAndIsIdempotent() = runTest {
        val ops = FakeOps()
        val wl = wakeLock(ops)

        wl.acquire()
        advanceTimeBy(10_000L)
        wl.release()
        wl.release()

        assertEquals(1, ops.releaseCount)
        advanceTimeBy(100_000L)
        runCurrent()
        assertEquals(1, ops.acquiredSlices.size)
        assertEquals(1, ops.releaseCount)
    }

    @Test
    fun reacquireResetsCeilingAndDoesNotLeakOldJob() = runTest {
        val ops = FakeOps()
        val wl = wakeLock(ops, sliceMs = 30_000L, renewIntervalMs = 20_000L, maxHoldMs = 45_000L)

        wl.acquire()
        advanceTimeBy(15_000L)
        wl.acquire()
        assertEquals(2, ops.acquiredSlices.size)

        advanceTimeBy(20_000L)
        runCurrent()
        assertEquals(3, ops.acquiredSlices.size)
        assertEquals(0, ops.releaseCount)

        wl.release()
        assertEquals(1, ops.releaseCount)
    }

    @Test
    fun opsFailureIsSwallowed() = runTest {
        val ops = FakeOps().apply { failAcquire = true }
        val wl = wakeLock(ops)

        wl.acquire()
        wl.release()
        assertEquals(1, ops.releaseCount)
    }
}
