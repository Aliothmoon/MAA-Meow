package com.aliothmoon.maameow.presentation.viewmodel

import com.aliothmoon.maameow.data.api.CopilotApiService
import com.aliothmoon.maameow.data.model.CopilotConfig
import com.aliothmoon.maameow.data.model.copilot.CopilotListItem
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotData
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotResponse
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotSetData
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotSetResponse
import com.aliothmoon.maameow.data.repository.CopilotProfileData
import com.aliothmoon.maameow.data.repository.CopilotProfileStore
import com.aliothmoon.maameow.data.repository.CopilotRepository
import com.aliothmoon.maameow.data.resource.MapInfo
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.CopilotManager
import com.aliothmoon.maameow.utils.i18n.UiText
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.domain.usecase.CheckGameReadinessUseCase
import com.aliothmoon.maameow.domain.usecase.GameReadiness
import com.aliothmoon.maameow.maa.callback.CopilotRuntimeStateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CopilotViewModelSearchAddTest {
    private companion object {
        const val MAIN_ID = 1
        const val MAIN_ID_2 = 3
        const val BOTH_ID = 4
        const val SSS_ID = 2
        const val MISSING_ID = 99
        const val SET_ID = 5
    }

    private val dispatcher = StandardTestDispatcher()
    private val api = mockk<CopilotApiService>()
    private val repository = mockk<CopilotRepository>(relaxed = true)
    private val resources = mockk<ResourceDataManager>(relaxed = true)
    private val profileId = MutableStateFlow("a")
    private val storedProfiles = mutableMapOf<String, CopilotProfileData>()
    private val profileStore = mockk<CopilotProfileStore>(relaxed = true)
    private val runtime = CopilotRuntimeStateStore()
    private val execution = MutableStateFlow(MaaExecutionState.IDLE)
    private val composition = mockk<MaaCompositionService>(relaxed = true) {
        every { state } returns execution
    }
    private val readiness = mockk<CheckGameReadinessUseCase>() {
        coEvery { this@mockk(any(), any(), any()) } returns GameReadiness.Ready(true)
    }
    private val chain = mockk<TaskChainState>(relaxed = true) {
        every { this@mockk.profileId } returns this@CopilotViewModelSearchAddTest.profileId
        every { clientType } returns "Official"
    }
    private var saveCount = 0
    private var failure: UiText? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { resources.findMap(any()) } answers {
            val stage = firstArg<String>()
            MapInfo(code = stage, stageId = stage)
        }
        coEvery { repository.saveCopilotJson(any(), any()) } answers { "/c/${firstArg<Int>()}.json" }
        coEvery { api.getCopilot(any()) } answers {
            val id = firstArg<Int>()
            val content = when (id) {
                MAIN_ID, MAIN_ID_2 -> """{"stage_name":"main_01-0$id","doc":{"title":"t$id"}}"""
                BOTH_ID -> """{"stage_name":"main_01-04","difficulty":3,"doc":{"title":"both"}}"""
                SSS_ID -> """{"type":"SSS","stage_name":"lt_x","doc":{"title":"sss"}}"""
                else -> null
            }
            Result.success(
                if (content == null) {
                    PrtsCopilotResponse(statusCode = 404)
                } else {
                    PrtsCopilotResponse(statusCode = 200, data = PrtsCopilotData(id = id, content = content))
                }
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        listMode: Boolean = true,
        taskList: List<CopilotListItem> = emptyList(),
    ): CopilotViewModel {
        storedProfiles["a"] = CopilotProfileData(CopilotConfig(useCopilotList = listMode), taskList)
        storedProfiles.putIfAbsent("b", CopilotProfileData())
        every { repository.profiles } returns profileStore
        every { profileStore.isLoaded } returns MutableStateFlow(true)
        coEvery { profileStore.awaitLoaded() } returns Unit
        every { profileStore.forProfile(any()) } answers { storedProfiles[firstArg()] ?: CopilotProfileData() }
        every { profileStore.update(any(), any()) } answers {
            val id = firstArg<String>()
            val transform = secondArg<(CopilotProfileData) -> CopilotProfileData>()
            storedProfiles[id] = transform(storedProfiles[id] ?: CopilotProfileData())
            saveCount++
        }
        coEvery { composition.startCopilot(any(), any()) } returns MaaCompositionService.StartResult.Success("test")
        val vm = CopilotViewModel(
            appContext = mockk(relaxed = true),
            copilotManager = CopilotManager(api, repository),
            compositionService = composition,
            repository = repository,
            resourceDataManager = resources,
            copilotResourceProvider = mockk(relaxed = true),
            runtimeStateStore = runtime,
            checkGameReadiness = readiness,
            chainState = chain,
            achievementRepository = mockk(relaxed = true),
            operBoxAssist = mockk(relaxed = true) { every { state } returns flowOf() },
            operBoxYituliuSync = mockk(relaxed = true),
            metadataGate = mockk(relaxed = true),
        )
        dispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    private fun CopilotViewModel.pick(id: Int) {
        onPickFromSearch(id) { failure = it }
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun CopilotViewModel.addSet(vararg ids: Int) {
        coEvery { api.getCopilotSet(SET_ID) } returns Result.success(
            PrtsCopilotSetResponse(
                statusCode = 200,
                data = PrtsCopilotSetData(id = SET_ID, name = "n", copilotIds = ids.toList()),
            )
        )
        onAddSetFromSearch(SET_ID) { failure = it }
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun CopilotViewModel.listedIds() = state.value.taskList.map { it.prtsId }

    @Test
    fun pickInListModeAddsWithoutFailure() {
        val vm = viewModel()

        vm.pick(MAIN_ID)

        assertEquals(listOf(MAIN_ID), vm.listedIds())
        assertNull(failure)
        assertTrue(!vm.state.value.isLoading)
    }

    @Test
    fun pickMissingCopilotReportsFailure() {
        val vm = viewModel()

        vm.pick(MISSING_ID)

        assertNotNull(failure)
        assertTrue(vm.listedIds().isEmpty())
        assertTrue(!vm.state.value.isLoading)
    }

    @Test
    fun pickSssInListModeIsRejectedAndKeepsTab() {
        val vm = viewModel()

        vm.pick(SSS_ID)

        assertNotNull(failure)
        assertTrue(vm.listedIds().isEmpty())
        assertEquals(CopilotTabs.MAIN, vm.state.value.tabIndex)
        assertTrue(vm.state.value.listModeActive)
        assertTrue(!vm.state.value.isLoading)
    }

    @Test
    fun pickSssInSingleModeStillLoads() {
        val vm = viewModel(listMode = false)

        vm.pick(SSS_ID)

        assertNull(failure)
        assertNotNull(vm.state.value.currentCopilot)
    }

    @Test
    fun setAddSkipsCopilotsAlreadyListed() {
        val vm = viewModel(taskList = listOf(CopilotListItem(name = "1-1", filePath = "/c/$MAIN_ID.json")))

        vm.addSet(MAIN_ID, MAIN_ID_2)

        assertEquals(listOf(MAIN_ID, MAIN_ID_2), vm.listedIds())
        assertNull(failure)
    }

    @Test
    fun setAddTwiceDoesNotDuplicate() {
        val vm = viewModel()

        vm.addSet(MAIN_ID, MAIN_ID_2)
        vm.addSet(MAIN_ID, MAIN_ID_2)

        assertEquals(listOf(MAIN_ID, MAIN_ID_2), vm.listedIds())
        assertNull(failure)
    }

    @Test
    fun setAddSkipsSssAndReportsIt() {
        val vm = viewModel()

        vm.addSet(MAIN_ID, SSS_ID)

        assertEquals(listOf(MAIN_ID), vm.listedIds())
        assertNotNull(failure)
        assertEquals(CopilotTabs.MAIN, vm.state.value.tabIndex)
    }

    @Test
    fun setAddReportsPartialFailure() {
        val vm = viewModel()

        vm.addSet(MAIN_ID, MISSING_ID)

        assertEquals(listOf(MAIN_ID), vm.listedIds())
        assertNotNull(failure)
    }

    @Test
    fun setAddSplitsNormalAndRaidLikeWpf() {
        val vm = viewModel()

        vm.addSet(MAIN_ID, BOTH_ID)

        assertEquals(listOf(MAIN_ID, BOTH_ID, BOTH_ID), vm.listedIds())
        assertEquals(listOf(false, false, true), vm.state.value.taskList.map { it.isRaid })
    }

    @Test
    fun pickNormalAndRaidCopilotAddsSingleRow() {
        val vm = viewModel()

        vm.pick(BOTH_ID)

        assertEquals(listOf(BOTH_ID), vm.listedIds())
        assertEquals(listOf(false), vm.state.value.taskList.map { it.isRaid })
    }

    @Test
    fun setAddSwitchesToListModeFromSingleMode() {
        val vm = viewModel(listMode = false)

        vm.addSet(MAIN_ID)

        assertTrue(vm.state.value.listModeActive)
        assertEquals(listOf(MAIN_ID), vm.listedIds())
    }

    @Test
    fun profileSwitchLoadsWithoutSavingAndAbaKeepsConfigAndList() {
        val vm = viewModel()
        vm.pick(MAIN_ID)
        val configA = vm.state.value.config.copy(loop = true, loopTimes = 4, useSupportUnit = true)
        vm.onConfigChanged(configA)
        val saved = saveCount
        profileId.value = "b"
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.taskList.isEmpty())
        assertEquals(CopilotConfig(), vm.state.value.config)
        assertEquals(saved, saveCount)
        vm.onConfigChanged(CopilotConfig(formationIndex = 3))
        profileId.value = "a"
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(configA, vm.state.value.config)
        assertEquals(listOf(MAIN_ID), vm.listedIds())
    }

    @Test
    fun queuedConfigSaveKeepsOriginalProfileBeforeCollectorRuns() {
        val vm = viewModel()
        val changed = vm.state.value.config.copy(loopTimes = 8)
        vm.onConfigChanged(changed)
        profileId.value = "b"
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(changed, storedProfiles.getValue("a").config)
        assertEquals(CopilotConfig(), storedProfiles.getValue("b").config)
    }

    @Test
    fun downloadFinishingAfterSwitchCannotAddToNewProfile() {
        val vm = viewModel()
        val gate = CompletableDeferred<Unit>()
        coEvery { api.getCopilot(MAIN_ID) } coAnswers {
            gate.await()
            Result.success(PrtsCopilotResponse(200, data = PrtsCopilotData(
                id = MAIN_ID, content = """{"stage_name":"main_01-01"}""",
            )))
        }
        vm.onPickFromSearch(MAIN_ID) { failure = it }
        dispatcher.scheduler.advanceUntilIdle()
        profileId.value = "b"
        dispatcher.scheduler.advanceUntilIdle()
        gate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.taskList.isEmpty())
        assertTrue(storedProfiles.getValue("b").taskList.isEmpty())
        assertNull(vm.state.value.currentCopilot)
    }

    private fun runnableList(): List<CopilotListItem> {
        coEvery { repository.readCopilotJson(any()) } returns """{"stage_name":"main_01-01"}"""
        every { repository.toCorePath(any()) } answers { firstArg() }
        return listOf(
            CopilotListItem("1-1", "/c/1.json", id = "a-first"),
            CopilotListItem("1-2", "/c/2.json", id = "a-second"),
        )
    }

    @Test
    fun runningCallbackUpdatesOriginalProfileByStableRowIdAfterSwitch() {
        val vm = viewModel(taskList = runnableList())
        vm.onStart()
        dispatcher.scheduler.advanceUntilIdle()
        profileId.value = "b"
        dispatcher.scheduler.advanceUntilIdle()
        val stateB = vm.state.value
        runtime.setCurrentCopilotIndex(1)
        runtime.markTaskSuccess()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(stateB, vm.state.value)
        assertEquals(listOf(true, false), storedProfiles.getValue("a").taskList.map { it.isChecked })
        profileId.value = "a"
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(true, false), vm.state.value.taskList.map { it.isChecked })
    }

    @Test
    fun runningCallbackKeepsLaunchCoordinatesAfterUserReordersList() {
        val vm = viewModel(taskList = runnableList())
        vm.onStart()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onReorderList("a-first", "a-second")
        vm.onReorderSettled()
        runtime.setCurrentCopilotIndex(0)
        runtime.markTaskSuccess()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("a-second", "a-first"), vm.state.value.taskList.map { it.id })
        assertEquals(listOf(true, false), vm.state.value.taskList.map { it.isChecked })
    }

    @Test
    fun secondStartDuringRunningDoesNotReplaceCallbackProfile() {
        val vm = viewModel(taskList = runnableList())
        vm.onStart()
        dispatcher.scheduler.advanceUntilIdle()
        execution.value = MaaExecutionState.RUNNING
        profileId.value = "b"
        dispatcher.scheduler.advanceUntilIdle()
        vm.onStart()
        runtime.setCurrentCopilotIndex(0)
        runtime.markTaskSuccess()
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { composition.startCopilot(any(), any()) }
        assertEquals(listOf(false, true), storedProfiles.getValue("a").taskList.map { it.isChecked })
        assertTrue(storedProfiles.getValue("b").taskList.isEmpty())
    }

    @Test
    fun stopFinishingAfterSwitchDoesNotChangeNewProfileStatus() {
        val vm = viewModel()
        val gate = CompletableDeferred<Unit>()
        coEvery { composition.stop() } coAnswers {
            gate.await()
            MaaCompositionService.StopResult.Success
        }
        vm.onStop()
        dispatcher.scheduler.advanceUntilIdle()
        profileId.value = "b"
        dispatcher.scheduler.advanceUntilIdle()
        val stateB = vm.state.value
        gate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(stateB, vm.state.value)
    }

    @Test
    fun launchUsesCapturedConfigAndListWhenUserEditsBeforeCoroutineRuns() {
        val original = runnableList()
        val vm = viewModel(taskList = original)
        vm.onStart()
        vm.onConfigChanged(CopilotConfig(useSupportUnit = true, supportUnitUsage = 3))
        vm.onClearList()
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) {
            composition.startCopilot(match { tasks ->
                val params = tasks.single().params
                params.contains("/c/1.json") && params.contains("/c/2.json") &&
                    params.contains("\"support_unit_usage\":0")
            }, any())
        }
        assertTrue(vm.state.value.taskList.isEmpty())
    }
}
