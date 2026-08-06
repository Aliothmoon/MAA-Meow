package com.aliothmoon.maameow.utils

/**
 * 页面缩放推荐与悬浮窗 fontScale 策略。
 *
 * 页面缩放通过改写 [androidx.compose.ui.unit.Density.density] 生效；
 * 推荐值主要依据最小宽度，并随系统字体双向微调：小字略抬、大字按档位下压
 */
object UiScale {

    /** 悬浮窗内对系统 fontScale 的钳制（与历史行为一致） */
    const val OVERLAY_FONT_SCALE_MIN = 0.85f
    const val OVERLAY_FONT_SCALE_MAX = 1.3f

    /**
     * 按最小宽度推荐页面缩放百分比（80–110）。
     *
     * @param smallestWidthDp [android.content.res.Configuration.smallestScreenWidthDp]
     * @param fontScale 系统 fontScale；小字略抬推荐，大字按档位下压
     *（最终文字大小 = fontScale × 页面缩放，叠乘不压会整体偏大）
     */
    fun recommendedFontSizeScale(smallestWidthDp: Int, fontScale: Float): Int {
        // 基准整体偏紧一档，默认信息密度更高（设置页列表等更省纵向空间）
        var scale = when {
            smallestWidthDp <= 0 -> 95
            smallestWidthDp < 340 -> 80
            smallestWidthDp < 360 -> 85
            smallestWidthDp < 400 -> 90
            else -> 95
        }
        scale += when {
            fontScale in 0.01f..0.9f -> 5
            fontScale > 1.5f -> -20
            fontScale > 1.3f -> -15
            fontScale > 1.15f -> -10
            fontScale > 1.0f -> -5
            else -> 0
        }
        return scale.coerceIn(80, 110)
    }

    fun clampOverlayFontScale(fontScale: Float): Float =
        fontScale.coerceIn(OVERLAY_FONT_SCALE_MIN, OVERLAY_FONT_SCALE_MAX)
}
