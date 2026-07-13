package com.aliothmoon.maameow.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperContentContrastTest {
    private val colorfulSeed = 0xFF3D7CF0.toInt()

    @Test
    fun pureDarkForcesBlackSurfaces() {
        val gray = darkColorScheme(
            background = Color(0xFF121212),
            surface = Color(0xFF1C1C1E),
            surfaceContainer = Color(0xFF2C2C2E),
            surfaceContainerHighest = Color(0xFF3A3A3C),
        )
        val pure = gray.withPureDarkSurfaces()
        assertTrue(pure.background.luminance() < 0.01f)
        assertTrue(pure.surface.luminance() < 0.01f)
        assertTrue(pure.surfaceContainer.luminance() < 0.02f)
        assertTrue(pure.surfaceContainerHighest.luminance() < 0.05f)
    }

    @Test
    fun darkThemeUsesLightDynamicInk() {
        val base = darkColorScheme()
        val adapted = with(WallpaperColorScheme) {
            base.adaptContentForWallpaper(colorfulSeed, isDark = true)
        }
        // tone ~90 → high luminance (readable on dark UI)
        assertTrue(adapted.onSurface.luminance() > 0.50f)
        assertTrue(adapted.onSurface.luminance() < 0.98f)
        val spread = colorSpread(adapted.onSurface)
        assertTrue(spread > 0.03f)
        // hints stay system-owned
        assertTrue(adapted.onSurfaceVariant == base.onSurfaceVariant)
    }

    @Test
    fun lightThemeUsesDarkDynamicInk() {
        val base = lightColorScheme()
        val adapted = with(WallpaperColorScheme) {
            base.adaptContentForWallpaper(colorfulSeed, isDark = false)
        }
        // tone ~18 → deep ink on light UI
        assertTrue(adapted.onSurface.luminance() < 0.18f)
        val spread = colorSpread(adapted.onSurface)
        assertTrue(spread > 0.03f)
        assertTrue(adapted.onSurfaceVariant == base.onSurfaceVariant)
    }

    @Test
    fun lightAndDarkInkLuminanceDiffer() {
        val seed = colorfulSeed
        val darkInk = with(WallpaperColorScheme) {
            darkColorScheme().adaptContentForWallpaper(seed, isDark = true).onSurface
        }
        val lightInk = with(WallpaperColorScheme) {
            lightColorScheme().adaptContentForWallpaper(seed, isDark = false).onSurface
        }
        assertTrue(
            "dark theme ink should be lighter than light theme ink",
            darkInk.luminance() > lightInk.luminance() + 0.08f,
        )
    }

    private fun colorSpread(c: Color): Float =
        maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue)
}
