package com.aliothmoon.maameow.domain.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.ParcelFileDescriptor
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.constant.MaaFiles
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.manager.RemoteServiceManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 独立目录投递的决策表：什么时候推热更包、什么算失败、应用目录下绝不碰远端 */
class CoreDataPusherTest {

    private class Env(val root: File, val srv: RemoteService, val pusher: CoreDataPusher, val sent: MutableList<String>)

    @After
    fun tearDown() = unmockkAll()

    private fun env(
        separated: Boolean = true,
        localVersion: String? = "2026-09-05",
        remoteVersion: String = "2026-09-01",
        keepZip: Boolean = true,
        applyOk: Boolean = true,
    ): Env {
        val root = Files.createTempDirectory("pusher").toFile()
        File(root, "resource").mkdirs()
        localVersion?.let { File(root, "resource/version.json").writeText("{\"last_updated\":\"$it\"}") }
        if (keepZip) File(root, MaaFiles.LAST_RESOURCE_UPDATE_ZIP).writeBytes(byteArrayOf(1))

        val pathConfig = mockk<MaaPathConfig> {
            every { isCoreSeparated } returns separated
            every { rootDir } returns root.absolutePath
            every { appVersionCode } returns 7L
            every { readDiskResourceVersion() } returns localVersion
        }
        val context = mockk<Context> {
            every { applicationInfo } returns ApplicationInfo().apply { sourceDir = File(root, "base.apk").absolutePath }
        }
        val sent = mutableListOf<String>()
        val srv = mockk<RemoteService> {
            every { ensureCoreResources(any(), any()) } returns true
            every { coreResourceVersion } returns remoteVersion
            every { applyCoreHotUpdate(any()) } answers { sent += "zip"; applyOk }
            every { putCoreFile(any(), any()) } answers { sent += firstArg<String>(); true }
            every { clearCoreData() } returns true
            every { removeStaleCoreResourceConfigs(any()) } returns true
        }
        // JVM 上 android.jar 的 open 是 stub，直接给个可 close 的假 fd
        mockkStatic(ParcelFileDescriptor::class)
        every { ParcelFileDescriptor.open(any(), any()) } returns mockk(relaxed = true)
        mockkObject(RemoteServiceManager)
        every { RemoteServiceManager.getInstanceOrNull() } returns srv
        return Env(root, srv, CoreDataPusher(context, pathConfig), sent)
    }

    @Test
    fun appDir_neverTouchesRemote() = runBlocking {
        val e = env(separated = false)
        assertTrue(e.pusher.prepare(e.srv))
        assertTrue(e.pusher.pushUserData())
        e.pusher.pushHotUpdateIfNeeded()
        verify(exactly = 0) { e.srv.ensureCoreResources(any(), any()) }
        verify(exactly = 0) { e.srv.applyCoreHotUpdate(any()) }
        verify(exactly = 0) { e.srv.putCoreFile(any(), any()) }
        verify(exactly = 0) { e.srv.removeStaleCoreResourceConfigs(any()) }
    }

    @Test
    fun prepare_passesApkAndStamp() = runBlocking {
        val e = env(remoteVersion = "2026-09-05")
        assertTrue(e.pusher.prepare(e.srv))
        val apk = File(e.root, "base.apk").absolutePath
        verify(exactly = 1) { e.srv.ensureCoreResources(apk, "7:${File(apk).lastModified()}") }
    }

    @Test
    fun hotUpdate_skippedWhenVersionsMatch() = runBlocking {
        val e = env(remoteVersion = "2026-09-05")
        assertTrue(e.pusher.prepare(e.srv))
        assertTrue(e.sent.none { it == "zip" })
    }

    @Test
    fun hotUpdate_pushedWhenVersionsDiffer_andZipKept() = runBlocking {
        val e = env()
        assertTrue(e.pusher.prepare(e.srv))
        assertEquals(listOf("zip"), e.sent)
    }

    @Test
    fun hotUpdate_notFatalWithoutZip() = runBlocking {
        val e = env(keepZip = false)
        assertTrue(e.pusher.prepare(e.srv))
        assertTrue(e.sent.isEmpty())
    }

    @Test
    fun hotUpdate_pushFailure_failsPrepare() = runBlocking {
        val e = env(applyOk = false)
        assertFalse(e.pusher.prepare(e.srv))
    }

    @Test
    fun ensureResourcesFailure_failsPrepare() = runBlocking {
        val e = env()
        every { e.srv.ensureCoreResources(any(), any()) } returns false
        assertFalse(e.pusher.prepare(e.srv))
        assertTrue(e.sent.isEmpty())
    }

