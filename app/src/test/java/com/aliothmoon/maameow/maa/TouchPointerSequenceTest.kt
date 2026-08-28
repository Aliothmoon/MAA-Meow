package com.aliothmoon.maameow.maa

import com.aliothmoon.maameow.maa.TouchPointerSequence.Kind
import com.aliothmoon.maameow.maa.TouchPointerSequence.Pointer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 多指规划：pointer index 必须紧凑且 ACTION_POINTER_INDEX 指向正确的手指，
 * 否则系统侧会把第二指的按下/抬起记到别的手指上
 */
class TouchPointerSequenceTest {

    private fun p(contact: Int, x: Float = contact * 10f) = Pointer(contact, x, 0f)

    @Test
    fun firstDown_isActionDown() {
        val step = TouchPointerSequence.plan(Kind.Down, emptyList(), 0, 1f, 2f)
        assertTrue(step.ok)
        assertEquals(TouchPointerSequence.ACTION_DOWN, step.actionMasked)
        assertEquals(0, step.changingIndex)
        assertFalse(step.cancelFirst)
        assertEquals(listOf(Pointer(0, 1f, 2f)), step.pointers)
    }

    @Test
    fun secondDown_isPointerDownAtNewIndex() {
        val step = TouchPointerSequence.plan(Kind.Down, listOf(p(0)), 1, 8f, 9f)
        assertTrue(step.ok)
        assertEquals(TouchPointerSequence.ACTION_POINTER_DOWN, step.actionMasked)
        assertEquals(1, step.changingIndex)
        assertEquals(listOf(p(0), Pointer(1, 8f, 9f)), step.pointers)
    }

    @Test
    fun liftSecondFingerFirst_isPointerUp() {
        val current = listOf(p(0), p(1))
        val step = TouchPointerSequence.plan(Kind.Up, current, 1, 10f, 0f)
        assertTrue(step.ok)
        assertEquals(TouchPointerSequence.ACTION_POINTER_UP, step.actionMasked)
        assertEquals(1, step.changingIndex)
        assertEquals(current, step.pointers)
    }

    @Test
    fun liftFirstFingerWhileAnotherStays_isPointerUpAtIndex0() {
        val current = listOf(p(0), p(1))
        val step = TouchPointerSequence.plan(Kind.Up, current, 0, 0f, 0f)
        assertTrue(step.ok)
        assertEquals(TouchPointerSequence.ACTION_POINTER_UP, step.actionMasked)
        assertEquals(0, step.changingIndex)
        assertEquals(current, step.pointers)
    }

    @Test
    fun lastFingerUp_isActionUp() {
        val step = TouchPointerSequence.plan(Kind.Up, listOf(p(1)), 1, 10f, 0f)
        assertTrue(step.ok)
        assertEquals(TouchPointerSequence.ACTION_UP, step.actionMasked)
        assertEquals(listOf(p(1)), step.pointers)
    }

    @Test
    fun up_carriesLiftPointOfThatContact() {
        // fw 的 touch_up 传的是该手指最后位置；抬起坐标要落到被抬的那根手指上
        val step = TouchPointerSequence.plan(Kind.Up, listOf(p(0), p(1)), 1, 40f, 50f)
        assertTrue(step.ok)
        assertEquals(Pointer(1, 40f, 50f), step.pointers[1])
        assertEquals(p(0), step.pointers[0])
    }

    @Test
    fun move_updatesOnlyThatContact() {
        val step = TouchPointerSequence.plan(Kind.Move, listOf(p(0), p(1)), 1, 40f, 50f)
        assertTrue(step.ok)
        assertEquals(TouchPointerSequence.ACTION_MOVE, step.actionMasked)
        assertEquals(Pointer(1, 40f, 50f), step.pointers[1])
        assertEquals(p(0), step.pointers[0])
    }

    @Test
    fun repeatDownOnSameContact_cancelsThenStartsNewGesture() {
        val step = TouchPointerSequence.plan(Kind.Down, listOf(p(0), p(1)), 0, 3f, 4f)
        assertTrue(step.ok)
        assertTrue(step.cancelFirst)
        assertEquals(TouchPointerSequence.ACTION_DOWN, step.actionMasked)
        assertEquals(listOf(Pointer(0, 3f, 4f)), step.pointers)
    }

    @Test
    fun moveOrUpWithoutThatContact_isRejected() {
        assertFalse(TouchPointerSequence.plan(Kind.Move, listOf(p(0)), 1, 0f, 0f).ok)
        assertFalse(TouchPointerSequence.plan(Kind.Up, listOf(p(0)), 1, 0f, 0f).ok)
    }

    @Test
    fun outOfRangeContact_isRejected() {
        assertFalse(TouchPointerSequence.plan(Kind.Down, emptyList(), -1, 0f, 0f).ok)
        assertFalse(
            TouchPointerSequence.plan(
                Kind.Down,
                emptyList(),
                TouchPointerSequence.MAX_CONTACTS,
                0f,
                0f,
            ).ok,
        )
    }
}
