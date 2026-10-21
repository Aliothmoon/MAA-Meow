package com.aliothmoon.maameow.constant

import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackagesTest {

    @After
    fun tearDown() {
        Packages.bindOverrides(MutableStateFlow(emptyMap()))
    }

    @Test
    fun get_prefersOverride_andFallsBackToDefault() {
        val overrides = MutableStateFlow(mapOf("YoStarKR" to "com.YoStarKR.Samsung.Arknights"))
        Packages.bindOverrides(overrides)

        assertEquals("com.YoStarKR.Samsung.Arknights", Packages["YoStarKR"])
        assertEquals("com.hypergryph.arknights", Packages["Official"])
        assertNull(Packages["Unknown"])

        // 设置变了即时生效
        overrides.value = emptyMap()
        assertEquals("com.YoStarKR.Arknights", Packages["YoStarKR"])
    }

    @Test
    fun iterator_replacesOverriddenClientsOnly() {
        Packages.bindOverrides(MutableStateFlow(mapOf("YoStarKR" to "com.YoStarKR.Samsung.Arknights")))
        val all = Packages.associate { it.key to it.value }

        assertEquals(Packages.defaults.keys, all.keys)
        assertEquals("com.YoStarKR.Samsung.Arknights", all["YoStarKR"])
    }

    @Test
    fun parseGamePackages_dropsInvalidEntries() {
        assertTrue(AppSettingsManager.parseGamePackages("").isEmpty())
        assertTrue(AppSettingsManager.parseGamePackages("not json").isEmpty())

        val parsed = AppSettingsManager.parseGamePackages(
            """
            {
              "YoStarKR": "com.YoStarKR.Samsung.Arknights",
              "Official": "com.hypergryph.arknights",
              "YoStarJP": "bad name",
              "Unknown": "com.example.game"
            }
            """
        )
        // 与默认相同、非法包名、未知客户端都不算自定义
        assertEquals(mapOf("YoStarKR" to "com.YoStarKR.Samsung.Arknights"), parsed)
    }
}
