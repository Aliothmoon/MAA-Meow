package com.aliothmoon.maameow.remote.internal

import com.aliothmoon.maameow.remote.CoreDataDir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 提权进程侧独立目录：资源来自 APK，热更包与用户文件由 App 投递 */
class CoreDataStoreTest {

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray =
        ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { zos ->
                entries.forEach { (name, content) ->
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(content.toByteArray())
                    zos.closeEntry()
                }
            }
        }.toByteArray()

    private fun fakeApk(dir: File): File = File(dir, "base.apk").apply {
        writeBytes(
            zipOf(
                "classes.dex" to "dex",
                "${CoreDataDir.RESOURCE_ASSET_PREFIX}version.json" to "{\"last_updated\":\"2026-09-01\"}",
                "${CoreDataDir.RESOURCE_ASSET_PREFIX}template/a.png" to "png",
                CoreDataDir.OVERRIDES_ASSET_ENTRY to "{\"override\":true}",
                "assets/announcement/announcement_zh.md" to "not a resource",
            )
        )
    }

    @Test
    fun ensureResources_extractsBundledResourceAndOverridesTemplate_thenSkipsOnSameStamp() {
        val tmp = Files.createTempDirectory("core-data").toFile()
        try {
            val store = CoreDataStore(File(tmp, "root"))
            val apk = fakeApk(tmp)

            assertTrue(store.ensureResources(apk, "1:100"))
            assertEquals("1:100", store.resourceStamp())
            assertEquals("png", File(store.root, "resource/template/a.png").readText())
            assertEquals("{\"override\":true}", File(store.root, CoreDataDir.OVERRIDES_TASKS_REL).readText())
            assertFalse(File(store.root, "resource/announcement").exists())

            // 同 stamp 不重解：删个文件再 ensure 仍然缺
            File(store.root, "resource/template/a.png").delete()
            assertTrue(store.ensureResources(apk, "1:100"))
            assertFalse(File(store.root, "resource/template/a.png").exists())

            // 换 stamp 重解
            assertTrue(store.ensureResources(apk, "2:200"))
            assertTrue(File(store.root, "resource/template/a.png").exists())
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun ensureResources_failsOnApkWithoutResource() {
        val tmp = Files.createTempDirectory("core-data").toFile()
        try {
            val store = CoreDataStore(File(tmp, "root"))
            val apk = File(tmp, "bad.apk").apply { writeBytes(zipOf("classes.dex" to "dex")) }
            assertFalse(store.ensureResources(apk, "1:1"))
            assertEquals("", store.resourceStamp())
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun applyHotUpdate_overwritesResource_andDerivesTasksJson() {
        val tmp = Files.createTempDirectory("core-data").toFile()
        try {
            val store = CoreDataStore(File(tmp, "root"))
            val zip = zipOf(
                "MaaResource-main/README.md" to "skip",
                "MaaResource-main/resource/version.json" to "{\"last_updated\":\"2026-09-05\"}",
                "MaaResource-main/resource/tasks.json" to "{\"t\":1}",
                "MaaResource-main/resource/global/YoStarEN/resource/tasks.json" to "{\"en\":1}",
            )
            assertTrue(store.applyHotUpdate(ByteArrayInputStream(zip)))
            assertEquals("2026-09-05", store.resourceVersion())
            assertEquals("{\"t\":1}", File(store.root, "resource/tasks/tasks.json").readText())
            assertEquals("{\"en\":1}", File(store.root, "resource/global/YoStarEN/resource/tasks/tasks.json").readText())
            assertFalse(File(store.root, "resource/README.md").exists())
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun applyHotUpdate_acceptsArchiveWithoutTopDir() {
        val tmp = Files.createTempDirectory("core-data").toFile()
        try {
            val store = CoreDataStore(File(tmp, "root"))
            val zip = zipOf(
                "resource/version.json" to "{\"last_updated\":\"2026-09-06\"}",
                "resource/tasks.json" to "{\"t\":2}",
                "README.md" to "skip",
            )
            assertTrue(store.applyHotUpdate(ByteArrayInputStream(zip)))
            assertEquals("2026-09-06", store.resourceVersion())
            assertEquals("{\"t\":2}", File(store.root, "resource/tasks/tasks.json").readText())
            assertFalse(File(store.root, "resource/README.md").exists())
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun putFile_rejectsEscapingPaths_andWritesInsideRoot() {
        val tmp = Files.createTempDirectory("core-data").toFile()
        try {
            val store = CoreDataStore(File(tmp, "root"))
            assertFalse(store.putFile("../evil.json", ByteArrayInputStream("x".toByteArray())))
            assertFalse(store.putFile("/abs.json", ByteArrayInputStream("x".toByteArray())))
            assertTrue(store.putFile("copilot/1.json", ByteArrayInputStream("{}".toByteArray())))
            assertEquals("{}", File(store.root, "copilot/1.json").readText())
            assertNull(store.debugFile("../copilot/1.json"))
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun staleConfigsAreRemovedAfterStoreRecreationWithoutDeletingOtherFiles() {
        val tmp = Files.createTempDirectory("core-config-sync").toFile()
        try {
            val store = CoreDataStore(tmp)
            val configs = listOf(
                "resource/config.json",
                "resource/global/YoStarKR/resource/config.json",
                "cache/resource/config.json",
                "cache/resource/global/YoStarKR/resource/config.json",
                "overrides/resource/config.json",
            )
            val unrelated = listOf("resource/tasks/tasks.json", "copilot/1.json", "debug/asst.log", "overrides/resource/tasks/tasks.json")
            (configs + unrelated).forEach { rel ->
                assertTrue(store.putFile(rel, ByteArrayInputStream("{}".toByteArray())))
            }
            val retained = configs.take(2)
            assertTrue(CoreDataStore(tmp).removeStaleResourceConfigs(retained))
            retained.forEach { assertTrue(File(tmp, it).isFile) }
            configs.drop(2).forEach { assertFalse(File(tmp, it).exists()) }
            unrelated.forEach { assertEquals("{}", File(tmp, it).readText()) }
            assertTrue(store.removeStaleResourceConfigs(retained))
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun configCleanupFailureIsReportedWithoutDeletingDirectories() {
        val tmp = Files.createTempDirectory("core-config-sync").toFile()
        try {
            val directory = File(tmp, "cache/resource/config.json").apply { mkdirs() }
            File(directory, "keep.txt").writeText("keep")
            assertFalse(CoreDataStore(tmp).removeStaleResourceConfigs(emptyList()))
            assertEquals("keep", File(directory, "keep.txt").readText())
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun clear_removesEverything() {
        val tmp = Files.createTempDirectory("core-data").toFile()
        try {
            val store = CoreDataStore(File(tmp, "root"))
            store.putFile("debug/asst.log", ByteArrayInputStream("log".toByteArray()))
            assertEquals(listOf("asst.log"), store.listDebugFiles())
            assertTrue(store.clear())
            assertFalse(store.root.exists())
            assertTrue(store.listDebugFiles().isEmpty())
        } finally {
            tmp.deleteRecursively()
        }
    }
}
