package com.aliothmoon.maameow.theme

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color as ComposeColor
import com.materialkolor.hct.Hct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.score.Score

/**
 * Extracts a seed color from wallpaper bitmaps and builds a Material You
 * ColorScheme using the official Material Color Utilities algorithms
 * (via the maintained materialkolor JVM packaging).
 */
object WallpaperColorScheme {

    private const val GOOGLE_BLUE_ARGB = 0xFF1B6EF3.toInt()
    private const val QUANTIZE_MAX_COLORS = 128
    private const val SAMPLE_TARGET = 128

    fun extractSeedColor(bitmap: Bitmap): Int {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return GOOGLE_BLUE_ARGB
        }
        val maxDim = maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
        val sampleSize = (maxDim / SAMPLE_TARGET).coerceAtLeast(1)
        val w = (bitmap.width / sampleSize).coerceAtLeast(1)
        val h = (bitmap.height / sampleSize).coerceAtLeast(1)
        val scaled = if (w == bitmap.width && h == bitmap.height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, w, h, false)
        }
        return try {
            val pixels = IntArray(w * h)
            scaled.getPixels(pixels, 0, w, 0, 0, w, h)
            val quantized = QuantizerCelebi.quantize(pixels, QUANTIZE_MAX_COLORS)
            if (quantized.isEmpty()) {
                GOOGLE_BLUE_ARGB
            } else {
                // Explicit desired count; default helper can be brittle across MCU packaging.
                // Positional args match MCU Score.score(map, desired, fallback, filter).
                val ranked = Score.score(quantized, 4, GOOGLE_BLUE_ARGB, true)
                ranked.firstOrNull() ?: GOOGLE_BLUE_ARGB
            }
        } catch (_: Exception) {
            GOOGLE_BLUE_ARGB
        } finally {
            if (scaled !== bitmap && !scaled.isRecycled) {
                scaled.recycle()
            }
        }
    }

    fun generateColorScheme(seedArgb: Int, isDark: Boolean): ColorScheme {
        val source = Hct.fromInt(seedArgb)
        // contrastLevel 0.0 matches the Material You default contrast.
        val scheme = SchemeTonalSpot(source, isDark, 0.0)
        return toComposeColorScheme(scheme, isDark)
    }

    /**
     * Rewrite content/text roles from a wallpaper seed. Surfaces/primary stay.
     *
     * Built-in theme body ink is ~tone 10 (light) / ~100 (dark). Dynamic text uses
     * a **high-contrast** colored ink (hue from seed) so body text stays readable
     * on wallpaper/cards while still carrying wallpaper tint:
     * - **Dark theme**: ink **90**, muted **82**
     * - **Light theme**: ink **18**, muted **28**
     *
     * Does not touch [onSurfaceVariant] (hints) or filled-button on-colors
     * ([onPrimary] etc.).
     *
     * @param isDark true for dark / pure-dark theme; false for light theme.
     */
    fun ColorScheme.adaptContentForWallpaper(seedArgb: Int, isDark: Boolean): ColorScheme {
        val seedHct = Hct.fromInt(seedArgb)
        // Extreme tones for readability; keep seed hue (not pure black/white).
        val inkTone = if (isDark) 90.0 else 18.0
        val mutedTone = if (isDark) 82.0 else 28.0
        val inkChromaScale = 0.65
        val mutedChromaScale = 0.45
        val ink = ComposeColor(
            Hct.from(
                seedHct.hue,
                (seedHct.chroma * inkChromaScale).coerceIn(20.0, 48.0),
                inkTone,
            ).toInt()
        )
        val muted = ComposeColor(
            Hct.from(
                seedHct.hue,
                (seedHct.chroma * mutedChromaScale).coerceIn(14.0, 32.0),
                mutedTone,
            ).toInt()
        )
        return copy(
            onBackground = ink,
            onSurface = ink,
            onPrimaryContainer = muted,
            onSecondaryContainer = muted,
            onTertiaryContainer = muted,
            inverseOnSurface = muted,
        )
    }

    private fun toComposeColorScheme(scheme: SchemeTonalSpot, isDark: Boolean): ColorScheme {
        fun c(argb: Int): ComposeColor = ComposeColor(argb)
        return if (isDark) {
            darkColorScheme(
                primary = c(scheme.primary),
                onPrimary = c(scheme.onPrimary),
                primaryContainer = c(scheme.primaryContainer),
                onPrimaryContainer = c(scheme.onPrimaryContainer),
                inversePrimary = c(scheme.inversePrimary),
                secondary = c(scheme.secondary),
                onSecondary = c(scheme.onSecondary),
                secondaryContainer = c(scheme.secondaryContainer),
                onSecondaryContainer = c(scheme.onSecondaryContainer),
                tertiary = c(scheme.tertiary),
                onTertiary = c(scheme.onTertiary),
                tertiaryContainer = c(scheme.tertiaryContainer),
                onTertiaryContainer = c(scheme.onTertiaryContainer),
                background = c(scheme.background),
                onBackground = c(scheme.onBackground),
                surface = c(scheme.surface),
                onSurface = c(scheme.onSurface),
                surfaceVariant = c(scheme.surfaceVariant),
                onSurfaceVariant = c(scheme.onSurfaceVariant),
                surfaceTint = c(scheme.surfaceTint),
                inverseSurface = c(scheme.inverseSurface),
                inverseOnSurface = c(scheme.inverseOnSurface),
                error = c(scheme.error),
                onError = c(scheme.onError),
                errorContainer = c(scheme.errorContainer),
                onErrorContainer = c(scheme.onErrorContainer),
                outline = c(scheme.outline),
                outlineVariant = c(scheme.outlineVariant),
                scrim = c(scheme.scrim),
                surfaceBright = c(scheme.surfaceBright),
                surfaceDim = c(scheme.surfaceDim),
                surfaceContainer = c(scheme.surfaceContainer),
                surfaceContainerHigh = c(scheme.surfaceContainerHigh),
                surfaceContainerHighest = c(scheme.surfaceContainerHighest),
                surfaceContainerLow = c(scheme.surfaceContainerLow),
                surfaceContainerLowest = c(scheme.surfaceContainerLowest),
            )
        } else {
            lightColorScheme(
                primary = c(scheme.primary),
                onPrimary = c(scheme.onPrimary),
                primaryContainer = c(scheme.primaryContainer),
                onPrimaryContainer = c(scheme.onPrimaryContainer),
                inversePrimary = c(scheme.inversePrimary),
                secondary = c(scheme.secondary),
                onSecondary = c(scheme.onSecondary),
                secondaryContainer = c(scheme.secondaryContainer),
                onSecondaryContainer = c(scheme.onSecondaryContainer),
                tertiary = c(scheme.tertiary),
                onTertiary = c(scheme.onTertiary),
                tertiaryContainer = c(scheme.tertiaryContainer),
                onTertiaryContainer = c(scheme.onTertiaryContainer),
                background = c(scheme.background),
                onBackground = c(scheme.onBackground),
                surface = c(scheme.surface),
                onSurface = c(scheme.onSurface),
                surfaceVariant = c(scheme.surfaceVariant),
                onSurfaceVariant = c(scheme.onSurfaceVariant),
                surfaceTint = c(scheme.surfaceTint),
                inverseSurface = c(scheme.inverseSurface),
                inverseOnSurface = c(scheme.inverseOnSurface),
                error = c(scheme.error),
                onError = c(scheme.onError),
                errorContainer = c(scheme.errorContainer),
                onErrorContainer = c(scheme.onErrorContainer),
                outline = c(scheme.outline),
                outlineVariant = c(scheme.outlineVariant),
                scrim = c(scheme.scrim),
                surfaceBright = c(scheme.surfaceBright),
                surfaceDim = c(scheme.surfaceDim),
                surfaceContainer = c(scheme.surfaceContainer),
                surfaceContainerHigh = c(scheme.surfaceContainerHigh),
                surfaceContainerHighest = c(scheme.surfaceContainerHighest),
                surfaceContainerLow = c(scheme.surfaceContainerLow),
                surfaceContainerLowest = c(scheme.surfaceContainerLowest),
            )
        }
    }
}
