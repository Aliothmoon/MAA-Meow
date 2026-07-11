package com.aliothmoon.maameow.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

private const val CUSTOM_WALLPAPER_PREFIX = "custom_wallpaper_"
private const val MAX_WALLPAPER_SIDE = 2400

fun decodeCustomWallpaper(path: String): Bitmap? {
    if (path.isBlank()) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (
        bounds.outWidth / sampleSize > MAX_WALLPAPER_SIDE ||
        bounds.outHeight / sampleSize > MAX_WALLPAPER_SIDE
    ) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    )
}

fun deleteManagedCustomWallpaper(context: Context, path: String, except: String = ""): Boolean {
    if (path.isBlank() || path == except) return false
    val file = File(path)
    if (file.parentFile != context.filesDir || !file.name.startsWith(CUSTOM_WALLPAPER_PREFIX)) {
        return false
    }
    return runCatching { file.delete() }.getOrDefault(false)
}
