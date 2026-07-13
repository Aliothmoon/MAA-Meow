package com.aliothmoon.maameow.data.preferences

import com.aliothmoon.maameow.domain.models.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Documents export/import wallpaper policy without spinning DataStore:
 * export must strip device-local wallpaper fields; import must be able to
 * re-apply local values over a sanitized payload.
 *
 * Keep this list in sync with ConfigBackupManager.AppSettings.sanitized() and
 * AppSettingsManager.setSettingsPreservingWallpaper.
 */
class WallpaperBackupPolicyTest {

    /** Full wallpaper-related field set that must never travel with backups. */
    private val wallpaperFieldDefaults = mapOf(
        "wallpaperUri" to "",
        "wallpaperAlpha" to "100",
        "wallpaperBlur" to "0",
        "wallpaperTextContrast" to "false",
        "wallpaperFrostedGlass" to "false",
        "useWallpaperColor" to "false",
    )

    @Test
    fun sanitizedExportShape_clearsAllWallpaperFieldsIncludingTextContrast() {
        val local = AppSettings(
            wallpaperUri = "file:///data/user/0/x/files/wallpaper/wallpaper.jpg",
            wallpaperAlpha = "70",
            wallpaperBlur = "40",
            wallpaperTextContrast = "true",
            wallpaperFrostedGlass = "true",
            useWallpaperColor = "true",
            mirrorChyanCdk = "secret",
        )
        // Mirrors ConfigBackupManager.AppSettings.sanitized()
        val exported = local.copy(
            mirrorChyanCdk = "",
            wallpaperUri = wallpaperFieldDefaults.getValue("wallpaperUri"),
            wallpaperAlpha = wallpaperFieldDefaults.getValue("wallpaperAlpha"),
            wallpaperBlur = wallpaperFieldDefaults.getValue("wallpaperBlur"),
            wallpaperTextContrast = wallpaperFieldDefaults.getValue("wallpaperTextContrast"),
            wallpaperFrostedGlass = wallpaperFieldDefaults.getValue("wallpaperFrostedGlass"),
            useWallpaperColor = wallpaperFieldDefaults.getValue("useWallpaperColor"),
        )
        assertEquals("", exported.wallpaperUri)
        assertEquals("100", exported.wallpaperAlpha)
        assertEquals("0", exported.wallpaperBlur)
        assertEquals("false", exported.wallpaperTextContrast)
        assertEquals("false", exported.wallpaperFrostedGlass)
        assertEquals("false", exported.useWallpaperColor)
        assertEquals("", exported.mirrorChyanCdk)
    }

    @Test
    fun importPreservingWallpaper_keepsLocalOverBackupIncludingTextContrast() {
        val local = AppSettings(
            wallpaperUri = "file:///local/wallpaper.jpg",
            wallpaperAlpha = "80",
            wallpaperBlur = "25",
            wallpaperTextContrast = "true",
            wallpaperFrostedGlass = "true",
            useWallpaperColor = "true",
            fontSizeScale = "100",
        )
        val fromBackup = AppSettings(
            wallpaperUri = "file:///other-device/wallpaper.jpg",
            wallpaperAlpha = "10",
            wallpaperBlur = "100",
            wallpaperTextContrast = "false",
            wallpaperFrostedGlass = "false",
            useWallpaperColor = "false",
            fontSizeScale = "90",
        )
        // Mirrors setSettingsPreservingWallpaper
        val merged = fromBackup.copy(
            wallpaperUri = local.wallpaperUri,
            wallpaperAlpha = local.wallpaperAlpha,
            wallpaperBlur = local.wallpaperBlur,
            wallpaperTextContrast = local.wallpaperTextContrast,
            wallpaperFrostedGlass = local.wallpaperFrostedGlass,
            useWallpaperColor = local.useWallpaperColor,
        )
        assertEquals(local.wallpaperUri, merged.wallpaperUri)
        assertEquals(local.wallpaperAlpha, merged.wallpaperAlpha)
        assertEquals(local.wallpaperBlur, merged.wallpaperBlur)
        assertEquals(local.wallpaperTextContrast, merged.wallpaperTextContrast)
        assertEquals(local.wallpaperFrostedGlass, merged.wallpaperFrostedGlass)
        assertEquals(local.useWallpaperColor, merged.useWallpaperColor)
        assertEquals("90", merged.fontSizeScale)
        assertFalse(merged.wallpaperUri.contains("other-device"))
    }
}
