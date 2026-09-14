package com.aliothmoon.maameow.presentation.view.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunDurationStepTest {

    @Test
    fun stepsByTenMinutes() {
        assertEquals(250, RunDurationStep.increase(240))
        assertEquals(230, RunDurationStep.decrease(240))
    }

    @Test
    fun offGridValuesSnapToNeighbourStep() {
        assertEquals(50, RunDurationStep.increase(45))
        assertEquals(40, RunDurationStep.decrease(45))
        assertEquals(10, RunDurationStep.increase(1))
    }

    @Test
    fun boundsDisableButtons() {
        assertFalse(RunDurationStep.canDecrease(10))
        assertTrue(RunDurationStep.canDecrease(11))
        assertEquals(10, RunDurationStep.decrease(11))
        assertFalse(RunDurationStep.canIncrease(1440))
        assertEquals(1440, RunDurationStep.increase(1430))
    }

    @Test
    fun inputAcceptsDigitsOnly() {
        assertTrue(RunDurationStep.acceptsInput(""))
        assertTrue(RunDurationStep.acceptsInput("1440"))
        assertFalse(RunDurationStep.acceptsInput("14400"))
        assertFalse(RunDurationStep.acceptsInput("-5"))
        assertFalse(RunDurationStep.acceptsInput("1.5"))
    }

    @Test
    fun inputIsClampedAndBlankKeepsValue() {
        assertEquals(null, RunDurationStep.parseInput(""))
        assertEquals(1, RunDurationStep.parseInput("0"))
        assertEquals(45, RunDurationStep.parseInput("45"))
        assertEquals(1440, RunDurationStep.parseInput("9999"))
    }

    @Test
    fun remainingRoundsUpToSecond() {
        assertEquals("4:00:00", RunDurationStep.formatRemaining(4 * 3_600_000L))
        assertEquals("0:00:01", RunDurationStep.formatRemaining(1L))
        assertEquals("1:02:03", RunDurationStep.formatRemaining(3_723_000L))
        assertEquals("0:00:00", RunDurationStep.formatRemaining(-5L))
    }
}
