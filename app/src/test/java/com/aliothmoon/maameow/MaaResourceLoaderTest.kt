package com.aliothmoon.maameow

import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.config.ResourceConfigEditor
import com.aliothmoon.maameow.data.config.GamePackageConfig
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.ParcelFileDescriptor
import com.aliothmoon.maameow.remote.internal.CoreDataStore
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.AppAliveChecker
import com.aliothmoon.maameow.domain.usecase.CheckGameReadinessUseCase
import com.aliothmoon.maameow.domain.usecase.GameReadiness
import com.aliothmoon.maameow.domain.usecase.TaskStartContext
import com.aliothmoon.maameow.domain.usecase.TaskStartMode
import com.aliothmoon.maameow.remote.AppAliveStatus
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import android.os.IBinder
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.models.ClientResourceProfile
import com.aliothmoon.maameow.domain.service.CoreDataPusher
import com.aliothmoon.maameow.domain.service.MaaResourceLoader
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.remote.CoreResourceConfigs
import com.aliothmoon.maameow.remote.SetupResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

class MaaResourceLoaderTest {

    @After
    fun tearDown() {
        unmockkObject(RemoteServiceManager)
        Packages.initialize(null)
    }

    @Test
    fun load_refreshesHotUpdate_forClient() = runBlocking {
        withEnv(AppSettingsManager.AppLanguage.EN) { env ->
            val result = env.loader.load("YoStarEN")

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { env.activityManager.load("YoStarEN") }
        }
    }

    // 日服写入 MaaCore 的键在进程内不可回滚，换资源档必须换提权进程

    // 提权进程读写不了根目录（#227）：不能再往下 LoadResource，且要标成需换存储位置的永久失败

    @Test
    fun load_marksStorageInaccessible_whenRemoteSetupRejectsUserDir() = runBlocking {
        withEnv(setupCode = SetupResult.ERR_USER_DIR_INACCESSIBLE) { env ->
            val result = env.loader.load("Official")

            assertTrue(result.isFailure)
            val state = env.loader.state.value
            assertTrue(state is MaaResourceLoader.State.Failed)
            state as MaaResourceLoader.State.Failed
            assertTrue(state.permanent)
            assertEquals(MaaResourceLoader.State.FailReason.STORAGE_INACCESSIBLE, state.reason)
            assertTrue(env.loadedDirs.isEmpty())
        }
    }

