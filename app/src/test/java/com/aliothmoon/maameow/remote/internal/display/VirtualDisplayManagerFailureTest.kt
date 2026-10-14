package com.aliothmoon.maameow.remote.internal.display

import android.util.Log
import com.aliothmoon.maameow.bridge.NativeBridgeLib
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class VirtualDisplayManagerFailureTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any<String>(), any()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        assumeFalse("JVM test expects the Android native bridge to be unavailable", NativeBridgeLib.LOADED)
    }

    @After
    fun tearDown() {
        VirtualDisplayManager.stop()
        unmockkStatic(Log::class)
    }

    @Test
    fun unsatisfiedLinkError_resetsStateAndAllowsRetry() {
        assertEquals(VirtualDisplayManager.DISPLAY_NONE, VirtualDisplayManager.start())
        assertEquals(VirtualDisplayManager.DISPLAY_NONE, VirtualDisplayManager.start())
    }

    @Test
    fun start_waitsForStoppingLifecycleBeforeRetrying() {
        val lifecycleState = lifecycleState()
        lifecycleState.set(3) // STATE_STOPPING

        val result = AtomicInteger(Int.MIN_VALUE)
        val worker = Thread {
            result.set(VirtualDisplayManager.start())
        }.apply { start() }

        assertTrue(
            "start should wait while cleanup owns STATE_STOPPING",
            waitUntil { worker.state == Thread.State.TIMED_WAITING },
        )
        assertTrue(worker.isAlive)

        lifecycleState.set(0) // STATE_IDLE
        VirtualDisplayManager::class.java
            .getDeclaredMethod("signalLifecycleChange")
            .apply { isAccessible = true }
            .invoke(VirtualDisplayManager)

        worker.join(5_000)
        assertFalse("start should retry after cleanup reaches IDLE", worker.isAlive)
        assertEquals(VirtualDisplayManager.DISPLAY_NONE, result.get())
    }

    @Test
    fun finishStart_doesNotRetryAfterOwnerDeadline() {
        val lifecycleState = lifecycleState()
        lifecycleState.set(1) // STATE_STARTING

        val result = VirtualDisplayManager::class.java
            .getDeclaredMethod("finishStart", java.lang.Long.TYPE)
            .apply { isAccessible = true }
            .invoke(VirtualDisplayManager, System.nanoTime() - 1L) as Int

        assertEquals(VirtualDisplayManager.DISPLAY_NONE, result)
        assertEquals("expired owner must finish its lifecycle", 0, lifecycleState.get())
    }

    private fun lifecycleState(): AtomicInteger = VirtualDisplayManager::class.java
        .getDeclaredField("state")
        .apply { isAccessible = true }
        .get(VirtualDisplayManager) as AtomicInteger

    private fun waitUntil(condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }
}
