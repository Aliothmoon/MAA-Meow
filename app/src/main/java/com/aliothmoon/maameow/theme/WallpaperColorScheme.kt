package com.aliothmoon.maameow.theme

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color as ComposeColor
import kotlin.math.*

/**
 * Extracts a dominant seed color from a wallpaper bitmap and generates
 * a Material You-style color scheme, without any external library.
 *
 * - Score by hue-area-proportion + chroma
 * - De-duplicate by hue distance
 *
 * Color scheme generation uses HSL-based tonal palette derivation.
 */
object WallpaperColorScheme {

    private const val MIN_CHROMA = 0.10 // HSL saturation threshold
    private const val GOOGLE_BLUE_ARGB = 0xFF1B6EF3.toInt()

    fun extractSeedColor(bitmap: Bitmap): Int {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        val sampleSize = (maxDim / 128).coerceAtLeast(1)
        val w = (bitmap.width / sampleSize).coerceAtLeast(1)
        val h = (bitmap.height / sampleSize).coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, false)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== bitmap) scaled.recycle()

        // Quantize colors and count
        val colorCounts = HashMap<Int, Int>()
        for (p in pixels) {
            val q = Color.rgb(
                Color.red(p) and 0xF8, // 3-bit quantization
                Color.green(p) and 0xF8,
                Color.blue(p) and 0xF8
            )
            colorCounts[q] = (colorCounts[q] ?: 0) + 1
        }

        val total = pixels.size.toDouble()

        // Build hue histogram and collect color info
        data class CInfo(val argb: Int, val hue: Float, val sat: Float, val light: Float, val proportion: Double)

        val hueProportions = DoubleArray(360)
        val infos = ArrayList<CInfo>()
        for ((argb, count) in colorCounts) {
            val hsv = FloatArray(3)
            Color.colorToHSV(argb, hsv)
            val sat = hsv[1]
            val light = hsv[2]
            if (sat < MIN_CHROMA) continue
            val proportion = count / total
            val hue = ((hsv[0].toInt() % 360) + 360) % 360
            infos.add(CInfo(argb, hsv[0], sat, light, proportion))
            hueProportions[hue] += proportion
        }

        if (infos.isEmpty()) return GOOGLE_BLUE_ARGB

        // Score: area-weighted hue density + chroma bonus
        fun hueArea(hue: Float): Double {
            var sum = 0.0
            for (i in -15..15) {
                val idx = (((hue.toInt() + i) % 360) + 360) % 360
                sum += hueProportions[idx]
            }
            return sum
        }
        fun score(c: CInfo): Double = 0.8 * 100.0 * hueArea(c.hue) + 0.2 * 100.0 * (c.sat - 0.48).coerceAtLeast(0.0)

        val sorted = infos.sortedByDescending { score(it) }

        // Pick top color with hue distinctness
        val picked = mutableListOf<CInfo>()
        for (threshold in 90 downTo 15) {
            picked.clear()
            for (info in sorted) {
                if (picked.any { hueDiff(info.hue.toDouble(), it.hue.toDouble()) < threshold }) continue
                picked.add(info)
                if (picked.size >= 4) break
            }
            if (picked.isNotEmpty()) break
        }
        return picked.firstOrNull()?.argb ?: GOOGLE_BLUE_ARGB
    }

    private fun hueDiff(a: Double, b: Double): Double {
        val d = abs(a - b)
        return if (d > 180.0) 360.0 - d else d
    }

    /**
     * Generate a Material You-style Compose ColorScheme from a seed ARGB color.
     *
     * Uses HSL-based tonal palette:
     * - Primary: seed hue, high saturation
     * - Secondary: seed hue, medium saturation
     * - Tertiary: seed hue + 60°, medium saturation
     * - Neutral: seed hue, very low saturation
     * - Error: red hue
     *
     * Each palette produces tones at 10/20/30/40/50/60/70/80/90/95/99/100.
     */
    fun generateColorScheme(seedArgb: Int, isDark: Boolean): androidx.compose.material3.ColorScheme {
        val hsv = FloatArray(3)
        Color.colorToHSV(seedArgb, hsv)
        val seedHue = hsv[0]

        // Tonal palette generator: given hue+sat, produce colors at different lightness levels
        fun tonal(hue: Float, sat: Float, lightness: Int): ComposeColor {
            val l = lightness / 100f
            val adjustedSat = sat * (1f - abs(2f * l - 1f) * 0.3f) // reduce sat at extremes
            return ComposeColor(Color.HSVToColor(floatArrayOf(hue, adjustedSat.coerceIn(0f, 1f), l)))
        }
        // Neutral: AOSP-style chroma ~4 for neutral, ~8 for neutral-var
        // Saturation varies with lightness to approximate perceptual uniformity
        fun neutral(hue: Float, lightness: Int): ComposeColor {
            val l = lightness / 100f
            val sat = 0.02f + abs(2f * l - 1f) * 0.04f  // higher at extremes
            return ComposeColor(Color.HSVToColor(floatArrayOf(hue, sat.coerceIn(0f, 0.06f), l)))
        }
        fun neutralVar(hue: Float, lightness: Int): ComposeColor {
            val l = lightness / 100f
            val sat = 0.03f + abs(2f * l - 1f) * 0.06f  // higher at extremes
            return ComposeColor(Color.HSVToColor(floatArrayOf(hue, sat.coerceIn(0f, 0.10f), l)))
        }

        val primaryHue = seedHue
        val secondaryHue = seedHue
        val tertiaryHue = (seedHue + 60f) % 360f
        val errorHue = 0f

        // Use container-level saturation as the main tone (softer, less heavy)
        // primary itself is very subtle; primaryContainer carries the accent feel
        val primarySat = 0.40f
        val secondarySat = 0.24f
        val tertiarySat = 0.30f
        val errorSat = 0.65f

        return if (isDark) {
            darkColorScheme(
                primary = tonal(primaryHue, primarySat, 80),
                onPrimary = tonal(primaryHue, primarySat, 20),
                primaryContainer = tonal(primaryHue, primarySat, 30),
                onPrimaryContainer = tonal(primaryHue, primarySat, 90),
                secondary = tonal(secondaryHue, secondarySat, 80),
                onSecondary = tonal(secondaryHue, secondarySat, 20),
                secondaryContainer = tonal(secondaryHue, secondarySat, 30),
                onSecondaryContainer = tonal(secondaryHue, secondarySat, 90),
                tertiary = tonal(tertiaryHue, tertiarySat, 80),
                onTertiary = tonal(tertiaryHue, tertiarySat, 20),
                tertiaryContainer = tonal(tertiaryHue, tertiarySat, 30),
                onTertiaryContainer = tonal(tertiaryHue, tertiarySat, 90),
                error = tonal(errorHue, errorSat, 80),
                onError = tonal(errorHue, errorSat, 20),
                errorContainer = tonal(errorHue, errorSat, 30),
                onErrorContainer = tonal(errorHue, errorSat, 90),
                background = neutral(primaryHue, 6),
                onBackground = neutral(primaryHue, 90),
                surface = neutral(primaryHue, 6),
                onSurface = neutral(primaryHue, 90),
                surfaceVariant = neutralVar(primaryHue, 30),
                onSurfaceVariant = neutralVar(primaryHue, 80),
                outline = neutralVar(primaryHue, 60),
                outlineVariant = neutralVar(primaryHue, 30),
                surfaceContainerLowest = neutral(primaryHue, 4),
                surfaceContainerLow = neutral(primaryHue, 10),
                surfaceContainer = neutral(primaryHue, 12),
                surfaceContainerHigh = neutral(primaryHue, 17),
                surfaceContainerHighest = neutral(primaryHue, 22),
                inverseSurface = neutral(primaryHue, 90),
                inverseOnSurface = neutral(primaryHue, 20),
                inversePrimary = tonal(primaryHue, primarySat, 40),
            )
        } else {
            lightColorScheme(
                primary = tonal(primaryHue, primarySat, 40),
                onPrimary = ComposeColor.White,
                primaryContainer = tonal(primaryHue, primarySat, 90),
                onPrimaryContainer = tonal(primaryHue, primarySat, 10),
                secondary = tonal(secondaryHue, secondarySat, 40),
                onSecondary = ComposeColor.White,
                secondaryContainer = tonal(secondaryHue, secondarySat, 90),
                onSecondaryContainer = tonal(secondaryHue, secondarySat, 10),
                tertiary = tonal(tertiaryHue, tertiarySat, 40),
                onTertiary = ComposeColor.White,
                tertiaryContainer = tonal(tertiaryHue, tertiarySat, 90),
                onTertiaryContainer = tonal(tertiaryHue, tertiarySat, 10),
                error = tonal(errorHue, errorSat, 40),
                onError = ComposeColor.White,
                errorContainer = tonal(errorHue, errorSat, 90),
                onErrorContainer = tonal(errorHue, errorSat, 10),
                background = neutral(primaryHue, 98),
                onBackground = neutral(primaryHue, 10),
                surface = neutral(primaryHue, 98),
                onSurface = neutral(primaryHue, 10),
                surfaceVariant = neutralVar(primaryHue, 90),
                onSurfaceVariant = neutralVar(primaryHue, 30),
                outline = neutralVar(primaryHue, 50),
                outlineVariant = neutralVar(primaryHue, 80),
                surfaceContainerLowest = neutral(primaryHue, 100),
                surfaceContainerLow = neutral(primaryHue, 96),
                surfaceContainer = neutral(primaryHue, 94),
                surfaceContainerHigh = neutral(primaryHue, 92),
                surfaceContainerHighest = neutral(primaryHue, 90),
                inverseSurface = neutral(primaryHue, 20),
                inverseOnSurface = neutral(primaryHue, 95),
                inversePrimary = tonal(primaryHue, primarySat, 80),
            )
        }
    }
}