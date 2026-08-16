package com.aliothmoon.maameow.remote.internal

import com.aliothmoon.maameow.domain.models.UnlockStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 轨迹 → 可展示、可回放的步骤 */
class UnlockGestureCodecTest {

    @Test
    fun shortStaticStrokeIsTap() {
        val steps = UnlockGestureCodec.toSteps(
            listOf(TouchStroke(listOf(TouchPoint(120, 340, 0)), endTMs = 90)),
        )
        assertEquals(UnlockStep.Tap(120, 340, delayBeforeMs = 0), steps.single())
    }

    @Test
    fun longStaticStrokeIsLongPress() {
        val steps = UnlockGestureCodec.toSteps(
            listOf(TouchStroke(listOf(TouchPoint(120, 340, 0)), endTMs = 700)),
        )
        assertEquals(UnlockStep.LongPress(120, 340, holdMs = 700, delayBeforeMs = 0), steps.single())
    }

    @Test
    fun movingStrokeIsSwipeEvenWhenSlow() {
        val points = (0..10).map { TouchPoint(540, 1800 - it * 90, it * 60) }
        val steps = UnlockGestureCodec.toSteps(listOf(TouchStroke(points, endTMs = 620)))

        val swipe = steps.single() as UnlockStep.Swipe
        assertEquals(540 to 1800, swipe.points.first().let { it.x to it.y })
        assertEquals(540 to 900, swipe.points.last().let { it.x to it.y })
        assertEquals(0, swipe.points.first().tMs)
    }

    @Test
    fun jitterWithinToleranceStaysTap() {
        // 手抖十几像素不该被当成滑动
        val points = listOf(
            TouchPoint(200, 200, 0),
            TouchPoint(208, 206, 30),
            TouchPoint(203, 201, 60),
        )
        assertTrue(UnlockGestureCodec.toSteps(listOf(TouchStroke(points, 120))).single() is UnlockStep.Tap)
    }

    @Test
    fun firstStepHasNoDelayAndGapsAreClamped() {
        val steps = UnlockGestureCodec.toSteps(
            listOf(
                TouchStroke(listOf(TouchPoint(10, 10, 3000)), endTMs = 3060),
                TouchStroke(listOf(TouchPoint(20, 20, 3400)), endTMs = 3460),
                // 中间犹豫了 8 秒，回放时不该照抄
                TouchStroke(listOf(TouchPoint(30, 30, 11_460)), endTMs = 11_520),
            ),
        )
        assertEquals(listOf(0, 340, UnlockGestureCodec.MAX_GAP_MS), steps.map { it.delayBeforeMs })
    }

    @Test
    fun swipePointsAreDecimatedAndCapped() {
        // 1ms 一个点、每次只挪 1px：既不满足时间间隔也不满足位移，应被大量丢弃
        val dense = (0..900).map { TouchPoint(100 + it, 500, it) }
        val swipe = UnlockGestureCodec.toSteps(listOf(TouchStroke(dense, endTMs = 900)))
            .single() as UnlockStep.Swipe

        assertTrue(swipe.points.size <= UnlockGestureCodec.MAX_POINTS_PER_SWIPE)
        assertTrue(swipe.points.size < dense.size / 2)
        // 首尾必须保留，否则轨迹起终点会偏
        assertEquals(100 to 500, swipe.points.first().let { it.x to it.y })
        assertEquals(1000 to 500, swipe.points.last().let { it.x to it.y })
    }

    @Test
    fun stepCountIsCapped() {
        val strokes = (0 until 200).map {
            TouchStroke(listOf(TouchPoint(10, 10, it * 100)), endTMs = it * 100 + 50)
        }
        assertEquals(UnlockGestureCodec.MAX_STEPS, UnlockGestureCodec.toSteps(strokes).size)
    }
}
