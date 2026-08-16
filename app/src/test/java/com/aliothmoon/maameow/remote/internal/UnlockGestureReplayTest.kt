package com.aliothmoon.maameow.remote.internal

import com.aliothmoon.maameow.domain.models.GesturePoint
import com.aliothmoon.maameow.domain.models.UnlockGesture
import com.aliothmoon.maameow.domain.models.UnlockStep
import com.aliothmoon.maameow.utils.JsonUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 步骤 → 注入时间轴，以及持久化往返 */
class UnlockGestureReplayTest {

    @Test
    fun tapExpandsToDownHoldUp() {
        val gesture = gestureOf(UnlockStep.Tap(100, 200))
        assertEquals(
            listOf(
                InjectAction.Down(100, 200),
                InjectAction.Sleep(60),
                InjectAction.Up(100, 200),
            ),
            UnlockGestureReplay.timeline(gesture, 1080, 2220),
        )
    }

    @Test
    fun swipeEndsWithUpAtLastPoint() {
        val gesture = gestureOf(
            UnlockStep.Swipe(
                listOf(
                    GesturePoint(540, 1800, 0),
                    GesturePoint(540, 1400, 40),
                    GesturePoint(540, 900, 90),
                ),
            ),
        )
        assertEquals(
            listOf(
                InjectAction.Down(540, 1800),
                InjectAction.Sleep(40),
                InjectAction.Move(540, 1400),
                InjectAction.Sleep(50),
                InjectAction.Up(540, 900),
            ),
            UnlockGestureReplay.timeline(gesture, 1080, 2220),
        )
    }

    @Test
    fun delayBeforeStepBecomesLeadingSleep() {
        val gesture = gestureOf(
            UnlockStep.Tap(10, 10),
            UnlockStep.Tap(20, 20, delayBeforeMs = 250),
        )
        assertEquals(InjectAction.Sleep(250), UnlockGestureReplay.timeline(gesture, 1080, 2220)[3])
    }

    @Test
    fun timelineScalesToTargetResolution() {
        val gesture = gestureOf(UnlockStep.Tap(540, 1110))
        val actions = UnlockGestureReplay.timeline(gesture, 540, 1110)
        assertEquals(InjectAction.Down(270, 555), actions.first())
    }

    @Test
    fun gestureSurvivesJsonRoundTrip() {
        val gesture = gestureOf(
            UnlockStep.Tap(1, 2),
            UnlockStep.LongPress(3, 4, holdMs = 800, delayBeforeMs = 120),
            UnlockStep.Swipe(listOf(GesturePoint(5, 6, 0)), delayBeforeMs = 30),
        )
        val json = JsonUtils.common.encodeToString(UnlockGesture.serializer(), gesture)
        assertEquals(gesture, JsonUtils.common.decodeFromString(UnlockGesture.serializer(), json))
    }

    private fun gestureOf(vararg steps: UnlockStep) = UnlockGesture(
        screenWidth = 1080,
        screenHeight = 2220,
        rotation = 0,
        steps = steps.toList(),
    )
}
