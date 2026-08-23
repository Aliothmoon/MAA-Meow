package com.aliothmoon.maameow.utils

import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiScaleTest {

    @Test
    fun recommended_360AndWider_is95() {
        assertEquals(95, UiScale.recommendedFontSizeScale(360, 1f))
        assertEquals(95, UiScale.recommendedFontSizeScale(393, 1f))
        assertEquals(95, UiScale.recommendedFontSizeScale(411, 1f))
        assertEquals(95, UiScale.recommendedFontSizeScale(600, 1f))
        assertEquals(95, UiScale.recommendedFontSizeScale(0, 1f))
    }

    @Test
    fun recommended_narrowerThan360_lerpsTo90() {
        assertEquals(90, UiScale.recommendedFontSizeScale(320, 1f))
        assertEquals(90, UiScale.recommendedFontSizeScale(280, 1f))
        assertEquals(93, UiScale.recommendedFontSizeScale(344, 1f))
        assertEquals(94, UiScale.recommendedFontSizeScale(352, 1f))
    }

    @Test
    fun recommended_systemFontDoesNotShrinkTypicalPhones() {
        assertEquals(95, UiScale.recommendedFontSizeScale(411, 0.85f))
        assertEquals(95, UiScale.recommendedFontSizeScale(411, 1.15f))
        assertEquals(95, UiScale.recommendedFontSizeScale(411, 1.8f))
    }

    @Test
    fun recommended_largeFontOnNarrowScreen_onlyNudge() {
        assertEquals(85, UiScale.recommendedFontSizeScale(320, 1.5f))
        assertEquals(88, UiScale.recommendedFontSizeScale(344, 1.5f))
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
            90,
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
