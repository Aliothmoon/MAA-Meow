package com.aliothmoon.maameow

import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.MaaResourceLoader
import com.aliothmoon.maameow.manager.RemoteServiceManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MaaResourceLoaderTest {

    @After
    fun tearDown() {
        unmockkObject(RemoteServiceManager)
    }

    @Test
    fun load_usesSelectedDisplayLanguage_forClientResources() = runBlocking {
        val rootDir = Files.createTempDirectory("maa-resource-loader-test").toFile()
        try {
            File(rootDir, "resource").mkdirs()
            File(rootDir, "cache/resource").mkdirs()
            File(rootDir, "resource/global/YoStarEN/resource").mkdirs()
            File(rootDir, "cache/resource/global/YoStarEN/resource").mkdirs()

            val pathConfig = mockk<MaaPathConfig> {
                every { this@mockk.rootDir } returns rootDir.absolutePath
                every { cacheDir } returns File(rootDir, "cache").absolutePath
                every { cacheResourceDir } returns File(rootDir, "cache/resource").absolutePath
                every { globalResourceDir("YoStarEN") } returns File(rootDir, "resource/global/YoStarEN/resource")
                every { globalCacheResourceDir("YoStarEN") } returns File(rootDir, "cache/resource/global/YoStarEN/resource")
            }
            val appSettings = mockk<AppSettingsManager> {
                every { debugMode } returns MutableStateFlow(false)
                every { language } returns MutableStateFlow(AppSettingsManager.AppLanguage.EN)
            }
            val chainState = mockk<TaskChainState> {
                every { getClientType() } returns "Official"
            }
            val itemHelper = mockk<ItemHelper>()
            val resourceDataManager = mockk<ResourceDataManager>()
            val activityManager = mockk<ActivityManager>()
            val service = mockk<RemoteService>()
            val maaCore = mockk<MaaCoreService>()

            coEvery { resourceDataManager.load(any(), any()) } returns Unit
            coEvery { itemHelper.load() } returns Unit
            coEvery { activityManager.load(any()) } returns Unit
            every { service.setup(any(), any()) } returns true
            every { service.maaCoreService } returns maaCore
            every { maaCore.LoadResource(any()) } returns true

            mockkObject(RemoteServiceManager)
            coEvery { RemoteServiceManager.useRemoteService<Result<Unit>>(any(), any()) } coAnswers {
                secondArg<suspend (RemoteService) -> Result<Unit>>().invoke(service)
            }

            val loader = MaaResourceLoader(
                pathConfig = pathConfig,
                appSettings = appSettings,
                chainState = chainState,
                itemHelper = itemHelper,
                resourceDataManager = resourceDataManager,
                activityManager = activityManager,
            )

            val result = loader.load("YoStarEN")

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { resourceDataManager.load("YoStarEN", "en-us") }
            coVerify(exactly = 1) { activityManager.load("YoStarEN") }
            coVerify(exactly = 1) { itemHelper.load() }
        } finally {
            rootDir.deleteRecursively()
        }
    }
}
