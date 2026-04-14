package com.aliothmoon.maameow.utils.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

object LocaleBootstrap {

    fun applyPersisted(manager: AppSettingsManager) {
        val persisted = manager.language.value
        val resolved = when {
            !AppCompatDelegate.getApplicationLocales().isEmpty -> currentRuntimeLanguage()
            persisted != AppSettingsManager.AppLanguage.SYSTEM -> persisted
            else -> systemSnapshotLanguage()
        }

        AppCompatDelegate.setApplicationLocales(resolved.toLocaleList())
        if (persisted != resolved) {
            persistResolvedLanguage(manager, resolved)
        }
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
            else -> AppSettingsManager.AppLanguage.ZH
        }
    }
}
