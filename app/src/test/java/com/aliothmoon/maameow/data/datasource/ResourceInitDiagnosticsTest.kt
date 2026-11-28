package com.aliothmoon.maameow.data.datasource

import android.content.Context
import android.content.res.AssetManager
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.service.LogExportService
import com.aliothmoon.maameow.schedule.service.ScheduleAlarmManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.io.InputStream
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

class ResourceInitDiagnosticsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun assetExtractorFailure_recordsPhaseProbesAndPersistsToInternalFilesDir() = runBlocking {
        val internalFiles = tempFolder.newFolder("files")
        val destDir = tempFolder.newFolder("resource")
        // 在目标子路径预先占一个同名目录，触发 FileOutputStream 阶段失败
        val conflictTarget = File(destDir, "Arknights-Tile-Pos/a001_05.json")
        conflictTarget.parentFile!!.mkdirs()
        conflictTarget.mkdirs()

        val manifestJson = """{"files":["MaaSync/MaaResource/Arknights-Tile-Pos/a001_05.json"]}"""
        val assets = mockk<AssetManager> {
            every { open("MaaSync/asset_manifest.json") } answers {
                ByteArrayInputStream(manifestJson.toByteArray())
            }
            every { open("MaaSync/MaaResource/Arknights-Tile-Pos/a001_05.json") } answers {
                ByteArrayInputStream("""{"ok":true}""".toByteArray())
            }
            every { list("MaaSync/MaaResource/Arknights-Tile-Pos") } returns arrayOf("a001_05.json")
        }
        val context = mockk<Context>(relaxed = true) {
            every { this@mockk.assets } returns assets
            every { filesDir } returns internalFiles
            every { cacheDir } returns tempFolder.newFolder("cache")
        }

        val diagnostics = ResourceInitDiagnostics(context)
        val extractor = AssetExtractor(context, diagnostics)

        val result = extractor.extract("MaaSync/MaaResource", destDir) {}
        assertTrue(result.isFailure)

        val report = diagnostics.readReport()
        assertNotNull(report)
        assertTrue(report!!.contains("phase=FileOutputStream"))
        assertTrue(report.contains("MaaSync/MaaResource/Arknights-Tile-Pos/a001_05.json"))
        assertTrue(report.contains("assetOpenProbe    : OK"))
        assertTrue(report.contains("syncCopyProbe(dst): OK"))

