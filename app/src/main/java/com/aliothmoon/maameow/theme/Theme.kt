package com.aliothmoon.maameow.theme

import android.os.Build
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalContext
import com.aliothmoon.maameow.data.preferences.AppSettingsManager

private val LightBackground = Color(0xFFF5F2ED)
private val LightSurface = Color(0xFFF9F7F3)
private val LightSurfaceVariant = Color(0xFFE8E4DE)
private val LightOnSurface = Color(0xFF1C1B18)
private val LightOnSurfaceVariant = Color(0xFF8A8580)
private val LightOutline = Color(0xFFC9C4BE)

// 与浅色同一暖色相（约 36°）的炭灰，切换明暗不换性格
private val DarkBackground = Color(0xFF151312)
private val DarkSurface = Color(0xFF1E1C1A)
private val DarkSurfaceVariant = Color(0xFF2D2B28)
private val DarkOnSurface = Color(0xFFF3F1ED)
private val DarkOnSurfaceVariant = Color(0xFFA19C97)
private val DarkOutline = Color(0xFF3C3A37)

private val PureDarkBackground = Color(0xFF000000)
private val PureDarkSurface = Color(0xFF000000)
private val PureDarkSurfaceVariant = Color(0xFF141311)

// 不补全会回退成 M3 默认紫色；Lowest → Highest 由亮到暗（深色反之）
private class SurfaceTones(
    val lowest: Color,
    val low: Color,
    val container: Color,
    val high: Color,
    val highest: Color,
    val dim: Color,
    val bright: Color,
)

private val LightTones = SurfaceTones(
    lowest = Color(0xFFFFFFFF),
    low = LightSurface,
    container = Color(0xFFF2EFEA),
    high = Color(0xFFEDE9E4),
    highest = LightSurfaceVariant,
    dim = Color(0xFFE3DFD9),
    bright = LightSurface,
)

private val DarkTones = SurfaceTones(
    lowest = Color(0xFF0F0E0D),
    low = DarkSurface,
    container = Color(0xFF23211F),
    high = Color(0xFF282624),
    highest = DarkSurfaceVariant,
    dim = DarkBackground,
    bright = DarkOutline,
)

private val PureDarkTones = SurfaceTones(
    lowest = Color(0xFF000000),
    low = Color(0xFF0B0A09),
    container = PureDarkSurfaceVariant,
    high = Color(0xFF1B1A18),
    highest = Color(0xFF23211F),
    dim = Color(0xFF000000),
    bright = DarkSurfaceVariant,
)


private fun createLightColorScheme(
    primary: Color, primaryContainer: Color, onPrimaryContainer: Color, inversePrimary: Color
): ColorScheme {
    val tones = LightTones
    return lightColorScheme(
        primary = primary,
        onPrimary = Color(0xFFFFFFFF),
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
        onErrorContainer = Color(0xFF690005),
        // 默认 primary 会把 tonalElevation 表面染蓝；不用 Transparent，M3 按 tint.copy(alpha) 叠加会压暗
        surfaceTint = LightSurface,
        inversePrimary = inversePrimary,
        inverseSurface = Color(0xFF31302C),
        inverseOnSurface = Color(0xFFF4F1EC),
        surfaceBright = tones.bright,
        surfaceDim = tones.dim,
        surfaceContainerLowest = tones.lowest,
        surfaceContainerLow = tones.low,
        surfaceContainer = tones.container,
        surfaceContainerHigh = tones.high,
        surfaceContainerHighest = tones.highest,
    )
}

private fun createDarkColorScheme(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    inversePrimary: Color,
    isPureDark: Boolean = false,
): ColorScheme {
    val bg = if (isPureDark) PureDarkBackground else DarkBackground
    val surface = if (isPureDark) PureDarkSurface else DarkSurface
    val surfaceVariant = if (isPureDark) PureDarkSurfaceVariant else DarkSurfaceVariant
    val tones = if (isPureDark) PureDarkTones else DarkTones

    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = DarkOnSurfaceVariant,
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = DarkSurfaceVariant,
        onSecondaryContainer = Color(0xFFEAE6E0),
        tertiary = primary.copy(alpha = 0.8f),
        onTertiary = onPrimary,
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
        onErrorContainer = Color(0xFFFFDAD6),
        surfaceTint = surface,
        inversePrimary = inversePrimary,
        inverseSurface = LightSurfaceVariant,
        inverseOnSurface = DarkSurface,
        surfaceBright = tones.bright,
        surfaceDim = tones.dim,
        surfaceContainerLowest = tones.lowest,
        surfaceContainerLow = tones.low,
        surfaceContainer = tones.container,
        surfaceContainerHigh = tones.high,
        surfaceContainerHighest = tones.highest,
    )
}

