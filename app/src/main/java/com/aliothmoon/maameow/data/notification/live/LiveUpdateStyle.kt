package com.aliothmoon.maameow.data.notification.live

import android.graphics.Color
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.AppSettingsManager.LiveUpdateChipContent
import com.aliothmoon.maameow.data.preferences.AppSettingsManager.LiveUpdateColorScheme
import com.aliothmoon.maameow.domain.notification.LiveCategory
import com.aliothmoon.maameow.domain.notification.LiveSession

/** 实况通知样式解析：配色与短文本，各后端共用 */
class LiveUpdateStyle(
    private val appSettings: AppSettingsManager,
) {

    val chipContent: LiveUpdateChipContent
        get() = appSettings.liveUpdateChipContent.value

    // 错误/完成固定语义色，用户配色只作用于进行中
    fun color(isError: Boolean, isCompleted: Boolean): Int = when {
        isError -> COLOR_ERROR
        isCompleted -> COLOR_COMPLETED
        else -> activeColor() ?: COLOR_ACTIVE
    }

    /** 超级岛进度色；DEFAULT 返回 null，沿用岛的默认色 */
    fun progressColorHexOrNull(isError: Boolean): String? =
        (if (isError) COLOR_ERROR else activeColor())
            ?.let { String.format("#%06X", 0xFFFFFF and it) }

    /** 原生胶囊文本；null 为不设，"" 为显式清空 */
    fun capsuleText(session: LiveSession): String? {
        if (session.category != LiveCategory.PROGRESS) return session.capsuleText.ifBlank { null }
        val text = when (chipContent) {
            LiveUpdateChipContent.BOTH -> session.capsuleText
            LiveUpdateChipContent.PROGRESS -> session.progressLabel.orEmpty()
            LiveUpdateChipContent.TASK -> session.taskName.orEmpty()
            LiveUpdateChipContent.LOG -> session.statusLine()
            LiveUpdateChipContent.NONE -> return ""
        }
        return text.ifBlank { null }
    }

    private fun activeColor(): Int? {
        val scheme = appSettings.liveUpdateColorScheme.value
        if (scheme != LiveUpdateColorScheme.CUSTOM) return scheme.argb
        val hex = appSettings.liveUpdateCustomColor.value
        return runCatching { Color.parseColor(hex) }.getOrNull()
    }

    companion object {
        const val COLOR_ACTIVE = 0xFF2196F3.toInt()
        const val COLOR_COMPLETED = 0xFF4CAF50.toInt()
        const val COLOR_ERROR = 0xFFD32F2F.toInt()
    }
}
