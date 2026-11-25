package com.aliothmoon.maameow.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDownloadPolicyTest {

    @Test
    fun `fromStored maps legacy booleans and enum names`() {
        assertEquals(AutoDownloadPolicy.OFF, AutoDownloadPolicy.fromStored("false"))
        assertEquals(AutoDownloadPolicy.OFF, AutoDownloadPolicy.fromStored("OFF"))
        assertEquals(AutoDownloadPolicy.OFF, AutoDownloadPolicy.fromStored(""))
        assertEquals(AutoDownloadPolicy.OFF, AutoDownloadPolicy.fromStored("unknown"))
        assertEquals(AutoDownloadPolicy.WIFI_ONLY, AutoDownloadPolicy.fromStored("WIFI_ONLY"))
        assertEquals(AutoDownloadPolicy.WIFI_ONLY, AutoDownloadPolicy.fromStored(" wifi_only "))
        assertEquals(AutoDownloadPolicy.ALWAYS, AutoDownloadPolicy.fromStored("true"))
        assertEquals(AutoDownloadPolicy.ALWAYS, AutoDownloadPolicy.fromStored("ALWAYS"))
    }

    @Test
    fun `allowsAutoDownload enforces network matrix`() {
        assertFalse(AutoDownloadPolicy.OFF.allowsAutoDownload(isUnmetered = true))
        assertFalse(AutoDownloadPolicy.OFF.allowsAutoDownload(isUnmetered = false))

        assertTrue(AutoDownloadPolicy.WIFI_ONLY.allowsAutoDownload(isUnmetered = true))
        assertFalse(AutoDownloadPolicy.WIFI_ONLY.allowsAutoDownload(isUnmetered = false))

        assertTrue(AutoDownloadPolicy.ALWAYS.allowsAutoDownload(isUnmetered = true))
        assertTrue(AutoDownloadPolicy.ALWAYS.allowsAutoDownload(isUnmetered = false))
    }
}