private val BluePrimary = Color(0xFF2B6BCA)

// 深色把主色提亮，按钮文字随之换深色
private val BluePrimaryDark = Color(0xFF6D9DE3)

private val BlueLight = createLightColorScheme(
    primary = BluePrimary,
    primaryContainer = Color(0xFFE5ECF5),
    onPrimaryContainer = Color(0xFF002453),
    inversePrimary = BluePrimaryDark,
)

private val BlueDark = createDarkColorScheme(
    primary = BluePrimaryDark,
    onPrimary = Color(0xFF071E40),
    primaryContainer = Color(0xFF224577),
    onPrimaryContainer = Color(0xFFD6E8FF),
    inversePrimary = BluePrimary,
)

private val BluePureDark = createDarkColorScheme(
    primary = BluePrimaryDark,
    onPrimary = Color(0xFF071E40),
    primaryContainer = Color(0xFF224577),
    onPrimaryContainer = Color(0xFFD6E8FF),
    inversePrimary = BluePrimary,
    isPureDark = true,
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
    const val DISABLED = 0.38f
    const val SECONDARY = 0.60f
    const val MEDIUM = 0.74f
}

/**
 * 保存启用玻璃背景前的「原始不透明」ColorScheme，供 [OpaqueTheme] 在弹窗中恢复。
 * 由 [MaaMeowTheme] 统一下发；未进入 App 主题时为 null。
 */
val LocalOpaqueColorScheme = staticCompositionLocalOf<ColorScheme?> { null }

/** 主界面自定义背景启用时，卡片/表面的默认不透明度（玻璃拟态）。 */
const val GLASS_SURFACE_ALPHA = 0.82f

/**
 * 生成玻璃版配色：背景置透明（露出背景图），各 surface 族加透明度让卡片透出背景，
 * 前景 on* 色保持不透明以保证文字清晰。
 */
fun ColorScheme.toGlass(surfaceAlpha: Float = GLASS_SURFACE_ALPHA): ColorScheme = copy(
    background = Color.Transparent,
    surface = surface.copy(alpha = surfaceAlpha),
    surfaceVariant = surfaceVariant.copy(alpha = surfaceAlpha),
    surfaceBright = surfaceBright.copy(alpha = surfaceAlpha),
    surfaceDim = surfaceDim.copy(alpha = surfaceAlpha),
    surfaceContainer = surfaceContainer.copy(alpha = surfaceAlpha),
    surfaceContainerLowest = surfaceContainerLowest.copy(alpha = surfaceAlpha),
    surfaceContainerLow = surfaceContainerLow.copy(alpha = surfaceAlpha),
    surfaceContainerHigh = surfaceContainerHigh.copy(alpha = surfaceAlpha),
    surfaceContainerHighest = surfaceContainerHighest.copy(alpha = surfaceAlpha),
)

/**
 * 在玻璃背景作用域内恢复不透明配色的包装器。
 *
 * 用于弹窗（[com.aliothmoon.maameow.presentation.components.AdaptiveTaskPromptDialog] 等）——
 * 它们在自身独立窗口内呈现，不应透出主界面背景图。若当前不处于玻璃作用域（[LocalOpaqueColorScheme] 为
 * 空或与当前配色一致），则为无副作用的透传。
 */
@Composable
fun OpaqueTheme(content: @Composable () -> Unit) {
    val opaque = LocalOpaqueColorScheme.current
    if (opaque == null || opaque === MaterialTheme.colorScheme) {
        content()
    } else {
        ProvideColorScheme(opaque, content)
    }
}

/**
 * 以指定配色应用 MaterialTheme（沿用当前排版与形状），并把内容色同步为 onSurface。
 * 玻璃配色（[toGlass]）与弹窗恢复不透明配色（[OpaqueTheme]）共用此包装。
 */
@Composable
fun ProvideColorScheme(scheme: ColorScheme, content: @Composable () -> Unit) {
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes) {
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
    }
}

@Composable
fun MaaMeowTheme(
    themeMode: AppSettingsManager.ThemeMode = AppSettingsManager.ThemeMode.SYSTEM,
    useSystemMonetColor: Boolean = true,
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
    val colorScheme: ColorScheme = remember(themeMode, useSystemMonetColor, isDarkTheme, context) {
        when {
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

    val reduceMotion = remember(context) { MaaMotion.reduceMotion(context) }
    CompositionLocalProvider(
        LocalIndication provides NoIndication,
        LocalOpaqueColorScheme provides colorScheme,
        LocalReduceMotion provides reduceMotion,
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
