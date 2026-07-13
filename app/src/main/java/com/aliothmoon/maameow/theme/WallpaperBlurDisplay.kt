package com.aliothmoon.maameow.theme

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.preferences.WallpaperBlur
import com.aliothmoon.maameow.utils.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wallpaper display blur:
 * - API 31+: leave [source] sharp and use [wallpaperBlurModifier] (Compose RenderEffect).
 * - API 28–30: pre-blur a downscaled copy on a background thread (Stack Blur).
 */
@Composable
fun rememberWallpaperDisplayBitmap(
    source: Bitmap?,
    blurPercent: Int,
    softwareMaxDimension: Int = 720,
): ImageBitmap? {
    val useSoftware = WallpaperBlur.needsSoftwareBlur
    val radiusPx = if (useSoftware) {
        WallpaperBlur.percentToSoftwareRadiusPx(blurPercent)
    } else {
        0
    }
    val sharp = remember(source) { source?.asImageBitmap() }

    // Always call remember (Compose rules); only used on software path.
    var soft by remember(source, radiusPx, softwareMaxDimension) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(source, radiusPx, softwareMaxDimension, useSoftware) {
        if (!useSoftware || source == null || radiusPx <= 0) {
            soft = null
            return@LaunchedEffect
        }
        soft = null
        val blurred = withContext(Dispatchers.Default) {
            BitmapUtils.blurForWallpaper(source, radiusPx, softwareMaxDimension)
        }
        soft = blurred.asImageBitmap()
    }

    return when {
        source == null -> null
        useSoftware && radiusPx > 0 -> soft ?: sharp
        else -> sharp
    }
}

/**
 * Apply Compose [Modifier.blur] only when the platform supports it (API 31+).
 * On older APIs this is always [Modifier] — blur is baked into the bitmap instead.
 */
fun wallpaperBlurModifier(blurPercent: Int): Modifier {
    if (WallpaperBlur.needsSoftwareBlur) return Modifier
    val radius = WallpaperBlur.percentToRadiusDp(blurPercent)
    return if (radius > 0f) Modifier.blur(radius.dp) else Modifier
}
