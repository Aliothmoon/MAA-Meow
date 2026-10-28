package com.aliothmoon.maameow.utils.i18n

import androidx.core.os.LocaleListCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** API 32 及以下应用语言同步进 Application 资源 */
class LocaleBootstrapTest {

    private val zh = LocaleListCompat.forLanguageTags("zh-CN")
    private val en = LocaleListCompat.forLanguageTags("en")
    private val none = LocaleListCompat.getEmptyLocaleList()

    @Test
    fun api33AndAbove_leavesResourcesToTheFramework() {
        assertNull(LocaleBootstrap.localesToSync(33, appLocales = en, systemLocales = zh, current = zh))
    }

    @Test
    fun systemChinese_appEnglish_syncsEnglish() {
        assertEquals(en, LocaleBootstrap.localesToSync(32, appLocales = en, systemLocales = zh, current = zh))
    }

    @Test
    fun systemEnglish_appChinese_syncsChinese() {
        assertEquals(zh, LocaleBootstrap.localesToSync(28, appLocales = zh, systemLocales = en, current = en))
    }

    @Test
    fun alreadyInAppLanguage_isLeftAlone() {
        assertNull(LocaleBootstrap.localesToSync(32, appLocales = en, systemLocales = zh, current = en))
    }

    @Test
    fun noAppLanguage_restoresSystemLanguage() {
        assertEquals(zh, LocaleBootstrap.localesToSync(32, appLocales = none, systemLocales = zh, current = en))
        assertNull(LocaleBootstrap.localesToSync(32, appLocales = none, systemLocales = zh, current = zh))
    }
}
