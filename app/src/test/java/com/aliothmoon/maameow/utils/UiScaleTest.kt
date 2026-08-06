package com.aliothmoon.maameow.utils

import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiScaleTest {

    @Test
    fun recommended_bySmallestWidth() {
        assertEquals(80, UiScale.recommendedFontSizeScale(320, 1f))
        assertEquals(85, UiScale.recommendedFontSizeScale(350, 1f))
        assertEquals(90, UiScale.recommendedFontSizeScale(380, 1f))
        assertEquals(95, UiScale.recommendedFontSizeScale(411, 1f))
        assertEquals(95, UiScale.recommendedFontSizeScale(600, 1f))
    }

    @Test
    fun recommended_smallSystemFont_canBump() {
        // sw 411 base 95 + small font → 100
        assertEquals(100, UiScale.recommendedFontSizeScale(411, 0.85f))
    }

    @Test
    fun recommended_largeSystemFont_shrinksByTier() {
        // base 95：1.15 → -5，1.3 → -10，1.5 → -15，>1.5 → -20
        assertEquals(90, UiScale.recommendedFontSizeScale(411, 1.15f))
        assertEquals(85, UiScale.recommendedFontSizeScale(411, 1.3f))
        assertEquals(80, UiScale.recommendedFontSizeScale(411, 1.5f))
        assertEquals(80, UiScale.recommendedFontSizeScale(411, 1.8f))
        assertEquals(80, UiScale.recommendedFontSizeScale(411, 2.0f))
    }

    @Test
    fun recommended_largeFontOnNarrowScreen_clampsAtMin() {
        // sw 350 base 85，1.5 档 -15 → 70，钳制到 80
        assertEquals(80, UiScale.recommendedFontSizeScale(350, 1.5f))
    }

    @Test
    fun clampOverlayFontScale() {
        assertEquals(0.85f, UiScale.clampOverlayFontScale(0.5f), 0f)
        assertEquals(1.0f, UiScale.clampOverlayFontScale(1.0f), 0f)
        assertEquals(1.3f, UiScale.clampOverlayFontScale(2.0f), 0f)
    }

    @Test
    fun parseFontSizeScale_autoAndManual() {
        assertEquals(AppSettingsManager.FONT_SIZE_SCALE_AUTO, AppSettingsManager.parseFontSizeScale("auto"))
        assertEquals(AppSettingsManager.FONT_SIZE_SCALE_AUTO, AppSettingsManager.parseFontSizeScale("0"))
        assertEquals(100, AppSettingsManager.parseFontSizeScale("100"))
        assertEquals(80, AppSettingsManager.parseFontSizeScale("80"))
        assertEquals(AppSettingsManager.FONT_SIZE_SCALE_AUTO, AppSettingsManager.parseFontSizeScale("oops"))
        assertEquals(AppSettingsManager.FONT_SIZE_SCALE_AUTO, AppSettingsManager.parseFontSizeScale("200"))
    }

    @Test
    fun resolveFontSizeScale_manualIgnoresRecommendation() {
        assertEquals(
            100,
            AppSettingsManager.resolveFontSizeScale(
                stored = 100,
                smallestWidthDp = 320,
                fontScale = 1f,
            )
        )
    }

    @Test
    fun resolveFontSizeScale_autoUsesRecommendation() {
        assertEquals(
            80,
            AppSettingsManager.resolveFontSizeScale(
                stored = AppSettingsManager.FONT_SIZE_SCALE_AUTO,
                smallestWidthDp = 320,
                fontScale = 1f,
            )
        )
    }

    @Test
    fun isFontSizeScaleAuto() {
        assertTrue(AppSettingsManager.isFontSizeScaleAuto(0))
        assertFalse(AppSettingsManager.isFontSizeScaleAuto(100))
    }
}