    @Test
    fun userData_pushedWithRelativePaths() = runBlocking {
        val e = env(remoteVersion = "2026-09-05")
        File(e.root, "copilot").mkdirs()
        File(e.root, "copilot/1.json").writeText("{}")
        File(e.root, "custom_infrast/sub").mkdirs()
        File(e.root, "custom_infrast/sub/plan.json").writeText("{}")
        File(e.root, "debug").mkdirs()
        File(e.root, "debug/asst.log").writeText("no")
        assertTrue(e.pusher.pushUserData())
        assertEquals(listOf("copilot/1.json", "custom_infrast/sub/plan.json"), e.sent.sorted())
    }

    @Test
    fun manualConfigsAreSentEvenWhenResourceVersionsMatch() = runBlocking {
        val e = env(remoteVersion = "2026-09-05")
        val paths = listOf(
            "resource/config.json",
            "cache/resource/config.json",
            "resource/global/YoStarKR/resource/config.json",
            "cache/resource/global/YoStarKR/resource/config.json",
            "overrides/resource/config.json",
        )
        paths.forEach { path ->
            File(e.root, path).apply {
                parentFile!!.mkdirs()
                writeText("""{"packageName":{"YoStarKR":"com.YoStarKR.Samsung.Arknights"}}""")
            }
        }
        try {
            assertTrue(e.pusher.prepare(e.srv))
            assertEquals(paths.sorted(), e.sent.distinct().sorted())
            verify(exactly = 1) { e.srv.removeStaleCoreResourceConfigs(match { it.toSet() == paths.toSet() }) }
            verify(exactly = 0) { e.srv.applyCoreHotUpdate(any()) }
        } finally {
            e.root.deleteRecursively()
        }
    }

    @Test
    fun manualConfigTransferFailurePreventsPreparingCore() = runBlocking {
        val e = env(remoteVersion = "2026-09-05")
        File(e.root, "resource/config.json").writeText("{}")
        every { e.srv.putCoreFile("resource/config.json", any()) } returns false
        try {
            assertFalse(e.pusher.prepare(e.srv))
        } finally {
            e.root.deleteRecursively()
        }
    }

    @Test
    fun deletedConfigIsReconciledAfterPusherRecreation() = runBlocking {
        val e = env(remoteVersion = "2026-09-05")
        val path = "cache/resource/global/YoStarKR/resource/config.json"
        val config = File(e.root, path).apply { parentFile!!.mkdirs(); writeText("{}") }
        try {
            assertTrue(e.pusher.prepare(e.srv))
            verify(exactly = 1) { e.srv.removeStaleCoreResourceConfigs(listOf(path)) }
            assertTrue(config.delete())
            val context = mockk<Context> {
                every { applicationInfo } returns ApplicationInfo().apply { sourceDir = File(e.root, "base.apk").path }
            }
            val paths = mockk<MaaPathConfig> {
                every { isCoreSeparated } returns true
                every { rootDir } returns e.root.path
                every { appVersionCode } returns 7L
                every { readDiskResourceVersion() } returns "2026-09-05"
            }
            assertTrue(CoreDataPusher(context, paths).prepare(e.srv))
            verify(exactly = 1) { e.srv.removeStaleCoreResourceConfigs(emptyList()) }
        } finally {
            e.root.deleteRecursively()
        }
    }

    @Test
    fun configCleanupFailurePreventsPreparingCore() = runBlocking {
        val e = env(remoteVersion = "2026-09-05")
        every { e.srv.removeStaleCoreResourceConfigs(any()) } returns false
        try {
            assertFalse(e.pusher.prepare(e.srv))
        } finally {
            e.root.deleteRecursively()
        }
    }

    @Test
    fun cachedTaskTransferFailurePreventsPreparingCore() = runBlocking {
        val e = env(remoteVersion = "2026-09-05")
        try {
            File(e.root, "cache/resource/tasks/tasks.json").apply {
                parentFile!!.mkdirs()
                writeText("{}")
            }
            every { e.srv.putCoreFile("cache/resource/tasks/tasks.json", any()) } returns false
            assertFalse(e.pusher.prepare(e.srv))
        } finally {
            e.root.deleteRecursively()
        }
    }

    @Test
    fun clearRemote_ignoresMode() = runBlocking {
        val e = env(separated = false)
        assertTrue(e.pusher.clearRemote())
        verify(exactly = 1) { e.srv.clearCoreData() }
    }
}
