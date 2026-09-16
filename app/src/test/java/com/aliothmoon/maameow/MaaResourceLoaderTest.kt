package com.aliothmoon.maameow

import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.CoreDataPusher
import com.aliothmoon.maameow.domain.service.MaaResourceLoader
import com.aliothmoon.maameow.manager.RemoteServiceManager
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
    }

    @Test
    fun load_usesSelectedDisplayLanguage_forClientResources() = runBlocking {
        withEnv(AppSettingsManager.AppLanguage.EN) { env ->
            val result = env.loader.load("YoStarEN")

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { env.resourceDataManager.load("YoStarEN", "en-us") }
            coVerify(exactly = 1) { env.activityManager.load("YoStarEN") }
            coVerify(exactly = 1) { env.itemHelper.load() }
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
        assertEquals("", MaaResourceLoader.resourceProfileOf(""))
        assertEquals("", MaaResourceLoader.resourceProfileOf("Official"))
        assertEquals("", MaaResourceLoader.resourceProfileOf("Bilibili"))
        assertEquals("YoStarJP", MaaResourceLoader.resourceProfileOf("YoStarJP"))
        assertEquals("YoStarEN", MaaResourceLoader.resourceProfileOf("YoStarEN"))
        assertEquals("txwy", MaaResourceLoader.resourceProfileOf("txwy"))
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

    private class Env(
        val loader: MaaResourceLoader,
        val loadedDirs: MutableList<String>,
        val resourceDataManager: ResourceDataManager,
        val itemHelper: ItemHelper,
        val activityManager: ActivityManager,
        /** 置 true 模拟换进程后服务没能回来 */
        val rebindFails: AtomicBoolean,
        val coreDataPusher: CoreDataPusher,
    )

    private suspend fun withEnv(
        appLanguage: AppSettingsManager.AppLanguage = AppSettingsManager.AppLanguage.ZH,
        setupCode: Int = SetupResult.OK,
        coreSeparated: Boolean = false,
        block: suspend (Env) -> Unit,
    ) {
        val rootDir = Files.createTempDirectory("maa-resource-loader-test").toFile()
        try {
            File(rootDir, "resource").mkdirs()
            File(rootDir, "cache/resource").mkdirs()
            listOf("YoStarJP", "YoStarEN").forEach {
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
            val appSettings = mockk<AppSettingsManager> {
                every { debugMode } returns MutableStateFlow(false)
                every { language } returns MutableStateFlow(appLanguage)
                every { displayLanguage } returns ResourceDataManager.displayLanguageCode(appLanguage)
                every { forceFullscreenOnVirtualDisplay } returns MutableStateFlow(false)
                every { tasksOverrideEnabled } returns MutableStateFlow(false)
            }
            val chainState = mockk<TaskChainState> {
                every { clientType } returns "Official"
            }
            val itemHelper = mockk<ItemHelper>()
            val resourceDataManager = mockk<ResourceDataManager>()
            val activityManager = mockk<ActivityManager>()
            val service = mockk<RemoteService>()
            every { pathConfig.isCoreSeparated } returns coreSeparated
            every { pathConfig.coreRootDir } answers {
                if (coreSeparated) "/data/local/tmp/maameow" else rootDir.absolutePath
            }
            every { pathConfig.toCorePath(any()) } answers {
                if (coreSeparated) {
                    MaaPathConfig.toCorePath(firstArg(), rootDir.absolutePath, "/data/local/tmp/maameow")
                } else firstArg()
            }
            val coreDataPusher = mockk<CoreDataPusher> {
                coEvery { prepare(any()) } returns true
            }
            val maaCore = mockk<MaaCoreService>()
            val loadedDirs = mutableListOf<String>()

            coEvery { resourceDataManager.load(any(), any()) } returns Unit
            coEvery { itemHelper.load() } returns Unit
            coEvery { activityManager.load(any()) } returns Unit
            every { service.setup(any(), any()) } returns setupCode
            justRun { service.setForceFullscreenOnVirtualDisplay(any()) }
            every { service.maaCoreService } returns maaCore
            every { maaCore.LoadResource(any()) } answers {
                loadedDirs += firstArg<String>()
                true
            }

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
            coEvery { RemoteServiceManager.useRemoteService<Result<Unit>>(any(), any()) } coAnswers {
                if (rebindFails.get()) error("elevated service gone")
                serviceState.value = RemoteServiceManager.ServiceState.Connected(service)
                secondArg<suspend (RemoteService) -> Result<Unit>>().invoke(service)
            }

            block(
                Env(
                    loader = MaaResourceLoader(
                        pathConfig = pathConfig,
                        appSettings = appSettings,
                        chainState = chainState,
                        itemHelper = itemHelper,
                        resourceDataManager = resourceDataManager,
                        activityManager = activityManager,
                        coreDataPusher = coreDataPusher,
                    ),
                    loadedDirs = loadedDirs,
                    resourceDataManager = resourceDataManager,
                    itemHelper = itemHelper,
                    activityManager = activityManager,
                    rebindFails = rebindFails,
                    coreDataPusher = coreDataPusher,
                )
            )
        } finally {
            rootDir.deleteRecursively()
        }
    }
}
