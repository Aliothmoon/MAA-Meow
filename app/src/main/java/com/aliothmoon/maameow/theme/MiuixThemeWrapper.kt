package com.aliothmoon.maameow.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

val LocalThemeMode = compositionLocalOf { AppSettingsManager.ThemeMode.SYSTEM }

/**
 * Miuix-based theme for MAA Meow.
 * Maps existing ThemeMode to miuix ColorSchemeMode and applies MiuixTheme.
 */
@Composable
fun MaaMeowMiuixTheme(
    themeMode: AppSettingsManager.ThemeMode = AppSettingsManager.ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val colorSchemeMode = when (themeMode) {
        AppSettingsManager.ThemeMode.SYSTEM -> ColorSchemeMode.System
        AppSettingsManager.ThemeMode.WHITE -> ColorSchemeMode.Light
        AppSettingsManager.ThemeMode.DARK -> ColorSchemeMode.Dark
        AppSettingsManager.ThemeMode.PURE_DARK -> ColorSchemeMode.Dark
    }

    val controller = ThemeController(
        colorSchemeMode = colorSchemeMode,
        lightColors = MaaMeowLightColors(),
        darkColors = MaaMeowDarkColors(),
    )

    CompositionLocalProvider(
        LocalThemeMode provides themeMode,
    ) {
        MiuixTheme(
            controller = controller,
            content = content
        )
    }
}

/**
 * Light color scheme adapted from MAA Meow's existing palette.
 * Preserves the original blue accent (#2B6BCA) while using miuix defaults.
 */
private fun MaaMeowLightColors() = lightColorScheme(
    primary = Color(0xFF2B6BCA),
    onPrimary = Color.White,
    primaryVariant = Color(0xFF2B6BCA),
    onPrimaryVariant = Color(0xFFAECDFF),
    primaryContainer = Color(0xFF5D9BFF),
    onPrimaryContainer = Color.White,
    background = Color(0xFFF5F2ED),
    onBackground = Color(0xFF1C1B18),
    surface = Color(0xFFF9F7F3),
    onSurface = Color(0xFF1C1B18),
    surfaceVariant = Color.White,
    onSurfaceSecondary = Color(0xCC000000),
    surfaceContainer = Color.White,
    onSurfaceContainer = Color(0xFF1C1B18),
    outline = Color(0xFFC9C4BE),
    dividerLine = Color(0xFFE0E0E0),
)

/**
 * Dark color scheme adapted from MAA Meow's existing palette.
 */
private fun MaaMeowDarkColors() = darkColorScheme(
    primary = Color(0xFF2B6BCA),
    onPrimary = Color.White,
    primaryVariant = Color(0xFF0073DD),
    onPrimaryVariant = Color(0xFF99C7F1),
    primaryContainer = Color(0xFF338FE4),
    onPrimaryContainer = Color.White,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1C1C1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF242424),
    onSurfaceSecondary = Color(0xCCFFFFFF),
    surfaceContainer = Color(0xFF242424),
    onSurfaceContainer = Color(0xE6FFFFFF),
    outline = Color(0xFF3A3A3C),
    dividerLine = Color(0xFF393939),
)
