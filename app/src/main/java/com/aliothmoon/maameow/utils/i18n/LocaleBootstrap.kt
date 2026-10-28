package com.aliothmoon.maameow.utils.i18n

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

object LocaleBootstrap {

    fun applyPersisted(context: Context, manager: AppSettingsManager) {
        val persisted = manager.language.value
        val resolved = when {
            !AppCompatDelegate.getApplicationLocales().isEmpty -> currentRuntimeLanguage()
            persisted != AppSettingsManager.AppLanguage.SYSTEM -> persisted
            else -> systemSnapshotLanguage()
        }

        applyLanguage(context, resolved)
        if (persisted != resolved) {
            persistResolvedLanguage(manager, resolved)
        }
    }

    /** 切换语言的唯一入口 */
    fun applyLanguage(context: Context, language: AppSettingsManager.AppLanguage) {
        AppCompatDelegate.setApplicationLocales(language.toLocaleList())
        syncApplicationResources(context)
    }

    /**
     * API 32 及以下 AppCompat 不更新 Application 的 Resources，日志、通知会露系统语言
     * 不用 ContextCompat.getContextForLanguage：未开 autoStoreLocales，32 及以下读不到
     */
    @Suppress("DEPRECATION")
    fun syncApplicationResources(context: Context) {
        val res = context.applicationContext.resources
        val target = localesToSync(
            sdkInt = Build.VERSION.SDK_INT,
            appLocales = AppCompatDelegate.getApplicationLocales(),
            systemLocales = ConfigurationCompat.getLocales(Resources.getSystem().configuration),
            current = ConfigurationCompat.getLocales(res.configuration),
        ) ?: return
        val config = Configuration(res.configuration)
        ConfigurationCompat.setLocales(config, target)
        res.updateConfiguration(config, res.displayMetrics)
    }

    /** null = 无需写入 */
    internal fun localesToSync(
        sdkInt: Int,
        appLocales: LocaleListCompat,
        systemLocales: LocaleListCompat,
        current: LocaleListCompat,
    ): LocaleListCompat? {
        if (sdkInt >= 33) return null
        val target = appLocales.takeUnless { it.isEmpty } ?: systemLocales
        return target.takeUnless { it == current }
    }

    fun resolveSelectedLanguage(current: AppSettingsManager.AppLanguage): AppSettingsManager.AppLanguage {
        return if (current == AppSettingsManager.AppLanguage.SYSTEM) {
            currentRuntimeLanguage()
        } else {
            current
        }
    }

    private fun currentRuntimeLanguage(): AppSettingsManager.AppLanguage {
        val current = AppCompatDelegate.getApplicationLocales()
        if (!current.isEmpty) {
            return current.toAppLanguage()
        }
        return systemSnapshotLanguage()
    }

    private fun systemSnapshotLanguage(): AppSettingsManager.AppLanguage {
        val current = LocaleListCompat.forLanguageTags(Locale.getDefault().toLanguageTag())
        return current.toAppLanguage()
    }

    private fun persistResolvedLanguage(
        manager: AppSettingsManager,
        resolved: AppSettingsManager.AppLanguage,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            manager.setLanguage(resolved)
        }
    }

    fun AppSettingsManager.AppLanguage.toLocaleList(): LocaleListCompat =
        if (this == AppSettingsManager.AppLanguage.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }

    private fun LocaleListCompat.toAppLanguage(): AppSettingsManager.AppLanguage {
        return when (this[0]?.language) {
            "en" -> AppSettingsManager.AppLanguage.EN
            "ko" -> AppSettingsManager.AppLanguage.KO
            else -> AppSettingsManager.AppLanguage.ZH
        }
    }
}
