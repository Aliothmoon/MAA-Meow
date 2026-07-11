package com.aliothmoon.maameow.theme

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.platform.LocalContext
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val LightBackground = Color(0xFFF5F2ED)
private val LightSurface = Color(0xFFF9F7F3)
private val LightSurfaceVariant = Color(0xFFE8E4DE)
private val LightOnSurface = Color(0xFF1C1B18)
private val LightOnSurfaceVariant = Color(0xFF8A8580)
private val LightOutline = Color(0xFFC9C4BE)

private val DarkBackground = Color(0xFF121212)
private val DarkSurface = Color(0xFF1C1C1E)
private val DarkSurfaceVariant = Color(0xFF2C2C2E)
private val DarkOnSurface = Color(0xFFFFFFFF)
private val DarkOnSurfaceVariant = Color(0xFF98989D)
private val DarkOutline = Color(0xFF3A3A3C)

private val PureDarkBackground = Color(0xFF000000)
private val PureDarkSurface = Color(0xFF000000)
private val PureDarkSurfaceVariant = Color(0xFF121212)


private fun createLightColorScheme(
    primary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    onPrimary: Color = Color.White,
): ColorScheme {
    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = Color(0xFF8A8580),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE8E4DE),
        onSecondaryContainer = Color(0xFF1C1B18),
        tertiary = primary.copy(alpha = 0.8f),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = primaryContainer.copy(alpha = 0.5f),
        onTertiaryContainer = onPrimaryContainer,
        background = LightBackground,
        onBackground = LightOnSurface,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightOnSurfaceVariant,
        outline = LightOutline,
        outlineVariant = LightSurfaceVariant,
        error = Color(0xfff53f3f),
        onError = Color.White,
        errorContainer = Color(0xFFFFD8D6),
        onErrorContainer = Color(0xFF690005)
    )
}

private fun createDarkColorScheme(
    primary: Color,
    primaryContainer: Color,
    onPrimary: Color = primary.contrastingContentColor(),
    onPrimaryContainer: Color,
    isPureDark: Boolean = false,
): ColorScheme {
    val bg = if (isPureDark) PureDarkBackground else DarkBackground
    val surface = if (isPureDark) PureDarkSurface else DarkSurface
    val surfaceVariant = if (isPureDark) PureDarkSurfaceVariant else DarkSurfaceVariant

    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = Color(0xFF98989D),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFF2C2C2E),
        onSecondaryContainer = Color(0xFFE5E5EA),
        tertiary = primary.copy(alpha = 0.8f),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = primaryContainer.copy(alpha = 0.5f),
        onTertiaryContainer = onPrimaryContainer,
        background = bg,
        onBackground = DarkOnSurface,
        surface = surface,
        onSurface = DarkOnSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        outline = DarkOutline,
        outlineVariant = surfaceVariant,
        error = Color(0xFFFF453A),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6)
    )
}

private val BlueLight = createLightColorScheme(
    primary = Color(0xFF2B6BCA),
    primaryContainer = Color(0xFFE5F1FF),
    onPrimaryContainer = Color(0xFF002453)
)

private val BlueDark = createDarkColorScheme(
    primary = Color(0xFF2B6BCA),
    primaryContainer = Color(0xFF004088),
    onPrimaryContainer = Color(0xFFD6E8FF)
)

private val BluePureDark = createDarkColorScheme(
    primary = Color(0xFF2B6BCA),
    primaryContainer = Color(0xFF004088),
    onPrimaryContainer = Color(0xFFD6E8FF),
    isPureDark = true
)

val MaaShapes = Shapes(
    extraSmall = RoundedCornerShape(MaaDesignTokens.CornerRadius.inner),
    small = RoundedCornerShape(MaaDesignTokens.CornerRadius.button),
    medium = RoundedCornerShape(MaaDesignTokens.CornerRadius.card),
    large = RoundedCornerShape(MaaDesignTokens.CornerRadius.card),
    extraLarge = RoundedCornerShape(MaaDesignTokens.CornerRadius.pill)
)


private object NoIndication : IndicationNodeFactory {
    private class NoIndicationNode : Modifier.Node(), DrawModifierNode {
        override fun ContentDrawScope.draw() {
            drawContent()
        }
    }

    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return NoIndicationNode()
    }

    override fun hashCode(): Int = -1

    override fun equals(other: Any?): Boolean = other === this
}

object MaaThemeAlphas {
    const val Disabled = 0.38f
    const val Secondary = 0.60f
    const val Medium = 0.74f
}

val LocalCardOpacity = staticCompositionLocalOf { 1f }
val LocalControlOpacity = staticCompositionLocalOf { 1f }

