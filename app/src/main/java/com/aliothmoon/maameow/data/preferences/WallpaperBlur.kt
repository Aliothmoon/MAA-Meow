package com.aliothmoon.maameow.data.preferences

import android.os.Build

/**
 * Wallpaper blur is user-facing as 0..100% (same scale as opacity).
 *
 * - API 31+: [androidx.compose.ui.draw.blur] with radius 0..[RADIUS_DP_MAX] dp.
 * - API 28–30: Compose blur is a no-op; use software Stack Blur on a downscaled bitmap
 *   with radius 0..[SOFTWARE_RADIUS_PX_MAX] px (see [BitmapUtils.blurForWallpaper]).
 */
object WallpaperBlur {
    const val PERCENT_MAX = 100
    /** Hard cap for Modifier.blur radius in dp (API 31+). */
    const val RADIUS_DP_MAX = 12f
    /**
     * Hard cap for software Stack Blur radius in pixels on the downscaled bitmap.
     * Tuned so 100% is clearly visible on API 28–30 without multi-second freezes.
     */
    const val SOFTWARE_RADIUS_PX_MAX = 25

    /** True when we must pre-blur the bitmap instead of using Modifier.blur. */
    val needsSoftwareBlur: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    fun parsePercent(raw: String?): Int =
        (raw?.toIntOrNull() ?: 0).coerceIn(0, PERCENT_MAX)

    fun percentToRadiusDp(percent: Int): Float =
        (percent.coerceIn(0, PERCENT_MAX) / 100f) * RADIUS_DP_MAX

    /** Software Stack Blur radius in pixels (downscaled bitmap space). */
    fun percentToSoftwareRadiusPx(percent: Int): Int =
        ((percent.coerceIn(0, PERCENT_MAX) / 100f) * SOFTWARE_RADIUS_PX_MAX)
            .toInt()
            .coerceIn(0, SOFTWARE_RADIUS_PX_MAX)

    /** Legacy builds stored absolute blur dp in 0..12. */
    fun legacyDpToPercent(legacyDp: Int): Int =
        ((legacyDp.coerceIn(0, RADIUS_DP_MAX.toInt()) * PERCENT_MAX) / RADIUS_DP_MAX.toInt())
            .coerceIn(0, PERCENT_MAX)
}
