package com.aliothmoon.maameow.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaaMotionTest {

    @Test
    fun duration_reduceMotionSnapsToZero() {
        assertEquals(0, MaaMotion.duration(reduceMotion = true, millis = 220))
        assertEquals(220, MaaMotion.duration(reduceMotion = false, millis = 220))
    }

    @Test
    fun pagerDuration_growsWithDistanceThenSnaps() {
        assertEquals(MaaMotion.Medium, MaaMotion.pagerDuration(1, reduceMotion = false))
        assertEquals(MaaMotion.Medium + MaaMotion.Fast, MaaMotion.pagerDuration(2, reduceMotion = false))
        assertEquals(0, MaaMotion.pagerDuration(3, reduceMotion = true))
    }

    @Test
    fun expandTransitions_noneWhenReduced() {
        assertEquals(
            androidx.compose.animation.EnterTransition.None,
            MaaMotion.expandIn(reduceMotion = true),
        )
        assertEquals(
            androidx.compose.animation.ExitTransition.None,
            MaaMotion.expandOut(reduceMotion = true),
        )
        assertTrue(MaaMotion.expandIn(reduceMotion = false) != androidx.compose.animation.EnterTransition.None)
    }
}
