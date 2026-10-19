package com.aliothmoon.maameow.data.resource

import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.models.GameMetadataKey
import com.aliothmoon.maameow.domain.service.ResourceInitService
import com.aliothmoon.maameow.domain.state.ResourceInitState
import com.aliothmoon.maameow.utils.i18n.uiTextDynamic
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class GameMetadataLoaderTest {

    private val initState = MutableStateFlow<ResourceInitState>(ResourceInitState.NotChecked)
    private val revision = MutableStateFlow(0)
    private val clientFlow = MutableStateFlow("Official")
    private val languageFlow = MutableStateFlow("zh-cn")

    private val resourceDataManager = mockk<ResourceDataManager> {
        coEvery { load(any(), any(), any()) } returns Unit
    }
    private val itemHelper = mockk<ItemHelper> { justRun { load() } }
    private val activityManager = mockk<ActivityManager> { justRun { load(any(), any()) } }
    private val hotUpdateRevision = MutableStateFlow(0)
    private val hotUpdateSync = mockk<HotUpdateSync> {
        every { revision } returns hotUpdateRevision
        every { stageActivity() } returns STAGE_JSON
    }
    private val resourceInitService = mockk<ResourceInitService> {
        every { state } returns initState
        every { this@mockk.revision } returns this@GameMetadataLoaderTest.revision
    }
    private val chainState = mockk<TaskChainState> {
        every { isLoaded } returns MutableStateFlow(true)
        every { clientTypeFlow } returns clientFlow
    }
    private val appSettings = mockk<AppSettingsManager> {
        every { displayLanguageFlow } returns languageFlow
    }

    private fun loader(scope: CoroutineScope) = GameMetadataLoader(
        resourceDataManager, itemHelper, activityManager, hotUpdateSync, resourceInitService,
        chainState, appSettings, scope,
    ).also { it.start() }

    private fun TestScope.startLoader() = loader(backgroundScope).also { runCurrent() }

    private fun readyOf(client: String, language: String = "zh-cn", revision: Int = 0) =
        GameMetadataLoader.State.Ready(GameMetadataKey(client, language, revision))

    @Test
    fun loadsOnlyAfterLocalResourcesAreReady() = runTest {
        val loader = startLoader()
        coVerify(exactly = 0) { resourceDataManager.load(any(), any(), any()) }
        assertEquals(GameMetadataLoader.State.Idle, loader.state.value)

        initState.value = ResourceInitState.Ready
        runCurrent()

        coVerify(exactly = 1) { resourceDataManager.load("Official", "zh-cn", 0) }
        verify(exactly = 1) { itemHelper.load() }
        assertEquals(readyOf("Official"), loader.state.value)
    }

    @Test
    fun baseOnlyClientsShareOneProfile() = runTest {
        initState.value = ResourceInitState.Ready
        startLoader()

        clientFlow.value = "Bilibili"
        runCurrent()

        coVerify(exactly = 1) { resourceDataManager.load(any(), any(), any()) }
        verify(exactly = 1) { activityManager.load("Official", STAGE_JSON) }

        clientFlow.value = "YoStarEN"
        runCurrent()

        coVerify(exactly = 1) { resourceDataManager.load("YoStarEN", "zh-cn", 0) }
        verify(exactly = 1) { activityManager.load("YoStarEN", STAGE_JSON) }
    }

    @Test
    fun hotUpdateReloadsActivityWithoutRereadingItems() = runTest {
        initState.value = ResourceInitState.Ready
        val loader = startLoader()

        hotUpdateRevision.value = 1
        runCurrent()

        verify(exactly = 2) { activityManager.load("Official", STAGE_JSON) }
        verify(exactly = 1) { itemHelper.load() }
        assertEquals(
            GameMetadataLoader.State.Ready(GameMetadataKey("Official", "zh-cn", 0, hotUpdateRevision = 1)),
            loader.state.value,
        )
    }

    @Test
    fun languageChangeSkipsItemTable_revisionReloadsBoth() = runTest {
        initState.value = ResourceInitState.Ready
        startLoader()

        languageFlow.value = "en-us"
        runCurrent()

        coVerify(exactly = 1) { resourceDataManager.load("Official", "en-us", 0) }
        verify(exactly = 1) { itemHelper.load() }

        revision.value = 1
        runCurrent()

        coVerify(exactly = 1) { resourceDataManager.load("Official", "en-us", 1) }
        verify(exactly = 2) { itemHelper.load() }
    }

    @Test
    fun reinitializeReloadsAfterExtraction() = runTest {
        initState.value = ResourceInitState.Ready
        val loader = startLoader()

        initState.value = ResourceInitState.Extracting(0, 0)
        runCurrent()
        assertEquals(GameMetadataLoader.State.Idle, loader.state.value)
        revision.value = 1
        initState.value = ResourceInitState.Ready
        runCurrent()

        coVerify(exactly = 1) { resourceDataManager.load("Official", "zh-cn", 1) }
        assertEquals(readyOf("Official", revision = 1), loader.state.value)
    }

    @Test
    fun awaitCurrentTimesOutWhileResourcesAreMissing() = runTest {
        val loader = startLoader()

        assertFalse(loader.awaitCurrent(1.seconds))
    }

    @Test
    fun awaitCurrentGivesUpAtOnceWhenInitFailed() = runTest {
        initState.value = ResourceInitState.Failed(uiTextDynamic("x"))
        val loader = startLoader()

        val ready = async { loader.awaitCurrent(30.seconds) }
        runCurrent()

        assertTrue(ready.isCompleted)
        assertFalse(ready.await())
    }

    @Test
    fun awaitCurrentWaitsForTheLatestProfile() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { resourceDataManager.load("Official", any(), any()) } coAnswers { gate.await() }
        initState.value = ResourceInitState.Ready
        val loader = startLoader()

        clientFlow.value = "YoStarJP"
        val ready = async { loader.awaitCurrent(5.seconds) }
        runCurrent()
        assertFalse(ready.isCompleted)

        // 旧档跑完不算数，要等新档
        gate.complete(Unit)
        runCurrent()

        assertTrue(ready.await())
        assertEquals(readyOf("YoStarJP"), loader.state.value)
    }

    @Test
    fun awaitCurrentSeesSwitchBeforeLoaderDoes() = runTest {
        // 加载器单独一个调度器，不推进就一直落后
        val loaderScheduler = TestCoroutineScheduler()
        val loaderScope = CoroutineScope(StandardTestDispatcher(loaderScheduler) + Job())
        initState.value = ResourceInitState.Ready
        val loader = loader(loaderScope)
        loaderScheduler.runCurrent()
        assertEquals(readyOf("Official"), loader.state.value)

        // 切换刚发生，加载器还没看到
        clientFlow.value = "YoStarJP"
        val ready = async { loader.awaitCurrent(5.seconds) }
        runCurrent()
        assertFalse(ready.isCompleted)

        loaderScheduler.runCurrent()
        runCurrent()

        assertTrue(ready.await())
        assertEquals(readyOf("YoStarJP"), loader.state.value)
        loaderScope.cancel()
    }

    @Test
    fun failedProfileFailsFastInsteadOfTimingOut() = runTest {
        coEvery { resourceDataManager.load("Official", any(), any()) } throws IllegalStateException("boom")
        initState.value = ResourceInitState.Ready
        val loader = startLoader()
        assertTrue(loader.state.value is GameMetadataLoader.State.Failed)

        assertFalse(loader.awaitCurrent(30.seconds))

        assertEquals(0L, currentTime)
        // 初次加一次等待触发的重试
        coVerify(exactly = 2) { resourceDataManager.load("Official", any(), any()) }
    }

    @Test
    fun awaitCurrentRetriesFailedProfile() = runTest {
        var calls = 0
        coEvery { resourceDataManager.load("Official", any(), any()) } coAnswers {
            if (++calls == 1) throw IllegalStateException("boom")
        }
        initState.value = ResourceInitState.Ready
        val loader = startLoader()
        assertTrue(loader.state.value is GameMetadataLoader.State.Failed)

        assertTrue(loader.awaitCurrent(5.seconds))
        assertEquals(readyOf("Official"), loader.state.value)
    }

    @Test
    fun failedRoundDoesNotStopLaterProfiles() = runTest {
        coEvery { resourceDataManager.load("Official", any(), any()) } throws IllegalStateException("boom")
        initState.value = ResourceInitState.Ready
        val loader = startLoader()

        clientFlow.value = "YoStarKR"
        runCurrent()

        assertEquals(readyOf("YoStarKR"), loader.state.value)
    }

    @Test
    fun activityStillSwitchesWhenTablesFail() = runTest {
        coEvery { resourceDataManager.load("YoStarJP", any(), any()) } throws IllegalStateException("boom")
        initState.value = ResourceInitState.Ready
        val loader = startLoader()

        clientFlow.value = "YoStarJP"
        runCurrent()

        verify { activityManager.load("YoStarJP", STAGE_JSON) }
        assertTrue(loader.state.value is GameMetadataLoader.State.Failed)
    }

    @Test
    fun activityFailureDoesNotFailMetadata() = runTest {
        every { activityManager.load(any(), any()) } throws IllegalStateException("bad json")
        initState.value = ResourceInitState.Ready
        val loader = startLoader()

        assertEquals(readyOf("Official"), loader.state.value)
    }

    private companion object {
        const val STAGE_JSON = "{}"
    }
}
