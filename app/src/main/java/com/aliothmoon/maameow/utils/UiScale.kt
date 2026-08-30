package com.aliothmoon.maameow.utils

/**
 * 页面缩放推荐与悬浮窗 fontScale 策略
 *
 * 页面缩放改写 [androidx.compose.ui.unit.Density.density]
 * 自动档以 360dp 为 95%，只在更窄时略缩，系统字号交给 fontScale
 */
object UiScale {

    /** 悬浮窗内对系统 fontScale 的钳制（与历史行为一致） */
    const val OVERLAY_FONT_SCALE_MIN = 0.85f
    const val OVERLAY_FONT_SCALE_MAX = 1.3f

    private const val REF_WIDTH_DP = 360
    private const val NARROW_WIDTH_DP = 320
    private const val REF_SCALE = 95
    private const val NARROW_SCALE = 90

    /**
     * 按最小宽度推荐页面缩放百分比（80–110）
     *
     * @param smallestWidthDp [android.content.res.Configuration.smallestScreenWidthDp]
     * @param fontScale 系统 fontScale；仅窄屏加大字时略减，避免挤爆
     */
    fun recommendedFontSizeScale(smallestWidthDp: Int, fontScale: Float): Int {
        val sw = if (smallestWidthDp <= 0) REF_WIDTH_DP else smallestWidthDp
        var scale = when {
            sw >= REF_WIDTH_DP -> REF_SCALE
            sw <= NARROW_WIDTH_DP -> NARROW_SCALE
            else -> NARROW_SCALE +
                    (sw - NARROW_WIDTH_DP) * (REF_SCALE - NARROW_SCALE) /
                    (REF_WIDTH_DP - NARROW_WIDTH_DP)
        }
        // 窄屏 + 系统大字：控件不再跟着重缩，只让出 5% 给变大的字
        if (sw < REF_WIDTH_DP && fontScale > 1.3f) {
            scale -= 5
        }
        return scale.coerceIn(80, 110)
    }

    fun clampOverlayFontScale(fontScale: Float): Float =
        fontScale.coerceIn(OVERLAY_FONT_SCALE_MIN, OVERLAY_FONT_SCALE_MAX)
}
