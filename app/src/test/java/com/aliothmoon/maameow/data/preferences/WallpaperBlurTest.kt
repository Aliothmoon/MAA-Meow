package com.aliothmoon.maameow.data.preferences
import org.junit.Assert.assertEquals
import org.junit.Test
class WallpaperBlurTest {
    @Test
    fun percentMapsLinearlyToRadiusDp() {
        assertEquals(0f, WallpaperBlur.percentToRadiusDp(0), 0.001f)
        assertEquals(6f, WallpaperBlur.percentToRadiusDp(50), 0.001f)
        assertEquals(12f, WallpaperBlur.percentToRadiusDp(100), 0.001f)
    }
    @Test
    fun legacyDpConvertsToPercent() {
        assertEquals(0, WallpaperBlur.legacyDpToPercent(0))
        assertEquals(50, WallpaperBlur.legacyDpToPercent(6))
        assertEquals(100, WallpaperBlur.legacyDpToPercent(12))
    }
    @Test
    fun parsePercentClamps() {
        assertEquals(0, WallpaperBlur.parsePercent("-1"))
        assertEquals(100, WallpaperBlur.parsePercent("200"))
        assertEquals(33, WallpaperBlur.parsePercent("33"))
    }

    @Test
    fun percentMapsToSoftwareRadiusPx() {
        assertEquals(0, WallpaperBlur.percentToSoftwareRadiusPx(0))
        assertEquals(WallpaperBlur.SOFTWARE_RADIUS_PX_MAX / 2, WallpaperBlur.percentToSoftwareRadiusPx(50))
        assertEquals(WallpaperBlur.SOFTWARE_RADIUS_PX_MAX, WallpaperBlur.percentToSoftwareRadiusPx(100))
    }
}
