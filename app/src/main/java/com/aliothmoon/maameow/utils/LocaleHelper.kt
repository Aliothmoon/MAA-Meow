package com.aliothmoon.maameow.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Utility for persisting and applying a per-app locale.
 *
 * The selected locale tag is stored in a dedicated SharedPreferences file so
 * it can be applied safely in [attachBaseContext] before the DataStore or Koin
 * are initialised.
 *
 * An empty string means "follow the system locale".
 */
object LocaleHelper {

    private const val PREFS_NAME = "locale_prefs"
    private const val KEY_LOCALE = "locale_tag"

    /** Read the stored locale tag (empty = system default). */
    fun getLocaleTag(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOCALE, "") ?: ""
    }

    /** Persist the locale tag (empty string to follow the system). */
    fun setLocaleTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOCALE, tag)
            .apply()
    }

    /**
     * Wrap [base] in a locale-overridden context if a locale is stored.
     * Call this from [attachBaseContext] in your Application and Activity.
     */
    fun applyLocale(base: Context): Context {
        val tag = getLocaleTag(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
