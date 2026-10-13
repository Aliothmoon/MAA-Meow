package com.aliothmoon.maameow.maa.callback

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetParamsErrorSignalTest {

    private val signal = SetParamsErrorSignal()

    @Test
    fun wakesOnErrorMarkedAfterSnapshot() = runTest {
        val before = signal.current
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            signal.awaitAfter(before, timeoutMs = 1500)
        }
        assertFalse(waiting.isCompleted)
        signal.mark()
        assertTrue(waiting.await())
    }

    @Test
    fun errorMarkedBeforeWaiting_stillCounts() = runTest {
        val before = signal.current
        signal.mark()
        assertTrue(signal.awaitAfter(before, timeoutMs = 1500))
    }

    @Test
    fun rejectionWithoutCallback_timesOut() = runTest {
        signal.mark()
        assertFalse(signal.awaitAfter(signal.current, timeoutMs = 1500))
    }
}
