package com.aliothmoon.maameow.domain.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSessionSlotTest {

    private fun progress(text: String = "run") = LiveSession(
        sessionId = LiveNotifyIds.PROGRESS_SESSION,
        category = LiveCategory.PROGRESS,
        title = "t",
        text = text,
        capsuleText = "cap",
        ongoing = true,
        firstFloat = true,
    )

    private fun result(title: String = "done") = LiveSession(
        sessionId = LiveNotifyIds.RESULT_SESSION,
        category = LiveCategory.RESULT,
        title = title,
        text = "body",
        capsuleText = title,
        ongoing = true,
        firstFloat = true,
        timeoutSec = 15,
    )

    @Test
    fun progressDedupesIdenticalUpdates() {
        val slot = LiveSessionSlot()
        slot.beginProgress(progress())
        assertNull(slot.updateProgress(progress()))
        assertNotNull(slot.updateProgress(progress("next")))
    }

    @Test
    fun resultIsPendingWhileProgressActiveThenFlushed() {
        val slot = LiveSessionSlot()
        slot.beginProgress(progress())
        assertNull(slot.offerResult(result()))
        val flushed = slot.endProgress()
        assertEquals("done", flushed?.title)
        assertFalse(slot.progressActive)
    }

    /** 任务链未登记时 max=0，下游据此渲染不确定态而不是停在 0% */
    @Test
    fun progressPercentIsNullWhenTotalUnknown() {
        assertNull(progress().copy(progressCurrent = 0, progressMax = 0).progressPercent())
        assertNull(progress().copy(progressCurrent = 0, progressMax = null).progressPercent())
        assertEquals(50, progress().copy(progressCurrent = 500, progressMax = 1000).progressPercent())
    }

    @Test
    fun resultPublishesImmediatelyWhenProgressIdle() {
        val slot = LiveSessionSlot()
        val ready = slot.offerResult(result())
        assertEquals("done", ready?.title)
        assertNull(slot.pendingResult)
    }
}

class LiveRunGateTest {

    @Test
    fun staleTokenIsRejectedAfterBeginRun() {
        val gate = LiveRunGate()
        val first = gate.beginRun()
        val second = gate.beginRun()
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test
    fun resultCanBeClaimedOncePerRun() {
        val gate = LiveRunGate()
        val token = gate.beginRun()
        assertTrue(gate.tryClaimResult(token))
        assertFalse(gate.tryClaimResult(token))
        val next = gate.beginRun()
        assertTrue(gate.tryClaimResult(next))
    }

    @Test
    fun zeroTokenIsNeverCurrent() {
        val gate = LiveRunGate()
        assertFalse(gate.isCurrent(0L))
        assertFalse(gate.tryClaimResult(0L))
    }
}

class FocusSequenceTest {

    @Test
    fun nextIsStrictlyIncreasing() {
        val seq = FocusSequence.next(10L, 5L)
        assertTrue(seq > 10L)
        assertEquals(11L, seq)
    }

    @Test
    fun nextJumpsToNowWhenClockAhead() {
        assertEquals(100L, FocusSequence.next(10L, 100L))
    }
}
