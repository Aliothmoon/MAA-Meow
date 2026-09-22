package com.aliothmoon.maameow.data.notification.live

import android.graphics.Color
import com.aliothmoon.maameow.data.preferences.AppSettingsManager

/**
 * Live Updates 配色解析。
 *
 * 错误/完成态固定用语义色（红/绿），用户配色只作用于进行中的常规态，避免选色后
 * 丢失状态信息。[progressColorHexOrNull] 供超级岛使用，DEFAULT 返回 null 表示沿用后端默认色。
 */
class LiveUpdateStyle(
    private val appSettings: AppSettingsManager,
) {

    fun color(isError: Boolean, isCompleted: Boolean): Int = when {
        isError -> COLOR_ERROR
        isCompleted -> COLOR_COMPLETED
        else -> activeColor() ?: COLOR_ACTIVE
    }

    /** 超级岛进度色：错误态固定红，其余跟随用户配色；DEFAULT 返回 null（沿用后端默认） */
    fun progressColorHexOrNull(isError: Boolean): String? {
        if (isError) return HEX_ERROR
        val value = activeColor() ?: return null
        return String.format("#%06X", 0xFFFFFF and value)
    }

    /** 用户配色解析出的常规态颜色；DEFAULT 返回 null */
    private fun activeColor(): Int? = when (appSettings.liveUpdateColorScheme.value) {
        AppSettingsManager.LiveUpdateColorScheme.DEFAULT -> null
        AppSettingsManager.LiveUpdateColorScheme.BLUE -> COLOR_BLUE
        AppSettingsManager.LiveUpdateColorScheme.GREEN -> COLOR_GREEN
        AppSettingsManager.LiveUpdateColorScheme.ORANGE -> COLOR_ORANGE
        AppSettingsManager.LiveUpdateColorScheme.PURPLE -> COLOR_PURPLE
        AppSettingsManager.LiveUpdateColorScheme.PINK -> COLOR_PINK
        AppSettingsManager.LiveUpdateColorScheme.TEAL -> COLOR_TEAL
        AppSettingsManager.LiveUpdateColorScheme.CUSTOM -> {
            val hex = appSettings.liveUpdateCustomColor.value
            if (hex.isEmpty()) null else runCatching { Color.parseColor(hex) }.getOrNull()
        }
    }

    private companion object {
        const val COLOR_COMPLETED = 0xFF4CAF50.toInt()
        const val COLOR_ACTIVE = 0xFF2196F3.toInt()
        const val COLOR_ERROR = 0xFFD32F2F.toInt()
        const val COLOR_BLUE = 0xFF2196F3.toInt()
        const val COLOR_GREEN = 0xFF4CAF50.toInt()
        const val COLOR_ORANGE = 0xFFFF9800.toInt()
        const val COLOR_PURPLE = 0xFF9C27B0.toInt()
        const val COLOR_PINK = 0xFFE91E63.toInt()
        const val COLOR_TEAL = 0xFF009688.toInt()
        const val HEX_ERROR = "#D32F2F"
    }
}
