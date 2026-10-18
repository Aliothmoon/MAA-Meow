package com.aliothmoon.maameow.data.config

import com.aliothmoon.maameow.CoreConfigFixture
import com.aliothmoon.maameow.remote.CoreResourceConfigs
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission.*

class ResourceConfigEditorTest {
    @get:Rule val temporary = TemporaryFolder()
    private val root get() = temporary.root
    private val editor get() = ResourceConfigEditor(root)
    private val path = ResourceConfigEditor.PACKAGE_FILE
    private val packageFile get() = File(root, path)
    private val generated get() = File(root, CoreResourceConfigs.PACKAGE_RESOURCE_CONFIG)
    private fun write(path: String, text: String = "{}") = File(root, path).apply {
        parentFile!!.mkdirs(); writeText(text)
    }
    private fun config(file: File) = JsonUtils.common.parseToJsonElement(file.readText()).jsonObject
    private fun base() = write("resource/config.json", CoreConfigFixture.text("""{"YoStarKR":"default.game","YoStarJP":"default.jp"}"""))
    private fun save(name: String, client: String = "YoStarKR") = editor.save(path, """{"packageName":{"$client":"$name"}}""", client)

    @Test fun openingTheEditorDoesNotCreatePackageOrLegacyCustomFiles() {
        base()
        write("cache/resource/tasks.json")
        write("overrides/resource/tasks/tasks.json")
        assertFalse(editor.isEnabled())
        assertTrue(editor.read(path, "YoStarKR", true).contains("default.game"))
        assertFalse(packageFile.exists())
    }

    @Test fun savingOnlyChangesTheCurrentClientInTheDedicatedFile() {
        val standard = base()
        val original = standard.readBytes()
        val legacy = write("resource/config_custom.json", "{existing custom is outside this feature}")
        val legacyBytes = legacy.readBytes()
        editor.setEnabled(true)
        save("custom.jp", "YoStarJP")
        save("new.game")
        assertEquals(setOf("packageName"), config(packageFile).keys)
        assertEquals("custom.jp", GamePackageConfig(root).resolve("YoStarJP"))
        assertEquals("new.game", GamePackageConfig(root).resolve("YoStarKR"))
        val shown = JsonUtils.common.parseToJsonElement(editor.read(path, "YoStarKR", false)).jsonObject
        assertEquals(setOf("YoStarKR"), shown["packageName"]!!.jsonObject.keys)
        assertArrayEquals(original, standard.readBytes())
        assertArrayEquals(legacyBytes, legacy.readBytes())
    }

    @Test fun togglingOnlyChangesEnabledStateAndKeepsManualEditsMadeWhileOff() {
        base()
        editor.setEnabled(true)
        save("saved.game")
        val saved = packageFile.readBytes()
        editor.setEnabled(false)
        assertArrayEquals(saved, packageFile.readBytes())
        assertEquals("default.game", GamePackageConfig(root).resolve("YoStarKR"))
        packageFile.writeText("""{"packageName":{"YoStarKR":"externally.edited"}}""")
        val edited = packageFile.readBytes()
        ResourceConfigEditor(root).setEnabled(true)
        assertArrayEquals(edited, packageFile.readBytes())
        assertEquals("externally.edited", GamePackageConfig(root).resolve("YoStarKR"))
        assertFalse(File(root, "resource/config_custom.json").exists())
    }

    @Test fun resetOnlyRemovesTheCurrentClientAndKeepsEnabledStateAndOriginalResources() {
        val standard = base()
        val original = standard.readBytes()
        val legacy = write("resource/config_custom.json", "{untouched}")
        editor.setEnabled(true)
        save("custom.jp", "YoStarJP")
        save("custom.kr")
        editor.reset("YoStarKR")
        assertTrue(editor.isEnabled())
        assertEquals("default.game", GamePackageConfig(root).resolve("YoStarKR"))
        assertEquals("custom.jp", GamePackageConfig(root).resolve("YoStarJP"))
        editor.setEnabled(false)
        editor.setEnabled(true)
        assertEquals("default.game", GamePackageConfig(root).resolve("YoStarKR"))
        assertEquals("{untouched}", legacy.readText())
        assertArrayEquals(original, standard.readBytes())
    }

    @Test fun coreResourceHasACompleteSchemaWhileTheSourceRemainsPackageOnly() {
        base()
        val highest = write("cache/resource/global/YoStarKR/resource/config.json",
            CoreConfigFixture.text().replace("\"taskDelay\":0", "\"taskDelay\":91"))
        val original = highest.readBytes()
        editor.setEnabled(true)
        save("custom.game")
        val source = packageFile.readBytes()
        assertEquals(File(root, CoreResourceConfigs.PACKAGE_RESOURCE_PARENT), editor.prepareForCore("YoStarKR", false))
        CoreConfigFixture.assertLoadable(generated)
        assertEquals(config(highest)["options"], config(generated)["options"])
        assertEquals(JsonArray(emptyList()), config(generated)["connection"])
        assertEquals("\"custom.game\"", config(generated)["packageName"]!!.jsonObject["YoStarKR"].toString())
        assertArrayEquals(source, packageFile.readBytes())
        assertArrayEquals(original, highest.readBytes())
    }

