package com.aliothmoon.maameow.remote.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameCountFpsEstimatorTest {

    private val estimator = FrameCountFpsEstimator()
    private val second = 1_000_000_000L

    @Test
    fun firstSampleHasNoRate() {
        assertNull(estimator.sample(100, second))
    }

    @Test
    fun rateIsFramesPerElapsedSecond() {
        estimator.sample(0, 0)
        assertEquals(60f, estimator.sample(60, second)!!, 0.001f)
        // 半秒 15 帧 = 30 fps
        assertEquals(30f, estimator.sample(75, second + second / 2)!!, 0.001f)
    }

    @Test
    fun staticScreenYieldsZero() {
        estimator.sample(10, 0)
        assertEquals(0f, estimator.sample(10, second)!!, 0f)
    }

    @Test
    fun counterResetSkipsOneIntervalThenRecovers() {
        estimator.sample(500, 0)
        // 截图器重建后计数从 0 重来
        assertNull(estimator.sample(3, second))
        assertEquals(57f, estimator.sample(60, 2 * second)!!, 0.001f)
    }

    @Test
    fun nonAdvancingClockIsIgnored() {
        estimator.sample(0, second)
        assertNull(estimator.sample(30, second))
    }
}
