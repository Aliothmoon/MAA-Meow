package com.aliothmoon.maameow.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.utils.BitmapUtils
import com.aliothmoon.maameow.utils.WallpaperFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Theme for UI trees outside AppNavigation (system overlays, etc.).
 *
 * Applies [MaaMeowTheme] base palette, then overrides with a wallpaper-derived
 * ColorScheme when dynamic color is enabled and a custom wallpaper is set.
 * Seed prefers the on-disk cache written by AppNavigation to avoid re-quantize.
 */
@Composable
fun WallpaperAwareMaterialTheme(
    themeMode: AppSettingsManager.ThemeMode,
    appSettings: AppSettingsManager,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useWallpaperColor by appSettings.useWallpaperColor.collectAsStateWithLifecycle()
    val wallpaperTextContrast by appSettings.wallpaperTextContrast.collectAsStateWithLifecycle()
    val wallpaperUri by appSettings.wallpaperUri.collectAsStateWithLifecycle()
    val wallpaperUpdateVersion by appSettings.wallpaperUpdateVersion.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val isDarkTheme = when (themeMode) {
        AppSettingsManager.ThemeMode.SYSTEM -> systemDark
        AppSettingsManager.ThemeMode.WHITE -> false
        AppSettingsManager.ThemeMode.DARK,
        AppSettingsManager.ThemeMode.PURE_DARK -> true
    }
    var wallpaperSeedArgb by remember { mutableStateOf<Int?>(null) }
    // Match AppNavigation: seed for full dynamic color and/or dynamic text.
    LaunchedEffect(wallpaperUri, useWallpaperColor, wallpaperTextContrast, wallpaperUpdateVersion) {
        if ((!useWallpaperColor && !wallpaperTextContrast) || wallpaperUri.isEmpty()) {
            wallpaperSeedArgb = null
            return@LaunchedEffect
        }
        val requestUri = wallpaperUri
        val requestVersion = wallpaperUpdateVersion
        val seed = withContext(Dispatchers.Default) {
            val store = WallpaperFileStore(context)
            val identity = store.wallpaperIdentity(requestUri)
            store.loadCachedSeed(identity)?.let { return@withContext it }
            val source = BitmapUtils.loadDownsampledBitmap(context, requestUri, maxDimension = 512)
                ?: return@withContext null
            try {
                WallpaperColorScheme.extractSeedColor(source).also { extracted ->
                    store.saveCachedSeed(identity, extracted)
                }
            } finally {
                if (!source.isRecycled) source.recycle()
            }
        }
        if (requestUri == wallpaperUri && requestVersion == wallpaperUpdateVersion) {
            wallpaperSeedArgb = seed
        }
    }

    val isPureDark = themeMode == AppSettingsManager.ThemeMode.PURE_DARK

    // Outer theme: system Monet when switch is on and there is no custom wallpaper
    // seed path — or PURE_DARK (never uses custom wallpaper MCU; keep system accents).
    val applySystemDynamicColor = useWallpaperColor && (
        wallpaperUri.isEmpty() || isPureDark
    )
    MaaMeowTheme(
        themeMode = themeMode,
        useWallpaperColor = applySystemDynamicColor,
    ) {
        val seed = wallpaperSeedArgb
        // Align with AppNavigation: full MCU when dynamic color on; text adapt
        // only when wallpaperTextContrast is on (light/dark tones via isDarkTheme).
        // PURE_DARK never overrides with wallpaper seed (surfaces stay pure black via outer theme).
        val overrideScheme = when {
            isPureDark || seed == null -> null
            useWallpaperColor -> {
                val scheme = WallpaperColorScheme.generateColorScheme(seed, isDarkTheme)
                if (wallpaperTextContrast) {
                    with(WallpaperColorScheme) {
                        scheme.adaptContentForWallpaper(seed, isDarkTheme)
                    }
                } else {
                    scheme
                }
            }
            wallpaperTextContrast -> {
                with(WallpaperColorScheme) {
                    MaterialTheme.colorScheme.adaptContentForWallpaper(seed, isDarkTheme)
                }
            }
            else -> null
        }
        if (overrideScheme != null) {
            MaterialTheme(
                colorScheme = overrideScheme,
                typography = MaterialTheme.typography,
                shapes = MaterialTheme.shapes,
                content = content,
            )
        } else {
            content()
        }
    }
}
