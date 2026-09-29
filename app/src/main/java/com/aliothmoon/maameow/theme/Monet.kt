package com.aliothmoon.maameow.theme

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.Score

/**
 * 原生莫奈取色：用 Material Color Utilities（与系统动态取色同一套算法）从背景图提取种子色，
 * 生成整套 [ColorScheme]。
 *
 * 图片过小或无法取色时返回 null，由调用方回退到原配色。
 */
@SuppressLint("RestrictedApi")
fun monetColorScheme(image: ImageBitmap, dark: Boolean): ColorScheme? {
    val seed = extractSeedColor(image) ?: return null
    return SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0).toComposeColorScheme()
}

/** 取色用的采样边长上限；先降采样到该量级再取像素，避免高分辨率壁纸产生大块临时分配。 */
private const val TARGET_SAMPLE_SIDE = 64

/** 降采样到约 [TARGET_SAMPLE_SIDE] 像素量级后量化取种子色。 */
@SuppressLint("RestrictedApi")
private fun extractSeedColor(image: ImageBitmap): Int? {
    val source = image.asAndroidBitmap()
    val width = source.width
    val height = source.height
    if (width <= 0 || height <= 0) return null
    return runCatching {
        val longSide = maxOf(width, height)
        val scaled = if (longSide > TARGET_SAMPLE_SIDE) {
            val ratio = TARGET_SAMPLE_SIDE.toFloat() / longSide
            Bitmap.createScaledBitmap(
                source,
                (width * ratio).toInt().coerceAtLeast(1),
                (height * ratio).toInt().coerceAtLeast(1),
                false,
            )
        } else {
            null
        }
        try {
            val sample = scaled ?: source
            val buffer = IntArray(sample.width * sample.height)
            sample.getPixels(buffer, 0, sample.width, 0, 0, sample.width, sample.height)
            if (buffer.isEmpty()) return null
            val quantized = QuantizerCelebi.quantize(buffer, 128)
            Score.score(quantized).firstOrNull()
        } finally {
            // 只回收自己创建的缩放副本，源位图归 ImageBitmap 所有。
            scaled?.recycle()
        }
    }.getOrNull()
}

/** 把 MCU 动态方案映射为 Compose 配色。 */
@SuppressLint("RestrictedApi")
private fun DynamicScheme.toComposeColorScheme(): ColorScheme {
    fun color(value: Int) = Color(value)
    return (if (isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = color(primary),
        onPrimary = color(onPrimary),
        primaryContainer = color(primaryContainer),
        onPrimaryContainer = color(onPrimaryContainer),
        secondary = color(secondary),
        onSecondary = color(onSecondary),
        secondaryContainer = color(secondaryContainer),
        onSecondaryContainer = color(onSecondaryContainer),
        tertiary = color(tertiary),
        onTertiary = color(onTertiary),
        tertiaryContainer = color(tertiaryContainer),
        onTertiaryContainer = color(onTertiaryContainer),
        error = color(error),
        onError = color(onError),
        errorContainer = color(errorContainer),
        onErrorContainer = color(onErrorContainer),
        background = color(background),
        onBackground = color(onBackground),
        surface = color(surface),
        onSurface = color(onSurface),
        surfaceVariant = color(surfaceVariant),
        onSurfaceVariant = color(onSurfaceVariant),
        surfaceTint = color(primary),
        inverseSurface = color(inverseSurface),
        inverseOnSurface = color(inverseOnSurface),
        inversePrimary = color(inversePrimary),
        outline = color(outline),
        outlineVariant = color(outlineVariant),
        scrim = color(scrim),
        surfaceBright = color(surfaceBright),
        surfaceDim = color(surfaceDim),
        surfaceContainer = color(surfaceContainer),
        surfaceContainerHigh = color(surfaceContainerHigh),
        surfaceContainerHighest = color(surfaceContainerHighest),
        surfaceContainerLow = color(surfaceContainerLow),
        surfaceContainerLowest = color(surfaceContainerLowest),
    )
}
