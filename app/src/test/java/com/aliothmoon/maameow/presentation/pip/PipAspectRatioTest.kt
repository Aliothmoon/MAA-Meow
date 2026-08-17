package com.aliothmoon.maameow.presentation.pip

import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 系统只接受 [1/2.39, 2.39]，越界会在 setAspectRatio 直接抛 IllegalArgumentException */
class PipAspectRatioTest {

    private val maxRatio = PipController.MAX_RATIO
    private val minRatio = PipController.MIN_RATIO

    private fun PipAspectRatio.value(): Float = numerator.toFloat() / denominator

    private fun assertWithinSystemBounds(ratio: PipAspectRatio) {
        val value = ratio.value()
        assertTrue("$value 超出系统允许区间", value in minRatio..maxRatio)
        assertTrue("分母必须为正", ratio.denominator > 0)
        assertTrue("分子必须为正", ratio.numerator > 0)
    }

    @Test
    fun `区间内的分辨率原样保留`() {
        assertEquals(PipAspectRatio(1280, 720), PipController.clampAspectRatio(1280, 720))
        assertEquals(PipAspectRatio(1920, 1080), PipController.clampAspectRatio(1920, 1080))
    }

    @Test
    fun `默认与 1080p 分辨率都在允许区间内`() {
        assertWithinSystemBounds(
            PipController.clampAspectRatio(
                DefaultDisplayConfig.RES_720P.width,
                DefaultDisplayConfig.RES_720P.height,
            )
        )
        assertWithinSystemBounds(
            PipController.clampAspectRatio(
                DefaultDisplayConfig.RES_1080P.width,
                DefaultDisplayConfig.RES_1080P.height,
            )
        )
    }

    @Test
    fun `超宽分辨率夹到上界`() {
        val ratio = PipController.clampAspectRatio(3840, 720)
        assertWithinSystemBounds(ratio)
        assertEquals(maxRatio, ratio.value(), 0.001f)
    }

    @Test
    fun `超窄分辨率夹到下界`() {
        val ratio = PipController.clampAspectRatio(720, 3840)
        assertWithinSystemBounds(ratio)
        assertEquals(minRatio, ratio.value(), 0.001f)
    }

    @Test
    fun `恰好落在边界上不被改写`() {
        assertEquals(PipAspectRatio(239, 100), PipController.clampAspectRatio(239, 100))
        assertEquals(PipAspectRatio(100, 239), PipController.clampAspectRatio(100, 239))
    }

    @Test
    fun `非法尺寸回落到 16 比 9`() {
        val expected = PipAspectRatio(
            DefaultDisplayConfig.ASPECT_RATIO_WIDTH,
            DefaultDisplayConfig.ASPECT_RATIO_HEIGHT,
        )
        assertEquals(expected, PipController.clampAspectRatio(0, 0))
        assertEquals(expected, PipController.clampAspectRatio(1280, 0))
        assertEquals(expected, PipController.clampAspectRatio(-1280, 720))
    }

    @Test
    fun `极端方形与细长比例都不越界`() {
        listOf(
            1 to 1,
            1 to 10000,
            10000 to 1,
            2560 to 1080,
            1080 to 2560,
        ).forEach { (w, h) ->
            assertWithinSystemBounds(PipController.clampAspectRatio(w, h))
        }
    }
}
