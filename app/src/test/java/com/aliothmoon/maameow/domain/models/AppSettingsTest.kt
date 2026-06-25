package com.aliothmoon.maameow.domain.models

import com.aliothmoon.maameow.constant.OFFICIAL_SHIZUKU_PACKAGE
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {

    @Test
    fun defaultShizukuLaunchPackage_usesOfficialShizukuPackage() {
        val settings = AppSettings()

        assertEquals(OFFICIAL_SHIZUKU_PACKAGE, settings.shizukuLaunchPackage)
    }
}
