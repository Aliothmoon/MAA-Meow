package com.aliothmoon.maameow.data.achievement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PallasSpeakTest {

    private fun glyphCount(s: String) = s.codePointCount(0, s.length)

    @Test
    fun random_lengthStaysInHalfOpenRange() {
        // 对齐 WPF Random.Next(low, high)：取不到 high
        repeat(200) {
            val n = glyphCount(PallasSpeak.random(3, 6))
            assertTrue("len=$n", n in 3..5)
        }
    }

    @Test
    fun random_tipRangeMatchesWpf() {
        repeat(200) {
            val n = glyphCount(PallasSpeak.random(1, 10))
            assertTrue("len=$n", n in 1..9)
        }
    }

    @Test
    fun random_degenerateRangeFallsBackToLow() {
        assertEquals(4, glyphCount(PallasSpeak.random(4, 4)))
        assertEquals(4, glyphCount(PallasSpeak.random(4, 2)))
    }

    /** 醉话不比原文长：短标签不该被一串酒杯撑爆 */
    @Test
    fun random_neverExceedsCap() {
        repeat(200) {
            assertEquals(2, glyphCount(PallasSpeak.random(3, 6, cap = 2)))
            assertEquals(1, glyphCount(PallasSpeak.random(3, 6, cap = 1)))
        }
    }

    @Test
    fun random_capAboveDrawnRangeLeavesItAlone() {
        repeat(200) {
            val n = glyphCount(PallasSpeak.random(3, 6, cap = 99))
            assertTrue("len=$n", n in 3..5)
        }
    }

    @Test
    fun random_emptyOriginalStaysEmpty() {
        assertEquals("", PallasSpeak.random(3, 6, cap = 0))
        assertEquals("", PallasSpeak.random(3, 6, cap = -1))
    }

    @Test
    fun random_onlyUsesDrinkGlyphs() {
        val allowed = setOf("💃", "🕺", "🍷", "🍸", "🍺", "🍻", "🥃", "🍶")
        val s = PallasSpeak.random(3, 6, random = Random(42))
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val glyph = String(Character.toChars(cp))
            assertTrue(glyph, glyph in allowed)
            i += Character.charCount(cp)
        }
    }
}
