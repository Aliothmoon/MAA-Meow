package com.aliothmoon.maameow.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RainbowFlowTest {
    @Test
    fun disabledAnimationsRequestNoFramesAndResumeWhenEnabled() = runTest {
        val clock = BroadcastFrameClock()
        val scale = TestScale(0f)
        val phase = Animatable(0f)
        val job = launch(clock + scale) { runRainbowFlow(phase) { true } }
        runCurrent()
        assertFalse(clock.hasAwaiters)

        scale.scaleFactor = 1f
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertTrue(clock.hasAwaiters)
        clock.sendFrame(0L)
        runCurrent()
        clock.sendFrame(1_000_000_000L)
        runCurrent()
        assertTrue(phase.value > 0f)

        scale.scaleFactor = 0f
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertFalse(clock.hasAwaiters)
        val paused = phase.value
        clock.sendFrame(2_000_000_000L)
        runCurrent()
        assertEquals(paused, phase.value, 0f)
        job.cancelAndJoin()
    }

    @Test
    fun hiddenAnimationSuspendsAndKeepsItsPhase() = runTest {
        val clock = BroadcastFrameClock()
        var visible by mutableStateOf(true)
        val phase = Animatable(0f)
        val job = launch(clock) { runRainbowFlow(phase) { visible } }
        runCurrent()
        clock.sendFrame(0L)
        runCurrent()
        clock.sendFrame(1_000_000_000L)
        runCurrent()
        val paused = phase.value

        visible = false
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertFalse(clock.hasAwaiters)
        visible = true
        Snapshot.sendApplyNotifications()
        runCurrent()
        clock.sendFrame(2_000_000_000L)
        runCurrent()
        assertEquals(paused, phase.value, 0.001f)
        job.cancelAndJoin()
    }

    @Test
    fun scaleThatWritesStateOnFirstReadDoesNotCrash() = runTest {
        val clock = BroadcastFrameClock()
        val phase = Animatable(0f)
        val job = launch(clock + LazyObservingScale()) { runRainbowFlow(phase) { true } }
        runCurrent()
        assertTrue(clock.hasAwaiters)
        job.cancelAndJoin()
    }

    private class TestScale(initial: Float) : MotionDurationScale {
        override var scaleFactor by mutableFloatStateOf(initial)
    }

    // 仿 Compose 的 MotionDurationScaleImpl：首读才写 state 起监听
    private class LazyObservingScale : MotionDurationScale {
        private var observing = false
        private var value by mutableFloatStateOf(0f)
        override val scaleFactor: Float
            get() {
                if (!observing) {
                    observing = true
                    value = 1f
                }
                return value
            }
    }
}
