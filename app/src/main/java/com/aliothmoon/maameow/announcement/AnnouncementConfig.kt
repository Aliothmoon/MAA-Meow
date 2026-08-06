package com.aliothmoon.maameow.announcement

import android.content.Context
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import java.io.IOException
import java.util.Locale

object AnnouncementConfig {

    private const val ASSET_DIR = "announcement"

    fun loadContent(context: Context, language: AppSettingsManager.AppLanguage): String {
        val fileName = if (isZh(language)) "announcement_zh.md" else "announcement_en.md"
        return try {
            context.assets.open("$ASSET_DIR/$fileName").bufferedReader().use { it.readText() }
        } catch (_: IOException) {
            ""
        }
    }

    fun imageAssetPath(language: AppSettingsManager.AppLanguage): String =
        if (isZh(language)) "announcement/NoSkland.jpg" else "announcement/NoSklandEn.jpg"

    fun isZh(language: AppSettingsManager.AppLanguage) = when (language) {
        AppSettingsManager.AppLanguage.ZH -> true
        AppSettingsManager.AppLanguage.EN -> false
        AppSettingsManager.AppLanguage.SYSTEM -> Locale.getDefault().language.startsWith("zh")
    }
}
