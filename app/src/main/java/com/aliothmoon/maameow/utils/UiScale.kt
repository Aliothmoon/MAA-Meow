package com.aliothmoon.maameow.utils

/**
 * 页面缩放推荐与悬浮窗 fontScale 策略。
 *
 * 页面缩放通过改写 [androidx.compose.ui.unit.Density.density] 生效；
 * 推荐值主要依据最小宽度。系统大字体不反向压低推荐值（尊重无障碍）。
 */
object UiScale {

    /** 悬浮窗内对系统 fontScale 的钳制（与历史行为一致） */
    const val OVERLAY_FONT_SCALE_MIN = 0.85f
    const val OVERLAY_FONT_SCALE_MAX = 1.3f

    /**
     * 按最小宽度推荐页面缩放百分比（80–110）。
     *
     * @param smallestWidthDp [android.content.res.Configuration.smallestScreenWidthDp]
     * @param fontScale 系统 fontScale；仅在系统字偏小时略抬推荐，大字不减
     */
    fun recommendedFontSizeScale(smallestWidthDp: Int, fontScale: Float): Int {
        var scale = when {
            smallestWidthDp <= 0 -> 100
            smallestWidthDp < 340 -> 85
            smallestWidthDp < 360 -> 90
            smallestWidthDp < 400 -> 95
            else -> 100
        }
        if (fontScale in 0.01f..0.9f) {
            scale = (scale + 5).coerceAtMost(110)
        }
        return scale.coerceIn(80, 110)
    }

    fun clampOverlayFontScale(fontScale: Float): Float =
        fontScale.coerceIn(OVERLAY_FONT_SCALE_MIN, OVERLAY_FONT_SCALE_MAX)
}
