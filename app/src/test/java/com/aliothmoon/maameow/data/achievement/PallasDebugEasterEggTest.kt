package com.aliothmoon.maameow.data.achievement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PallasDebugEasterEggTest {

    private fun egg(
        random: () -> Double = { 0.0 },
        now: () -> Long,
        debounce: Long = 0L,
        exitCooldown: Long = 0L,
    ) = PallasDebugEasterEgg(
        random = random,
        nowMs = now,
        clickDebounceMs = debounce,
        exitCooldownMs = exitCooldown,
    )

    @Test
    fun nineClicks_doNotTrigger() {
        var t = 0L
        val e = egg(now = { t })
        repeat(9) { n ->
            t += 300
            val r = e.onClick()
            assertTrue(r is PallasClickResult.Counting)
            assertEquals(n + 1, (r as PallasClickResult.Counting).n)
        }
        assertFalse(e.isTriggered)
    }

    @Test
    fun tenthClick_lowRandom_entersDebug() {
        var t = 0L
        val e = egg(random = { 0.05 }, now = { t })
        repeat(9) {
            t += 300
            e.onClick()
        }
        t += 300
        assertEquals(PallasClickResult.EnteredDebug, e.onClick())
        assertTrue(e.isTriggered)
    }

    @Test
    fun tenthClick_highRandom_misses() {
        var t = 0L
        val e = egg(random = { 0.5 }, now = { t })
        repeat(9) {
            t += 300
            e.onClick()
        }
        t += 300
        assertEquals(PallasClickResult.MissedRoll, e.onClick())
        assertFalse(e.isTriggered)
    }

    @Test
    fun clickWhileTriggered_afterCooldown_exits() {
        var t = 0L
        val e = egg(now = { t }, exitCooldown = 1_000L)
        repeat(10) {
            t += 300
            e.onClick()
        }
        assertTrue(e.isTriggered)
        t += 500
        assertEquals(PallasClickResult.Ignored, e.onClick())
        assertTrue(e.isTriggered)
        t += 600
        assertEquals(PallasClickResult.ExitedDebug, e.onClick())
        assertFalse(e.isTriggered)
    }

    @Test
    fun debounce_ignoresRapidClicks() {
        var t = 0L
        val e = egg(now = { t }, debounce = 280L)
        t = 1000
        assertTrue(e.onClick() is PallasClickResult.Counting)
        t = 1100 // +100ms < 280
        assertEquals(PallasClickResult.Ignored, e.onClick())
        t = 1300 // +200 from last accepted? last was 1000, 1300-1000=300 >= 280
        assertTrue(e.onClick() is PallasClickResult.Counting)
    }

    @Test
    fun afterMiss_canRetryAndEnter() {
        var t = 0L
        var roll = 0.9
        val e = egg(random = { roll }, now = { t })
        repeat(10) {
            t += 300
            e.onClick()
        }
        assertFalse(e.isTriggered)
        roll = 0.01
        repeat(9) {
            t += 300
            e.onClick()
        }
        t += 300
        assertEquals(PallasClickResult.EnteredDebug, e.onClick())
        assertTrue(e.isTriggered)
    }
}
