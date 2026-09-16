package com.aliothmoon.maameow.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EyeProtectionDetectorTest {

    @Test
    fun `when all settings are off then returns false`() {
        val result = EyeProtectionDetector.detectInternal(
            secureGetter = { false },
            systemGetter = { false },
            globalGetter = { false },
            serviceChecker = { false }
        )

        assertFalse(result.isEnabled)
        assertNull(result.source)
    }

    @Test
    fun `when AOSP night display is activated then returns true`() {
        val result = EyeProtectionDetector.detectInternal(
            secureGetter = { it == "night_display_activated" },
            systemGetter = { false },
            globalGetter = { false },
            serviceChecker = { false }
        )

        assertTrue(result.isEnabled)
        assertEquals("aosp:night_display_activated", result.source)
    }

    @Test
    fun `when Xiaomi paper mode is enabled then returns true`() {
        val result = EyeProtectionDetector.detectInternal(
            secureGetter = { false },
            systemGetter = { it == "screen_paper_mode_enabled" },
            globalGetter = { false },
            serviceChecker = { false }
        )

        assertTrue(result.isEnabled)
        assertEquals("xiaomi:screen_paper_mode_enabled", result.source)
    }

    @Test
    fun `when Huawei eyes protection mode is enabled then returns true`() {
        val result = EyeProtectionDetector.detectInternal(
            secureGetter = { false },
            systemGetter = { it == "eyes_protection_mode" },
            globalGetter = { false },
            serviceChecker = { false }
        )

        assertTrue(result.isEnabled)
        assertEquals("huawei:eyes_protection_mode", result.source)
    }

    @Test
    fun `when Samsung blue light filter is enabled in system then returns true`() {
        val result = EyeProtectionDetector.detectInternal(
            secureGetter = { false },
            systemGetter = { it == "blue_light_filter" },
            globalGetter = { false },
            serviceChecker = { false }
        )

        assertTrue(result.isEnabled)
        assertEquals("samsung:blue_light_filter", result.source)
    }

    @Test
    fun `when Samsung blue light filter is enabled in global then returns true`() {
        val result = EyeProtectionDetector.detectInternal(
            secureGetter = { false },
            systemGetter = { false },
            globalGetter = { it == "blue_light_filter" },
            serviceChecker = { false }
        )

        assertTrue(result.isEnabled)
        assertEquals("samsung:blue_light_filter", result.source)
    }

    @Test
    fun `when Oppo eye protect is enabled then returns true`() {
        val result = EyeProtectionDetector.detectInternal(
            secureGetter = { false },
            systemGetter = { it == "coloros_eyeprotect_enable" },
            globalGetter = { false },
            serviceChecker = { false }
        )

        assertTrue(result.isEnabled)
        assertEquals("oppo:coloros_eyeprotect_enable", result.source)
    }

    @Test
    fun `when Vivo night display is enabled then returns true`() {
        val result = EyeProtectionDetector.detectInternal(
            secureGetter = { false },
            systemGetter = { it == "vivo_night_display" },
            globalGetter = { false },
            serviceChecker = { false }
        )

        assertTrue(result.isEnabled)
        assertEquals("vivo:vivo_night_display", result.source)
    }

    @Test
    fun `when settings missed but color display service is activated then returns true`() {
        val result = EyeProtectionDetector.detectInternal(
            secureGetter = { false },
            systemGetter = { false },
            globalGetter = { false },
            serviceChecker = { true }
        )

        assertTrue(result.isEnabled)
        assertEquals("color_display:isNightDisplayActivated", result.source)
    }
}