        // 模拟进程重启后用新实例从内部 filesDir 读回
        val persisted = ResourceInitDiagnostics(context).readReport()
        assertNotNull(persisted)
        assertTrue(persisted!!.contains("Resource Init Run #1 (FAILED)"))
        assertTrue(persisted.contains("externalPkgPresent:"))
    }

    @Test
    fun failureInNewProcess_keepsReportsPersistedByPreviousProcess() {
        val internalFiles = tempFolder.newFolder("files")
        val destDir = tempFolder.newFolder("resource")
        val context = mockk<Context>(relaxed = true) {
            every { filesDir } returns internalFiles
            every { cacheDir } returns tempFolder.newFolder("cache")
        }

        ResourceInitDiagnostics(context).beginDirectRun(destDir)
            .finishFailure(IOException("first-process"), 0, 0)
        ResourceInitDiagnostics(context).beginDirectRun(destDir)
            .finishFailure(IOException("second-process"), 0, 0)

        val persisted = File(internalFiles, ResourceInitDiagnostics.ENTRY_NAME).readText()
        assertEquals(2, Regex("=== Resource Init Run #1 \\(FAILED\\)").findAll(persisted).count())
        assertTrue(persisted.contains("first-process"))
        assertTrue(persisted.contains("second-process"))
    }

    @Test
    fun assetOpenFailure_recordsAssetsOpenPhase() = runBlocking {
        val internalFiles = tempFolder.newFolder("files2")
        val destDir = tempFolder.newFolder("resource2")
        val manifestJson = """{"files":["MaaSync/MaaResource/broken.json"]}"""
        val assets = mockk<AssetManager> {
            every { open("MaaSync/asset_manifest.json") } answers {
                ByteArrayInputStream(manifestJson.toByteArray())
            }
            every { open("MaaSync/MaaResource/broken.json") } throws IOException("corrupt entry")
            every { list("MaaSync/MaaResource") } returns emptyArray()
        }
        val context = mockk<Context>(relaxed = true) {
            every { this@mockk.assets } returns assets
            every { filesDir } returns internalFiles
            every { cacheDir } returns tempFolder.newFolder("cache2")
        }

        val diagnostics = ResourceInitDiagnostics(context)
        val result = AssetExtractor(context, diagnostics).extract("MaaSync/MaaResource", destDir) {}
        assertTrue(result.isFailure)

        val report = diagnostics.readReport()!!
        assertTrue(report.contains("phase=assets.open"))
        assertTrue(report.contains("corrupt entry"))
        assertTrue(report.contains("assetOpenProbe    : FAILED"))
    }

    @Test
    fun assetExtractorSuccess_doesNotPersistDiagnosticFile() = runBlocking {
        val internalFiles = tempFolder.newFolder("files_ok")
        val destDir = tempFolder.newFolder("resource_ok")
        val manifestJson = """{"files":["MaaSync/MaaResource/ok.json"]}"""
        val assets = mockk<AssetManager> {
            every { open("MaaSync/asset_manifest.json") } answers {
                ByteArrayInputStream(manifestJson.toByteArray())
            }
            every { open("MaaSync/MaaResource/ok.json") } answers {
                ByteArrayInputStream("""{"ok":true}""".toByteArray())
            }
        }
        val context = mockk<Context>(relaxed = true) {
            every { this@mockk.assets } returns assets
            every { filesDir } returns internalFiles
            every { cacheDir } returns tempFolder.newFolder("cache_ok")
        }

        val diagnostics = ResourceInitDiagnostics(context)
        val result = AssetExtractor(context, diagnostics).extract("MaaSync/MaaResource", destDir) {}
        assertTrue(result.isSuccess)
        assertNull(diagnostics.readReport())
        assertFalse(File(internalFiles, ResourceInitDiagnostics.ENTRY_NAME).exists())
    }

    @Test
    fun subsequentSuccess_clearsPreviousFailedReportFromMemoryAndDisk() = runBlocking {
        val internalFiles = tempFolder.newFolder("files_clear")
        val destDir = tempFolder.newFolder("resource_clear")
        val manifestJson = """{"files":["MaaSync/MaaResource/a.json"]}"""
        var shouldFail = true
        val assets = mockk<AssetManager> {
            every { open("MaaSync/asset_manifest.json") } answers {
                ByteArrayInputStream(manifestJson.toByteArray())
            }
            every { open("MaaSync/MaaResource/a.json") } answers {
                if (shouldFail) throw IOException("transient")
                ByteArrayInputStream("""{"ok":true}""".toByteArray())
            }
            every { list("MaaSync/MaaResource") } returns arrayOf("a.json")
        }
        val context = mockk<Context>(relaxed = true) {
            every { this@mockk.assets } returns assets
            every { filesDir } returns internalFiles
            every { cacheDir } returns tempFolder.newFolder("cache_clear")
        }

        val diagnostics = ResourceInitDiagnostics(context)
        val extractor = AssetExtractor(context, diagnostics)

        assertTrue(extractor.extract("MaaSync/MaaResource", destDir) {}.isFailure)
        assertNotNull(diagnostics.readReport())
        assertTrue(File(internalFiles, ResourceInitDiagnostics.ENTRY_NAME).isFile)

        shouldFail = false
        assertTrue(extractor.extract("MaaSync/MaaResource", destDir) {}.isSuccess)
        assertNull(diagnostics.readReport())
        assertFalse(File(internalFiles, ResourceInitDiagnostics.ENTRY_NAME).exists())
    }

    @Test
    fun classifyError_usesStableNamesAndHidesCustomExceptions() {
        class CustomInternalFailure : RuntimeException("boom")

        assertEquals("IOException" to null, ResourceInitDiagnostics.classifyError(IOException("x")))
        assertEquals(
            "SerializationException" to null,
            ResourceInitDiagnostics.classifyError(SerializationException("bad json")),
        )
        assertEquals("AppException" to null, ResourceInitDiagnostics.classifyError(CustomInternalFailure()))
    }

    @Test
    fun midCopyFailure_recordsPartialBytesCopied() = runBlocking {
        val internalFiles = tempFolder.newFolder("files_mid")
        val destDir = tempFolder.newFolder("resource_mid")
        val manifestJson = """{"files":["MaaSync/MaaResource/partial.json"]}"""
        val assets = mockk<AssetManager> {
            every { open("MaaSync/asset_manifest.json") } answers {
                ByteArrayInputStream(manifestJson.toByteArray())
            }
            every { open("MaaSync/MaaResource/partial.json") } answers {
                object : InputStream() {
                    private var chunkSent = false
                    override fun read(): Int = -1
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        if (!chunkSent) {
                            chunkSent = true
                            val payload = "hello-partial".toByteArray()
                            payload.copyInto(b, off)
                            return payload.size
                        }
                        throw IOException("disk read error mid-stream")
                    }
                }
            }
            every { list("MaaSync/MaaResource") } returns arrayOf("partial.json")
        }
        val context = mockk<Context>(relaxed = true) {
            every { this@mockk.assets } returns assets
            every { filesDir } returns internalFiles
            every { cacheDir } returns tempFolder.newFolder("cache_mid")
        }

        val diagnostics = ResourceInitDiagnostics(context)
        assertTrue(AssetExtractor(context, diagnostics).extract("MaaSync/MaaResource", destDir) {}.isFailure)
        val report = diagnostics.readReport()!!
        assertTrue(report.contains("phase=copy bytes=13"))
    }

    @Test
    fun logExportService_fallsBackToCacheDirAndIncludesInitDiagnostics() = runBlocking {
        val internalFiles = tempFolder.newFolder("files3")
        val internalCache = tempFolder.newFolder("cache3")
        // 让 debugDir 指向普通文件，模拟外置目录不可建/不可写
        val blockedDebugPath = tempFolder.newFile("blocked_debug")

        val context = mockk<Context>(relaxed = true) {
            every { filesDir } returns internalFiles
            every { cacheDir } returns internalCache
            every { packageName } returns "com.aliothmoon.maameow"
        }
        val diagnostics = ResourceInitDiagnostics(context)
        File(internalFiles, ResourceInitDiagnostics.ENTRY_NAME).writeText("DIAG_MARKER_123")

        val pathConfig = mockk<MaaPathConfig>(relaxed = true) {
            every { debugDir } returns blockedDebugPath.absolutePath
            every { rootDir } returns tempFolder.root.absolutePath
            every { isCoreSeparated } returns false
        }
        val appSettings = mockk<AppSettingsManager>(relaxed = true) {
            every { settings } returns MutableStateFlow(mockk(relaxed = true))
        }
        val taskChainState = mockk<TaskChainState>(relaxed = true)
        val achievements = mockk<AchievementRepository>(relaxed = true) {
            coEvery { report(any()) } returns Unit
        }
        val alarmManager = mockk<ScheduleAlarmManager>(relaxed = true)

        val service = LogExportService(
            context = context,
            pathConfig = pathConfig,
            appSettingsManager = appSettings,
            taskChainState = taskChainState,
            achievementRepository = achievements,
            scheduleAlarmManager = alarmManager,
            applicationLogWriter = null,
            resourceInitDiagnostics = diagnostics,
        )

        val zipFile = service.exportZip()
        assertNotNull(zipFile)
        assertTrue(zipFile!!.absolutePath.startsWith(internalCache.absolutePath))

        ZipFile(zipFile).use { zf ->
            val diagEntry = zf.getEntry(ResourceInitDiagnostics.ENTRY_NAME)
            assertNotNull(diagEntry)
            val content = zf.getInputStream(diagEntry).bufferedReader().use { it.readText() }
            assertTrue(content.contains("DIAG_MARKER_123"))
        }
    }
}
