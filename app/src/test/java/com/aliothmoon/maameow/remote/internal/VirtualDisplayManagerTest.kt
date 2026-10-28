package com.aliothmoon.maameow.remote.internal

import com.aliothmoon.maameow.third.Ln
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VirtualDisplayManagerTest {

    private val trusted = 1 shl 10
    private val ownDisplayGroup = 1 shl 11
    private val alwaysUnlocked = 1 shl 12
    private val ownFocus = 1 shl 14

    @Before
    fun setUp() {
        mockkStatic(Ln::class)
        every { Ln.w(any()) } just runs
    }

    @After
    fun tearDown() = unmockkStatic(Ln::class)

    private fun combined(sdkInt: Int) =
        VirtualDisplayManager.flagTiers(sdkInt).map { tier -> tier.fold(0, Int::or) }

    @Test
    fun `below Android 13 there is a single tier without extra flags`() {
        assertEquals(listOf(0), combined(32))
    }

    @Test
    fun `first tier asks for trusted display and own focus on Android 14+`() {
        val first = combined(34).first()
        assertTrue(first and trusted != 0)
        assertTrue(first and ownFocus != 0)
    }

    @Test
    fun `last tier drops every flag that needs a permission removed in Android 15 QPR2`() {
        for (sdkInt in listOf(33, 34, 35, 36)) {
            val last = combined(sdkInt).last()
            assertFalse(last and trusted != 0)
            assertFalse(last and ownDisplayGroup != 0)
            assertFalse(last and alwaysUnlocked != 0)
        }
    }

    @Test
    fun `first accepted tier wins`() {
        val tried = mutableListOf<Int>()
        val result = VirtualDisplayManager.createWithFallback(listOf(1, 2)) { tried += it; "vd$it" }
        assertEquals(0 to "vd1", result)
        assertEquals(listOf(1), tried)
    }

    @Test
    fun `security exception falls through to the next tier`() {
        val tried = mutableListOf<Int>()
        val result = VirtualDisplayManager.createWithFallback(listOf(1, 2)) {
            tried += it
            if (it == 1) throw SecurityException("Requires ADD_TRUSTED_DISPLAY")
            "vd$it"
        }
        assertEquals(1 to "vd2", result)
        assertEquals(listOf(1, 2), tried)
    }

    @Test(expected = SecurityException::class)
    fun `rejection on the last tier propagates`() {
        VirtualDisplayManager.createWithFallback(listOf(1, 2)) { throw SecurityException("denied") }
    }

    @Test
    fun `effective display flags are named by their hidden bits`() {
        val flags = (1 shl 2) or (1 shl 7) or (1 shl 8) or (1 shl 9) or (1 shl 10) or (1 shl 11) or (1 shl 12)
        assertEquals(
            "[TRUSTED, OWN_DISPLAY_GROUP, ALWAYS_UNLOCKED, TOUCH_FEEDBACK_DISABLED, OWN_FOCUS, STEAL_TOP_FOCUS_DISABLED]",
            VirtualDisplayManager.describeDisplayFlags(flags),
        )
        assertEquals("[TOUCH_FEEDBACK_DISABLED]", VirtualDisplayManager.describeDisplayFlags(1 shl 10))
        assertEquals("[]", VirtualDisplayManager.describeDisplayFlags(0))
    }

    @Test
    fun `other failures do not fall back`() {
        val tried = mutableListOf<Int>()
        val error = runCatching {
            VirtualDisplayManager.createWithFallback(listOf(1, 2)) {
                tried += it
                throw IllegalStateException("surface gone")
            }
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals(listOf(1), tried)
    }
}