    @Test fun generatedResourceFollowsEnabledTaskOverrideOptionsWithoutModifyingIt() {
        val standard = base()
        val tasksConfig = write("overrides/resource/config.json",
            CoreConfigFixture.text().replace("\"taskDelay\":0", "\"taskDelay\":123"))
        val original = tasksConfig.readBytes()
        editor.setEnabled(true)
        save("custom.game")
        editor.prepareForCore("YoStarKR", false)
        assertEquals(config(standard)["options"], config(generated)["options"])
        editor.prepareForCore("YoStarKR", true)
        assertEquals(config(tasksConfig)["options"], config(generated)["options"])
        assertArrayEquals(original, tasksConfig.readBytes())
    }

    @Test fun disabledDeletedAndResetOverridesCannotLeaveAnActiveGeneratedResource() {
        base()
        editor.setEnabled(true)
        save("saved.game")
        editor.prepareForCore("YoStarKR", false)
        assertTrue(generated.isFile)
        editor.setEnabled(false)
        assertNull(editor.prepareForCore("YoStarKR", false))
        assertFalse(generated.exists())
        assertTrue(packageFile.readText().contains("saved.game"))
        editor.setEnabled(true)
        editor.prepareForCore("YoStarKR", false)
        editor.reset("YoStarKR")
        assertNull(editor.prepareForCore("YoStarKR", false))
        assertFalse(generated.exists())
        save("saved.again")
        editor.prepareForCore("YoStarKR", false)
        assertTrue(packageFile.delete())
        assertNull(editor.prepareForCore("YoStarKR", false))
        assertFalse(generated.exists())
    }

    @Test fun repeatedPackageEditsKeepNativeConfigValidAndOriginalBytesUnchanged() {
        val standard = base()
        val original = standard.readBytes()
        editor.setEnabled(true)
        for (name in listOf("com.example.notinstalled", "com.YoStarKR.Samsung.Arknights",
            "com.example.notinstalled", "com.YoStarKR.Samsung.Arknights")) {
            save(name)
            editor.prepareForCore("YoStarKR", false)
            CoreConfigFixture.assertLoadable(generated)
            assertEquals(name, GamePackageConfig(root).snapshot("YoStarKR")["YoStarKR"])
            assertEquals(setOf("packageName"), config(packageFile).keys)
        }
        assertArrayEquals(original, standard.readBytes())
    }

    @Test fun atomicSavesUseSharedReadablePermissionsIncludingTheCoreResource() {
        assumeTrue("POSIX file modes are unavailable on this host", root.toPath().fileSystem.supportedFileAttributeViews().contains("posix"))
        base()
        editor.setEnabled(true)
        save("custom.game")
        editor.prepareForCore("YoStarKR", false)
        for (file in listOf(packageFile, generated)) {
            val permissions = Files.getPosixFilePermissions(file.toPath())
            assertTrue(GROUP_READ in permissions || OTHERS_READ in permissions)
        }
        Files.setPosixFilePermissions(generated.toPath(), setOf(OWNER_READ, OWNER_WRITE))
        editor.prepareForCore("YoStarKR", false)
        val repaired = Files.getPosixFilePermissions(generated.toPath())
        assertTrue(GROUP_READ in repaired || OTHERS_READ in repaired)
    }

    @Test fun invalidJsonAndUnsupportedPathsCannotChangeSavedPackages() {
        base()
        editor.setEnabled(true)
        save("old.game")
        val original = packageFile.readBytes()
        for (invalid in listOf("{broken", "[]", "{}", """{"packageName":[]}""",
            """{"packageName":{"YoStarKR":"bad;name"}}""", """{"packageName":{"YoStarJP":"jp.game"}}""",
            """{"packageName":{"YoStarKR":"valid.game"},"other":true}""")) {
            assertFalse(editor.isValid(invalid, "YoStarKR"))
            assertTrue(runCatching { editor.save(path, invalid, "YoStarKR") }.isFailure)
            assertArrayEquals(original, packageFile.readBytes())
        }
        for (unsupported in listOf("../$path", "resource/config.json", "resource/config_custom.json", "overrides/resource/tasks/tasks.json")) {
            assertTrue(runCatching { editor.save(unsupported, """{"packageName":{"YoStarKR":"valid.game"}}""", "YoStarKR") }.isFailure)
        }
        editor.setEnabled(false)
        assertTrue(runCatching { save("disabled.game") }.isFailure)
        assertArrayEquals(original, packageFile.readBytes())
    }

    @Test fun malformedPackageFileFailsWhenEnabledButDoesNotBlockDisabledResources() {
        base()
        packageFile.writeText("{broken")
        editor.setEnabled(true)
        assertTrue(runCatching { editor.prepareForCore("YoStarKR", false) }.isFailure)
        editor.setEnabled(false)
        assertNull(editor.prepareForCore("YoStarKR", false))
        assertEquals("{broken", packageFile.readText())
    }
}