    @Test
    fun load_usesAppDir_byDefault() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("Official").isSuccess)
            assertTrue(env.loadedDirs.isNotEmpty())
            assertTrue(env.loadedDirs.none { it.startsWith("/data/local/tmp") })
        }
    }

    @Test
    fun load_passesLocalTmpPaths_whenCoreSeparated() = runBlocking {
        withEnv(coreSeparated = true) { env ->
            assertTrue(env.loader.load("Official").isSuccess)
            assertTrue(env.loadedDirs.isNotEmpty())
            assertTrue(env.loadedDirs.all { it.startsWith("/data/local/tmp/maameow") })
            coVerify(exactly = 1) { env.coreDataPusher.prepare(any()) }
        }
    }

    @Test
    fun load_failsRetryable_whenCoreDataPrepareFails() = runBlocking {
        withEnv(coreSeparated = true) { env ->
            coEvery { env.coreDataPusher.prepare(any()) } returns false
            assertTrue(env.loader.load("Official").isFailure)
            val state = env.loader.state.value as MaaResourceLoader.State.Failed
            assertFalse(state.permanent)
            assertTrue(env.loadedDirs.isEmpty())
        }
    }

    @Test
    fun load_keepsGenericSetupFailureRetryable() = runBlocking {
        withEnv(setupCode = SetupResult.ERR_CORE_NOT_LOADED) { env ->
            assertTrue(env.loader.load("Official").isFailure)
            val state = env.loader.state.value as MaaResourceLoader.State.Failed
            assertFalse(state.permanent)
            assertEquals(MaaResourceLoader.State.FailReason.GENERIC, state.reason)
        }
    }

    @Test
    fun resourceProfile_groupsBaseOnlyClientsTogether() {
        assertEquals("", ClientResourceProfile.of(""))
        assertEquals("", ClientResourceProfile.of("Official"))
        assertEquals("", ClientResourceProfile.of("Bilibili"))
        assertEquals("YoStarJP", ClientResourceProfile.of("YoStarJP"))
        assertEquals("YoStarEN", ClientResourceProfile.of("YoStarEN"))
        assertEquals("txwy", ClientResourceProfile.of("txwy"))
    }

    @Test
    fun requiresServiceRestart_onlyWhenResourceProfileChanges() {
        // 全新进程没什么可污染的
        assertFalse(MaaResourceLoader.requiresServiceRestart(null, "Official"))
        assertFalse(MaaResourceLoader.requiresServiceRestart(null, "YoStarJP"))
        // 官服/B服共用 base
        assertFalse(MaaResourceLoader.requiresServiceRestart("Official", "Bilibili"))
        assertFalse(MaaResourceLoader.requiresServiceRestart("Bilibili", "Official"))
        assertFalse(MaaResourceLoader.requiresServiceRestart("YoStarJP", "YoStarJP"))
        // 复现路径
        assertTrue(MaaResourceLoader.requiresServiceRestart("YoStarJP", "Official"))
        assertTrue(MaaResourceLoader.requiresServiceRestart("YoStarJP", "Bilibili"))
        // 反向也脏，公招 tag 的 static 缓存双向失效
        assertTrue(MaaResourceLoader.requiresServiceRestart("Official", "YoStarJP"))
        // global 之间也互相残留
        assertTrue(MaaResourceLoader.requiresServiceRestart("YoStarJP", "YoStarEN"))
    }

    @Test
    fun load_restartsElevatedService_whenSwitchingFromGlobalClientToOfficial() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("YoStarJP").isSuccess)
            verify(exactly = 0) { RemoteServiceManager.unbind() }

            assertTrue(env.loader.load("Official").isSuccess)
            verify(exactly = 1) { RemoteServiceManager.unbind() }
        }
    }

    @Test
    fun load_keepsElevatedService_whenSwitchingBetweenBaseOnlyClients() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("Official").isSuccess)
            assertTrue(env.loader.load("Bilibili").isSuccess)
            assertTrue(env.loader.load("Official").isSuccess)
            verify(exactly = 0) { RemoteServiceManager.unbind() }
        }
    }

    @Test
    fun load_dropsGlobalOverride_afterSwitchingBackToOfficial() = runBlocking {
        withEnv { env ->
            env.loader.load("YoStarJP")
            assertTrue(env.loadedDirs.any { it.contains("YoStarJP") })

            env.loadedDirs.clear()
            env.loader.load("Official")
            // 切回国服不能再带上日服覆盖
            assertTrue(env.loadedDirs.none { it.contains("YoStarJP") })

            env.loadedDirs.clear()
            env.loader.load("YoStarJP")
            assertTrue(env.loadedDirs.any { it.contains("YoStarJP") })
            verify(exactly = 2) { RemoteServiceManager.unbind() }
        }
    }

    @Test
    fun ensureLoaded_reloads_whenRequestedClientDiffersFromLoadedOne() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("YoStarJP").isSuccess)
            assertTrue(env.loader.state.value is MaaResourceLoader.State.Ready)

            // Ready 但客户端对不上，不能直接放行
            assertTrue(env.loader.ensureLoaded("Official").isSuccess)
            verify(exactly = 1) { RemoteServiceManager.unbind() }

            // 同一资源档不该重复加载
            env.loadedDirs.clear()
            assertTrue(env.loader.ensureLoaded("Bilibili").isSuccess)
            verify(exactly = 1) { RemoteServiceManager.unbind() }
            assertTrue(env.loadedDirs.isEmpty())
        }
    }

    @Test
    fun reset_forgetsLoadedClient_soNextLoadDoesNotRestart() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("YoStarJP").isSuccess)
            // 服务死亡后新进程是干净的，不该再多重启一次
            env.loader.reset()
            assertTrue(env.loader.state.value is MaaResourceLoader.State.NotLoaded)

            assertTrue(env.loader.load("Official").isSuccess)
            verify(exactly = 0) { RemoteServiceManager.unbind() }
        }
    }

    @Test
    fun load_dropsReadyState_whenServiceDoesNotComeBackAfterProfileSwitch() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("YoStarJP").isSuccess)
            assertTrue(env.loader.state.value is MaaResourceLoader.State.Ready)

            // 解绑后服务没起来，不能停在 Ready
            env.rebindFails.set(true)
            assertTrue(env.loader.load("Official").isFailure)
            assertTrue(env.loader.state.value is MaaResourceLoader.State.NotLoaded)

            // 服务恢复后重新完整加载，不该再多重启一次
            env.rebindFails.set(false)
            env.loadedDirs.clear()
            assertTrue(env.loader.load("Official").isSuccess)
            assertTrue(env.loadedDirs.isNotEmpty())
            verify(exactly = 1) { RemoteServiceManager.unbind() }
        }
    }

    @Test
    fun invalidate_keepsLoadedClient_soProfileSwitchStillRestarts() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("YoStarJP").isSuccess)

            // 覆盖开关之类只是让资源失效，进程还活着且带着日服残留
            env.loader.invalidate()
            assertTrue(env.loader.state.value is MaaResourceLoader.State.NotLoaded)

            // 所以切回国服依然必须换进程
            assertTrue(env.loader.load("Official").isSuccess)
            verify(exactly = 1) { RemoteServiceManager.unbind() }
        }
    }

    @Test
    fun invalidate_doesNotRestart_whenClientUnchanged() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("YoStarJP").isSuccess)
            env.loader.invalidate()

            env.loadedDirs.clear()
            assertTrue(env.loader.load("YoStarJP").isSuccess)
            // 同一档位不该白白换进程
            verify(exactly = 0) { RemoteServiceManager.unbind() }
            assertTrue(env.loadedDirs.any { it.contains("YoStarJP") })
        }
    }

    // Shizuku 重启后直接 Connected -> Connecting -> Connected，新进程没加载过资源
    @Test
    fun ensureLoaded_reloads_whenServiceReplacedWithoutDisconnect() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("Official").isSuccess)
            env.replaceService()
            env.loadedDirs.clear()

            assertTrue(env.loader.ensureLoaded("Official").isSuccess)
            assertTrue(env.loadedDirs.isNotEmpty())
            // 新进程是干净的，不该再换一次
            verify(exactly = 0) { RemoteServiceManager.unbind() }
        }
    }

    @Test
    fun resetIfStale_keepsReady_forSameService() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("Official").isSuccess)
            assertFalse(env.loader.resetIfStale(RemoteServiceManager.getInstanceOrNull()!!))
            assertTrue(env.loader.state.value is MaaResourceLoader.State.Ready)
        }
    }

    @Test
    fun resetIfStale_dropsReady_whenServiceReplaced() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("YoStarJP").isSuccess)
            val fresh = env.replaceService()

            assertTrue(env.loader.resetIfStale(fresh))
            assertTrue(env.loader.state.value is MaaResourceLoader.State.NotLoaded)
            // 旧进程的资源档也一并作废，换客户端不该多重启
            assertTrue(env.loader.load("Official").isSuccess)
            verify(exactly = 0) { RemoteServiceManager.unbind() }
        }
    }

    @Test
    fun manualConfigUsesExistingLoadPaths_inBothStorageModes() = runBlocking {
        for (separated in listOf(false, true)) {
            withEnv(coreSeparated = separated, customPackages = true) { env ->
                val original = File(env.rootDir, "resource/config.json").readText()
                assertTrue(env.loader.load("YoStarKR").isSuccess)
                val expectedRoot = if (separated) "/data/local/tmp/maameow" else env.rootDir.absolutePath
                assertEquals(expectedRoot, env.loadedDirs.first())
                assertTrue(env.loadedDirs.none { it.contains("game-package") })
                assertEquals("com.YoStarKR.Samsung.Arknights", Packages["YoStarKR"])
                assertEquals(original, File(env.rootDir, "resource/config.json").readText())
                File(env.rootDir, "resource/config.json").writeText("""{"packageName":{"YoStarKR":"changed.after.load"}}""")
                assertEquals("com.YoStarKR.Samsung.Arknights", Packages["YoStarKR"])
            }
            unmockkObject(RemoteServiceManager)
        }
    }

    @Test
    fun rejectedGlobalResourcePreventsPublishingPackageSnapshot() = runBlocking {
        withEnv(customPackages = true, rejectGlobalResource = true) { env ->
            assertTrue(env.loader.load("YoStarKR").isFailure)
            assertTrue(env.loader.state.value is MaaResourceLoader.State.Failed)
            File(env.rootDir, "resource/config.json").writeText("""{"packageName":{"YoStarKR":"changed.after.failure"}}""")
            assertEquals("changed.after.failure", Packages["YoStarKR"])
        }
    }

    @Test
    fun rejectedResourcePreservesPreviouslyPublishedPackageSnapshot() = runBlocking {
        withEnv(customPackages = true, rejectGlobalResource = true) { env ->
            Packages.onResourcesLoaded("YoStarKR", mapOf("YoStarKR" to "previous.success"))
            assertTrue(env.loader.load("YoStarKR").isFailure)
            File(env.rootDir, "resource/config.json").writeText("""{"packageName":{"YoStarKR":"changed.after.failure"}}""")
            assertEquals("previous.success", Packages["YoStarKR"])
        }
    }

    @Test
    fun overrideConfigMatchesLastCoreLayerInBothStorageModes() = runBlocking {
        for (separated in listOf(false, true)) {
            withEnv(coreSeparated = separated, customPackages = true, overridesEnabled = true) { env ->
                val override = File(env.rootDir, "overrides/resource/config.json")
                override.writeText("""{"packageName":{"YoStarKR":"override.game"}}""")
                assertTrue(env.loader.load("YoStarKR").isSuccess)
                val expectedRoot = if (separated) "/data/local/tmp/maameow" else env.rootDir.absolutePath
                assertEquals("${expectedRoot.replace('\\', '/')}/overrides", env.loadedDirs.last().replace('\\', '/'))
                assertEquals("override.game", Packages["YoStarKR"])
                override.writeText("""{"packageName":{"YoStarKR":"changed.game"}}""")
                assertEquals("override.game", Packages["YoStarKR"])
            }
            unmockkObject(RemoteServiceManager)
        }
    }

    @Test
    fun disabledOverrideDoesNotChangePackageOrCoreLoadPaths() = runBlocking {
        withEnv(customPackages = true) { env ->
            File(env.rootDir, "overrides/resource/config.json").apply {
                parentFile!!.mkdirs()
                writeText("""{"packageName":{"YoStarKR":"override.game"}}""")
            }
            assertTrue(env.loader.load("YoStarKR").isSuccess)
            assertEquals("com.YoStarKR.Samsung.Arknights", Packages["YoStarKR"])
            assertTrue(env.loadedDirs.none { it.replace('\\', '/').endsWith("/overrides") })
        }
    }

    @Test
    fun rejectedOverrideResourcePreventsPublishingPackageSnapshot() = runBlocking {
        withEnv(customPackages = true, overridesEnabled = true, rejectOverrideResource = true) { env ->
            assertTrue(env.loader.load("YoStarKR").isFailure)
            assertTrue(env.loader.state.value is MaaResourceLoader.State.Failed)
            File(env.rootDir, "resource/config.json").writeText("""{"packageName":{"YoStarKR":"after.failure"}}""")
            assertEquals("after.failure", Packages["YoStarKR"])
        }
    }

    @Test
    fun freshSeparatedStoreReceivesCachedTasksBeforeCoreLoadsThem() = runBlocking {
        withEnv(coreSeparated = true, syncCachedResources = true) { env ->
            assertFalse(File(env.rootDir, "remote/cache/resource").exists())
            File(env.rootDir, "cache/resource/tasks.json").writeText("""{"downloaded":{}}""")
            File(env.rootDir, "cache/resource/template/event.png").apply {
                parentFile!!.mkdirs()
                writeText("event image")
            }
            assertTrue(env.loader.load("Official").isSuccess)
            assertTrue(env.loadedDirs.any { it.replace('\\', '/').endsWith("/cache") })
            assertEquals("""{"downloaded":{}}""",
                File(env.rootDir, "remote/cache/resource/tasks/tasks.json").readText())
            assertEquals("event image", File(env.rootDir, "remote/cache/resource/template/event.png").readText())
        }
    }

    @Test
    fun invalidatedOverrideUsesPendingPackageForScheduledReadinessAndKeepsActiveTarget() = runBlocking {
        withEnv(customPackages = true, overridesEnabled = true) { env ->
            File(env.rootDir, "overrides/resource/config.json")
                .writeText("""{"packageName":{"YoStarKR":"old.channel"}}""")
            assertTrue(env.loader.load("YoStarKR").isSuccess)
            assertEquals("old.channel", Packages.forStartup("YoStarKR"))
            env.tasksOverrides.value = false
            assertEquals("old.channel", Packages.forStartup("YoStarKR"))
            env.loader.invalidate()
            // A current run must retain the package loaded into its Core process.
            assertEquals("old.channel", Packages["YoStarKR"])
            val checked = mutableListOf<String>()
            val readiness = CheckGameReadinessUseCase(
                appAliveChecker = mockk<AppAliveChecker> {
                    coEvery { isAppAlive(any()) } returns AppAliveStatus.ALIVE
                },
                appSettings = mockk { every { runMode } returns MutableStateFlow(RunMode.FOREGROUND) },
                achievementReporter = mockk<AchievementReporter>(relaxed = true),
                isPackageInstalled = { checked += it; it == "com.YoStarKR.Samsung.Arknights" },
                isEyeProtectionEnabled = { false },
                isSmartResolutionEnabled = { false },
                isAdbInputRestricted = { false },
            )
            for (mode in listOf(TaskStartMode.SCHEDULED, TaskStartMode.MANUAL)) {
                assertTrue(readiness("YoStarKR", true, TaskStartContext(mode)) is GameReadiness.Ready)
            }
            assertEquals(List(2) { "com.YoStarKR.Samsung.Arknights" }, checked)
            assertTrue(env.loader.ensureLoaded("YoStarKR").isSuccess)
            assertEquals("com.YoStarKR.Samsung.Arknights", Packages["YoStarKR"])
        }
    }

    @Test
    fun dedicatedPackageConfigIsLoadedLastAndFrozenInBothStorageModes() = runBlocking {
        for (separated in listOf(false, true)) {
            withEnv(coreSeparated = separated, customPackages = true, syncCachedResources = separated, validateNativeConfigs = true) { env ->
                File(env.rootDir, "cache/resource/tasks.json").writeText("{}")
                File(env.rootDir, "cache/resource/global/YoStarKR/resource/tasks.json").writeText("{}")
                val standard = File(env.rootDir, "resource/config.json")
                val original = standard.readBytes()
                val editor = ResourceConfigEditor(env.rootDir)
                val source = File(env.rootDir, ResourceConfigEditor.PACKAGE_FILE)
                val coreRoot = if (separated) File(env.rootDir, "remote") else env.rootDir
                val nativeConfig = File(coreRoot, CoreResourceConfigs.PACKAGE_RESOURCE_CONFIG)
                editor.setEnabled(true)
                var loaded: String? = null
                for (name in listOf("com.example.notinstalled", "com.YoStarKR.Samsung.Arknights",
                    "com.example.notinstalled", "com.YoStarKR.Samsung.Arknights")) {
                    editor.save(ResourceConfigEditor.PACKAGE_FILE, """{"packageName":{"YoStarKR":"$name"}}""", "YoStarKR")
                    val saved = source.readBytes()
                    env.loader.invalidate()
                    if (loaded != null) assertEquals(loaded, Packages["YoStarKR"])
                    assertEquals(name, Packages.forStartup("YoStarKR"))
                    assertTrue(env.loader.ensureLoaded("YoStarKR").isSuccess)
                    assertEquals(name, Packages["YoStarKR"])
                    assertTrue(env.loadedDirs.last().replace('\\', '/').endsWith("/package-config"))
                    CoreConfigFixture.assertLoadable(nativeConfig)
                    assertTrue(nativeConfig.readText().contains(name))
                    assertArrayEquals(saved, source.readBytes())
                    loaded = name
                }
                assertArrayEquals(original, standard.readBytes())
                assertFalse(File(env.rootDir, "resource/config_custom.json").exists())
            }
            unmockkObject(RemoteServiceManager)
        }
    }

    @Test
    fun disablingResetAndDeletionRemoveGeneratedResourceInBothStorageModes() = runBlocking {
        for (separated in listOf(false, true)) {
            withEnv(coreSeparated = separated, customPackages = true, syncCachedResources = separated, validateNativeConfigs = true) { env ->
                File(env.rootDir, "cache/resource/tasks.json").writeText("{}")
                File(env.rootDir, "cache/resource/global/YoStarKR/resource/tasks.json").writeText("{}")
                val editor = ResourceConfigEditor(env.rootDir)
                val source = File(env.rootDir, ResourceConfigEditor.PACKAGE_FILE)
                val coreRoot = if (separated) File(env.rootDir, "remote") else env.rootDir
                val nativeConfig = File(coreRoot, CoreResourceConfigs.PACKAGE_RESOURCE_CONFIG)
                editor.setEnabled(true)
                editor.save(ResourceConfigEditor.PACKAGE_FILE, """{"packageName":{"YoStarKR":"com.example.notinstalled"}}""", "YoStarKR")
                assertTrue(env.loader.load("YoStarKR").isSuccess)
                val saved = source.readBytes()
                editor.setEnabled(false)
                env.loader.invalidate()
                assertEquals("com.example.notinstalled", Packages["YoStarKR"])
                val readiness = CheckGameReadinessUseCase(
                    appAliveChecker = mockk<AppAliveChecker> {
                        coEvery { isAppAlive(any()) } returns AppAliveStatus.ALIVE
                    },
                    appSettings = mockk { every { runMode } returns MutableStateFlow(RunMode.FOREGROUND) },
                    achievementReporter = mockk<AchievementReporter>(relaxed = true),
                    isPackageInstalled = { it == "com.YoStarKR.Samsung.Arknights" },
                    isEyeProtectionEnabled = { false },
                    isSmartResolutionEnabled = { false },
                    isAdbInputRestricted = { false },
                )
                assertTrue(readiness("YoStarKR", true, TaskStartContext(TaskStartMode.SCHEDULED)) is GameReadiness.Ready)
                assertTrue(env.loader.ensureLoaded("YoStarKR").isSuccess)
                assertEquals("com.YoStarKR.Samsung.Arknights", Packages["YoStarKR"])
                assertFalse(nativeConfig.exists())
                assertArrayEquals(saved, source.readBytes())
                editor.setEnabled(true)
                env.loader.invalidate()
                assertTrue(env.loader.ensureLoaded("YoStarKR").isSuccess)
                assertTrue(nativeConfig.isFile)
                editor.reset("YoStarKR")
                env.loader.invalidate()
                assertTrue(env.loader.ensureLoaded("YoStarKR").isSuccess)
                assertFalse(nativeConfig.exists())
                assertEquals("com.YoStarKR.Samsung.Arknights", Packages["YoStarKR"])
                editor.save(ResourceConfigEditor.PACKAGE_FILE, """{"packageName":{"YoStarKR":"saved.again"}}""", "YoStarKR")
                env.loader.invalidate()
                assertTrue(env.loader.ensureLoaded("YoStarKR").isSuccess)
                assertTrue(source.delete())
                env.loader.invalidate()
                assertTrue(env.loader.ensureLoaded("YoStarKR").isSuccess)
                assertFalse(nativeConfig.exists())
                assertEquals("com.YoStarKR.Samsung.Arknights", Packages["YoStarKR"])
            }
            unmockkObject(RemoteServiceManager)
        }
    }

    private class Env(
        val rootDir: File,
        val tasksOverrides: MutableStateFlow<Boolean>,
        val loader: MaaResourceLoader,
        val loadedDirs: MutableList<String>,
        val activityManager: ActivityManager,
        /** 置 true 模拟换进程后服务没能回来 */
        val rebindFails: AtomicBoolean,
        val coreDataPusher: CoreDataPusher,
        /** 换一个新提权进程并直接置 Connected，不经过 Disconnected */
        val replaceService: () -> RemoteService,
    )

    private suspend fun withEnv(
        appLanguage: AppSettingsManager.AppLanguage = AppSettingsManager.AppLanguage.ZH,
        setupCode: Int = SetupResult.OK,
        coreSeparated: Boolean = false,
        customPackages: Boolean = false,
        rejectGlobalResource: Boolean = false,
        overridesEnabled: Boolean = false,
        rejectOverrideResource: Boolean = false,
        syncCachedResources: Boolean = false,
        validateNativeConfigs: Boolean = false,
        block: suspend (Env) -> Unit,
    ) {
        val rootDir = Files.createTempDirectory("maa-resource-loader-test").toFile()
        try {
            File(rootDir, "resource").mkdirs()
            File(rootDir, "resource/config.json").writeText(if (validateNativeConfigs) CoreConfigFixture.text() else """{"packageName":{}}""")
            Packages.initialize(null)
            if (customPackages) {
                File(rootDir, "resource/config.json").writeText(if (validateNativeConfigs) {
                    CoreConfigFixture.text("""{"YoStarKR":"com.YoStarKR.Samsung.Arknights"}""")
                } else """{"packageName":{"YoStarKR":"com.YoStarKR.Samsung.Arknights"}}""")
                Packages.initialize(GamePackageConfig(rootDir))
            }
            File(rootDir, "cache/resource").mkdirs()
            if (overridesEnabled) File(rootDir, "overrides/resource").mkdirs()
            listOf("YoStarJP", "YoStarEN", "YoStarKR").forEach {
                File(rootDir, "resource/global/$it/resource").mkdirs()
                File(rootDir, "cache/resource/global/$it/resource").mkdirs()
            }

            val pathConfig = mockk<MaaPathConfig> {
                every { this@mockk.rootDir } returns rootDir.absolutePath
                every { isResourceReady } returns true
                every { cacheDir } returns File(rootDir, "cache").absolutePath
                every { cacheResourceDir } returns File(rootDir, "cache/resource").absolutePath
                every { overridesDir } returns File(rootDir, "overrides").absolutePath
                every { globalResourceDir(any()) } answers {
                    File(rootDir, "resource/global/${firstArg<String>()}/resource")
                }
                every { globalCacheResourceDir(any()) } answers {
                    File(rootDir, "cache/resource/global/${firstArg<String>()}/resource")
                }
            }
            val tasksOverrides = MutableStateFlow(overridesEnabled)
            val appSettings = mockk<AppSettingsManager> {
                every { debugMode } returns MutableStateFlow(false)
                every { language } returns MutableStateFlow(appLanguage)
                every { displayLanguage } returns ResourceDataManager.displayLanguageCode(appLanguage)
                every { forceFullscreenOnVirtualDisplay } returns MutableStateFlow(false)
                every { tasksOverrideEnabled } returns tasksOverrides
            }
            if (customPackages) Packages.initialize(GamePackageConfig(rootDir) { tasksOverrides.value })
            val chainState = mockk<TaskChainState> {
                every { clientType } returns "Official"
            }
            val activityManager = mockk<ActivityManager>()
            every { pathConfig.isCoreSeparated } returns coreSeparated
            every { pathConfig.coreRootDir } answers {
                if (coreSeparated) "/data/local/tmp/maameow" else rootDir.absolutePath
            }
            every { pathConfig.toCorePath(any()) } answers {
                if (coreSeparated) {
                    MaaPathConfig.toCorePath(firstArg(), rootDir.absolutePath, "/data/local/tmp/maameow")
                } else firstArg()
            }
            val remoteStore = CoreDataStore(File(rootDir, "remote"))
            val openedFiles = mutableMapOf<ParcelFileDescriptor, File>()
            val coreDataPusher = if (syncCachedResources) {
                every { pathConfig.appVersionCode } returns 7L
                every { pathConfig.readDiskResourceVersion() } returns "same.version"
                mockkStatic(ParcelFileDescriptor::class)
                every { ParcelFileDescriptor.open(any(), any()) } answers {
                    mockk<ParcelFileDescriptor>(relaxed = true).also { openedFiles[it] = firstArg() }
                }
                CoreDataPusher(mockk<Context> {
                    every { applicationInfo } returns ApplicationInfo().apply { sourceDir = File(rootDir, "base.apk").path }
                }, pathConfig)
            } else mockk<CoreDataPusher> {
                coEvery { prepare(any()) } returns true
            }
            val loadedDirs = mutableListOf<String>()

            coEvery { activityManager.load(any()) } returns Unit
            fun newService(): RemoteService {
                val maaCore = mockk<MaaCoreService>()
                every { maaCore.LoadResource(any()) } answers {
                    val path = firstArg<String>()
                    loadedDirs += path
                    if (validateNativeConfigs) {
                        val local = if (coreSeparated) File(remoteStore.root,
                            path.replace('\\', '/').removePrefix("/data/local/tmp/maameow").trimStart('/')) else File(path)
                        listOf("config.json").forEach { name ->
                            File(local, "resource/$name").takeIf { it.isFile }?.let { CoreConfigFixture.assertLoadable(it) }
                        }
                    }
                    val resourcePath = path.replace('\\', '/')
                    if (syncCachedResources) {
                        val relative = resourcePath.removePrefix("/data/local/tmp/maameow").trimStart('/')
                        check(File(remoteStore.root, "$relative/resource").isDirectory) {
                            "Core resource path missing: $path"
                        }
                    }
                    !(rejectGlobalResource && resourcePath.contains("global/YoStarKR")) &&
                            !(rejectOverrideResource && resourcePath.endsWith("/overrides"))
                }
                return mockk<RemoteService> {
                    if (syncCachedResources) {
                        every { ensureCoreResources(any(), any()) } answers {
                            File(remoteStore.root, "resource").mkdirs()
                            File(remoteStore.root, "resource/global/YoStarKR/resource").mkdirs()
                            true
                        }
                        every { coreResourceVersion } returns "same.version"
                        every { putCoreFile(any(), any()) } answers {
                            openedFiles.getValue(secondArg()).inputStream().use { remoteStore.putFile(firstArg(), it) }
                        }
                        every { removeStaleCoreResourceConfigs(any()) } answers {
                            remoteStore.removeStaleResourceConfigs(firstArg())
                        }
                    }
                    every { asBinder() } returns mockk<IBinder>()
                    every { setup(any(), any()) } returns setupCode
                    justRun { setForceFullscreenOnVirtualDisplay(any()) }
                    every { maaCoreService } returns maaCore
                }
            }
            var service = newService()

            // 解绑置 Disconnected，取到服务即视为 Connected
            val serviceState = MutableStateFlow<RemoteServiceManager.ServiceState>(
                RemoteServiceManager.ServiceState.Disconnected
            )
            val rebindFails = AtomicBoolean(false)

            mockkObject(RemoteServiceManager)
            every { RemoteServiceManager.state } returns serviceState
            every { RemoteServiceManager.unbind() } answers {
                serviceState.value = RemoteServiceManager.ServiceState.Disconnected
            }
            every { RemoteServiceManager.getInstanceOrNull() } answers {
                (serviceState.value as? RemoteServiceManager.ServiceState.Connected)?.service
            }
            coEvery { RemoteServiceManager.useRemoteService<Result<Unit>>(any(), any()) } coAnswers {
                if (rebindFails.get()) error("elevated service gone")
                serviceState.value = RemoteServiceManager.ServiceState.Connected(service)
                secondArg<suspend (RemoteService) -> Result<Unit>>().invoke(service)
            }

            block(
                Env(
                    rootDir = rootDir,
                    tasksOverrides = tasksOverrides,
                    loader = MaaResourceLoader(
                        pathConfig = pathConfig,
                        appSettings = appSettings,
                        chainState = chainState,
                        activityManager = activityManager,
                        coreDataPusher = coreDataPusher,
                    ),
                    loadedDirs = loadedDirs,
                    activityManager = activityManager,
                    rebindFails = rebindFails,
                    coreDataPusher = coreDataPusher,
                    replaceService = {
                        service = newService()
                        serviceState.value = RemoteServiceManager.ServiceState.Connected(service)
                        service
                    },
                )
            )
        } finally {
            Packages.initialize(null)
            if (syncCachedResources) unmockkStatic(ParcelFileDescriptor::class)
            rootDir.deleteRecursively()
        }
    }
}
