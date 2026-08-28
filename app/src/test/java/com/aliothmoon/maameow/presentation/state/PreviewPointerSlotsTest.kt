package com.aliothmoon.maameow.presentation.state

import com.aliothmoon.maameow.maa.TouchPointerSequence
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewPointerSlotsTest {

    private val slots = PreviewPointerSlots()

    @Test
    fun firstPointerGetsContactZero_secondGetsOne() {
        assertEquals(0, slots.acquire(100L))
        assertEquals(1, slots.acquire(101L))
        assertEquals(0, slots.indexOf(100L))
        assertEquals(1, slots.indexOf(101L))
    }

    @Test
    fun acquireIsIdempotentForSamePointer() {
        assertEquals(0, slots.acquire(100L))
        assertEquals(0, slots.acquire(100L))
        assertEquals(1, slots.acquire(101L))
    }

    @Test
    fun releasedSlotIsReusedByNextPointer() {
        slots.acquire(100L)
        slots.acquire(101L)
        assertEquals(0, slots.release(100L))
        assertEquals(-1, slots.indexOf(100L))
        assertEquals(0, slots.acquire(102L))
        assertEquals(1, slots.indexOf(101L))
    }

    @Test
    fun releaseUnknownPointerReturnsMinusOne() {
        assertEquals(-1, slots.release(999L))
    }

    @Test
    fun fullTableRejectsNewPointer() {
        val max = TouchPointerSequence.MAX_CONTACTS
        for (i in 0 until max) {
            assertEquals(i, slots.acquire(1000L + i))
        }
        assertEquals(-1, slots.acquire(2000L))
        slots.release(1000L)
        assertEquals(0, slots.acquire(2000L))
    }
}
