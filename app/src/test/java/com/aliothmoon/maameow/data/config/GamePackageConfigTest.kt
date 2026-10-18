package com.aliothmoon.maameow.data.config

import com.aliothmoon.maameow.constant.Packages
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GamePackageConfigTest {
    @get:Rule val temporary = TemporaryFolder()
    private val root get() = temporary.root

    @After fun resetResolver() = Packages.initialize(null)

    private fun write(path: String, content: String): File = File(root, path).apply {
        parentFile!!.mkdirs()
        writeText(content)
    }

    @Test fun missingMalformedAndUnreadableConfigsUseBuiltInDefaults() {
        val config = GamePackageConfig(root)
        assertEquals("com.YoStarKR.Arknights", config.resolve("YoStarKR"))
        val file = write("resource/config.json", "{broken")
        val expected = mapOf(
            "Official" to "com.hypergryph.arknights",
            "Bilibili" to "com.hypergryph.arknights.bilibili",
            "YoStarEN" to "com.YoStarEN.Arknights",
            "YoStarJP" to "com.YoStarJP.Arknights",
            "YoStarKR" to "com.YoStarKR.Arknights",
            "txwy" to "tw.txwy.and.arknights",
        )
        for ((client, name) in expected) assertEquals(name, config.resolve(client))
        file.delete()
        file.mkdirs()
        assertEquals("com.YoStarKR.Arknights", config.resolve("YoStarKR"))
        assertNull(config.resolve("Unknown"))
        assertTrue(runCatching { config.snapshot("YoStarKR") }.isFailure)
    }

    @Test fun editingOnlyConfigIsEnoughAndReadsNeverModifyIt() {
        val original = """{"packageName":{"YoStarKR":"$SAMSUNG","YoStarJP":"jp.vendor.game"},"future":{"flag":true}}"""
        val file = write("resource/config.json", original)
        Packages.initialize(GamePackageConfig(root))
        assertEquals(SAMSUNG, Packages["YoStarKR"])
        assertEquals("jp.vendor.game", Packages["YoStarJP"])
        assertEquals(SAMSUNG, GamePackageConfig(root).snapshot("YoStarKR")["YoStarKR"])
        assertEquals(original, file.readText())
        assertFalse(File(root, "overrides").exists())
    }

    @Test fun resourceLayersAndServerSwitchFollowCoreOrder() {
        write("resource/config.json", """{"packageName":{"YoStarKR":"base.game"}}""")
        write("cache/resource/config.json", """{"packageName":{"YoStarKR":"cache.game"}}""")
        write("resource/global/YoStarKR/resource/config.json", """{"packageName":{"YoStarKR":"global.game"}}""")
        val hot = write("cache/resource/global/YoStarKR/resource/config.json", """{"packageName":{"YoStarKR":"global.cache.game"}}""")
        val config = GamePackageConfig(root)
        assertEquals("global.cache.game", config.resolve("YoStarKR"))
        assertEquals("com.YoStarJP.Arknights", config.resolve("YoStarJP"))
        hot.writeText("""{"packageName":{"YoStarKR":"updated.game"}}""")
        assertEquals("updated.game", config.snapshot("YoStarKR")["YoStarKR"])
    }

    @Test fun overridePackageIsLastOnlyWhenEnabled() {
        write("resource/config.json", """{"packageName":{"YoStarKR":"base.game"}}""")
        write("cache/resource/global/YoStarKR/resource/config.json", """{"packageName":{"YoStarKR":"global.cache.game"}}""")
        val override = write("overrides/resource/config.json", """{"packageName":{"YoStarKR":"override.game"}}""")
        var enabled = false
        val config = GamePackageConfig(root) { enabled }
        Packages.initialize(config)
        assertEquals("global.cache.game", Packages["YoStarKR"])
        enabled = true
        assertEquals("override.game", Packages["YoStarKR"])
        assertEquals("override.game", config.snapshot("YoStarKR")["YoStarKR"])
        assertEquals("global.cache.game", config.snapshot("YoStarKR", includeOverrides = false)["YoStarKR"])
        override.writeText("{broken")
        assertTrue(runCatching { config.snapshot("YoStarKR") }.isFailure)
        assertEquals("global.cache.game", config.snapshot("YoStarKR", includeOverrides = false)["YoStarKR"])
    }

    @Test fun loadedPackageStaysStableUntilNextSuccessfulResourceLoad() {
        val file = write("resource/config.json", """{"packageName":{"YoStarKR":"$SAMSUNG"}}""")
        val config = GamePackageConfig(root)
        Packages.initialize(config)
        Packages.onResourcesLoaded("YoStarKR", config.snapshot("YoStarKR"))
        file.writeText("""{"packageName":{"YoStarKR":"next.game"}}""")
        assertEquals(SAMSUNG, Packages["YoStarKR"])
        assertEquals("com.YoStarJP.Arknights", Packages["YoStarJP"])
        Packages.onResourcesLoaded("YoStarKR", config.snapshot("YoStarKR"))
        assertEquals("next.game", Packages["YoStarKR"])
    }

    @Test fun invalidPackageEntriesFallBackWithoutAffectingOtherServers() {
        write("resource/config.json", """{"packageName":{"YoStarKR":"bad;name","YoStarJP":123,"Official":"valid.game"}}""")
        val config = GamePackageConfig(root)
        assertEquals("com.YoStarKR.Arknights", config.resolve("YoStarKR"))
        assertEquals("com.YoStarJP.Arknights", config.resolve("YoStarJP"))
        assertEquals("valid.game", config.resolve("Official"))
    }

    @Test fun unreadableHigherLayerBlocksSnapshotInsteadOfGuessingCorePackage() {
        write("resource/config.json", """{"packageName":{"YoStarKR":"$SAMSUNG"}}""")
        write("cache/resource/config.json", "{broken")
        val config = GamePackageConfig(root)
        assertEquals(SAMSUNG, config.resolve("YoStarKR"))
        assertTrue(runCatching { config.snapshot("YoStarKR") }.isFailure)
    }

    @Test fun dedicatedPackagesApplyLastOnlyWhenEnabledAndLegacyCustomIsOutsideMeowResolution() {
        write("resource/config.json", """{"packageName":{"YoStarKR":"base.game"}}""")
        write("overrides/resource/config.json", """{"packageName":{"YoStarKR":"task.game"}}""")
        val legacy = write("resource/config_custom.json", "{not read by Meow}")
        val packages = write(ResourceConfigEditor.PACKAGE_FILE, """{"packageName":{"YoStarKR":"package.game"}}""")
        val editor = ResourceConfigEditor(root)
        val config = GamePackageConfig(root) { true }
        assertEquals("task.game", config.snapshot("YoStarKR")["YoStarKR"])
        editor.setEnabled(true)
        assertEquals("package.game", config.resolve("YoStarKR"))
        assertEquals("package.game", config.snapshot("YoStarKR", false)["YoStarKR"])
        editor.setEnabled(false)
        assertEquals("task.game", config.snapshot("YoStarKR")["YoStarKR"])
        assertEquals("base.game", config.snapshot("YoStarKR", false)["YoStarKR"])
        packages.writeText("{broken")
        assertEquals("task.game", config.snapshot("YoStarKR")["YoStarKR"])
        editor.setEnabled(true)
        assertTrue(runCatching { config.snapshot("YoStarKR") }.isFailure)
        assertEquals("task.game", config.resolve("YoStarKR"))
        assertEquals("{not read by Meow}", legacy.readText())
    }

    private companion object { const val SAMSUNG = "com.YoStarKR.Samsung.Arknights" }
}
