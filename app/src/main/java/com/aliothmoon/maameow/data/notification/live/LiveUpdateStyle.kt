package com.aliothmoon.maameow.data.notification.live

import android.graphics.Color
import com.aliothmoon.maameow.data.preferences.AppSettingsManager

/**
 * Live Updates 配色解析。
 *
 * [color] 供通知强调色与进度分段使用，DEFAULT 走状态语义色；
 * [colorHexOrNull] 供超级岛使用，DEFAULT 返回 null 表示沿用后端默认色。
 */
class LiveUpdateStyle(
    private val appSettings: AppSettingsManager,
) {

    fun color(isError: Boolean, isCompleted: Boolean): Int =
        when (appSettings.liveUpdateColorScheme.value) {
            AppSettingsManager.LiveUpdateColorScheme.DEFAULT -> when {
                isError -> COLOR_ERROR
                isCompleted -> COLOR_COMPLETED
                else -> COLOR_ACTIVE
            }
            AppSettingsManager.LiveUpdateColorScheme.BLUE -> COLOR_BLUE
            AppSettingsManager.LiveUpdateColorScheme.GREEN -> COLOR_GREEN
            AppSettingsManager.LiveUpdateColorScheme.ORANGE -> COLOR_ORANGE
            AppSettingsManager.LiveUpdateColorScheme.PURPLE -> COLOR_PURPLE
            AppSettingsManager.LiveUpdateColorScheme.PINK -> COLOR_PINK
            AppSettingsManager.LiveUpdateColorScheme.TEAL -> COLOR_TEAL
            AppSettingsManager.LiveUpdateColorScheme.CUSTOM -> parseCustomColor() ?: COLOR_ACTIVE
        }

    /** 非默认配色方案的 #RRGGBB，DEFAULT 返回 null */
    fun colorHexOrNull(): String? {
        val value = when (appSettings.liveUpdateColorScheme.value) {
            AppSettingsManager.LiveUpdateColorScheme.DEFAULT -> return null
            AppSettingsManager.LiveUpdateColorScheme.BLUE -> COLOR_BLUE
            AppSettingsManager.LiveUpdateColorScheme.GREEN -> COLOR_GREEN
            AppSettingsManager.LiveUpdateColorScheme.ORANGE -> COLOR_ORANGE
            AppSettingsManager.LiveUpdateColorScheme.PURPLE -> COLOR_PURPLE
            AppSettingsManager.LiveUpdateColorScheme.PINK -> COLOR_PINK
            AppSettingsManager.LiveUpdateColorScheme.TEAL -> COLOR_TEAL
            AppSettingsManager.LiveUpdateColorScheme.CUSTOM -> parseCustomColor() ?: return null
        }
        return String.format("#%06X", 0xFFFFFF and value)
    }

    private fun parseCustomColor(): Int? {
        val hex = appSettings.liveUpdateCustomColor.value
        if (hex.isEmpty()) return null
        return runCatching { Color.parseColor(hex) }.getOrNull()
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
    }
}