@Composable
fun MaaMeowTheme(
    themeMode: AppSettingsManager.ThemeMode = AppSettingsManager.ThemeMode.SYSTEM,
    useSystemMonetColor: Boolean = true,
    customWallpaperPath: String = "",
    cardOpacity: Float = 1f,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val isDarkTheme = when (themeMode) {
        AppSettingsManager.ThemeMode.SYSTEM -> systemDarkTheme
        AppSettingsManager.ThemeMode.WHITE -> false
        AppSettingsManager.ThemeMode.DARK, AppSettingsManager.ThemeMode.PURE_DARK -> true
    }
    val isPureDark = themeMode == AppSettingsManager.ThemeMode.PURE_DARK
    val customSeed by produceState<Color?>(null, customWallpaperPath) {
        value = withContext(Dispatchers.IO) { readWallpaperSeedColor(customWallpaperPath) }
    }
    val wallpaperSeed = customSeed
    val colorScheme: ColorScheme = remember(themeMode, useSystemMonetColor, customSeed, isDarkTheme, context) {
        when {
            useSystemMonetColor && wallpaperSeed != null -> createSeedColorScheme(
                seed = wallpaperSeed,
                isDark = isDarkTheme,
                isPureDark = isPureDark,
            )
            // Android 12+ with monet enabled ==> system dynamic color (Material You)
            useSystemMonetColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val dynamic =
                    if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(
                        context
                    )
                // PURE_DARK keeps the monet-tinted primary but forces pure-black surfaces
                if (isPureDark) {
                    dynamic.copy(
                        background = PureDarkBackground,
                        surface = PureDarkSurface,
                        surfaceVariant = PureDarkSurfaceVariant
                    )
                } else dynamic
            }
            // Otherwise fall back to the built-in blue palette
            else -> when (themeMode) {
                AppSettingsManager.ThemeMode.SYSTEM -> if (systemDarkTheme) BlueDark else BlueLight
                AppSettingsManager.ThemeMode.WHITE -> BlueLight
                AppSettingsManager.ThemeMode.DARK -> BlueDark
                AppSettingsManager.ThemeMode.PURE_DARK -> BluePureDark
            }
        }
    }

    CompositionLocalProvider(
        LocalIndication provides NoIndication,
        LocalCardOpacity provides cardOpacity.coerceIn(0.4f, 1f),
        LocalControlOpacity provides (cardOpacity + 0.1f).coerceIn(0.5f, 1f),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = MaaShapes,
        ) {
            ProvideLogPalette(isDark = isDarkTheme, content = content)
        }
    }
}

private fun createSeedColorScheme(seed: Color, isDark: Boolean, isPureDark: Boolean): ColorScheme {
    val primary = seed.ensureReadablePrimary(isDark)
    return if (isDark) {
        createDarkColorScheme(
            primary = primary,
            primaryContainer = primary.darken(0.38f),
            onPrimary = primary.contrastingContentColor(),
            onPrimaryContainer = primary.darken(0.38f).contrastingContentColor(),
            isPureDark = isPureDark,
        )
    } else {
        createLightColorScheme(
            primary = primary,
            primaryContainer = primary.copy(alpha = 0.16f).compositeOver(Color.White),
            onPrimaryContainer = primary.darken(0.45f),
            onPrimary = primary.contrastingContentColor(),
        )
    }
}

private fun readWallpaperSeedColor(path: String): Color? {
    if (path.isBlank()) return null
    val file = File(path)
    if (!file.isFile) return null
    val options = BitmapFactory.Options().apply { inSampleSize = 16 }
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
    return try {
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        val stepX = (bitmap.width / 24).coerceAtLeast(1)
        val stepY = (bitmap.height / 24).coerceAtLeast(1)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                val saturation = maxOf(r, g, b) - minOf(r, g, b)
                if (saturation > 16) {
                    red += r
                    green += g
                    blue += b
                    count++
                }
                x += stepX
            }
            y += stepY
        }
        if (count == 0L) null else Color(
            red = (red / count).toInt(),
            green = (green / count).toInt(),
            blue = (blue / count).toInt(),
        )
    } finally {
        bitmap.recycle()
    }
}

private fun Color.ensureReadablePrimary(isDark: Boolean): Color = if (isDark) lighten(0.24f) else darken(0.18f)

private fun Color.contrastingContentColor(): Color {
    val luminance = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    return if (luminance > 0.46f) Color.Black else Color.White
}

private fun Color.lighten(amount: Float): Color = mix(Color.White, amount)

private fun Color.darken(amount: Float): Color = mix(Color.Black, amount)

private fun Color.mix(target: Color, amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (target.red - red) * a,
        green = green + (target.green - green) * a,
        blue = blue + (target.blue - blue) * a,
        alpha = alpha,
    )
}

private fun Color.compositeOver(background: Color): Color {
    val outAlpha = alpha + background.alpha * (1f - alpha)
    if (outAlpha <= 0f) return Color.Transparent
    return Color(
        red = (red * alpha + background.red * background.alpha * (1f - alpha)) / outAlpha,
        green = (green * alpha + background.green * background.alpha * (1f - alpha)) / outAlpha,
        blue = (blue * alpha + background.blue * background.alpha * (1f - alpha)) / outAlpha,
        alpha = outAlpha,
    )
}
