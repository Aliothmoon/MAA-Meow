package com.aliothmoon.maameow.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunDurationLimitTest {

    @Test
    fun resolve_profileUnset_followsGlobal() {
        assertEquals(90, RunDurationLimit.resolve(profileMinutes = null, globalEnabled = true, globalMinutes = 90))
        assertNull(RunDurationLimit.resolve(profileMinutes = null, globalEnabled = false, globalMinutes = 90))
    }

    @Test
    fun resolve_profileSet_overridesGlobalRegardlessOfSwitch() {
        assertEquals(30, RunDurationLimit.resolve(profileMinutes = 30, globalEnabled = true, globalMinutes = 90))
        assertEquals(600, RunDurationLimit.resolve(profileMinutes = 600, globalEnabled = false, globalMinutes = 90))
    }

    @Test
    fun resolve_profileOutOfRange_isClamped() {
        assertEquals(
            RunDurationLimit.MIN_MINUTES,
            RunDurationLimit.resolve(profileMinutes = 0, globalEnabled = false, globalMinutes = 90),
        )
        assertEquals(
            RunDurationLimit.MAX_MINUTES,
            RunDurationLimit.resolve(profileMinutes = 99_999, globalEnabled = false, globalMinutes = 90),
        )
    }
}
