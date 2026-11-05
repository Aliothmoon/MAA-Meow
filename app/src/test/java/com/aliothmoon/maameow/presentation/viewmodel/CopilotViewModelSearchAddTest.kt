package com.aliothmoon.maameow.presentation.viewmodel

import com.aliothmoon.maameow.data.api.CopilotApiService
import com.aliothmoon.maameow.data.model.CopilotConfig
import com.aliothmoon.maameow.data.model.copilot.CopilotListItem
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotData
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotResponse
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotSetData
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotSetResponse
import com.aliothmoon.maameow.data.repository.CopilotRepository
import com.aliothmoon.maameow.data.resource.MapInfo
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.CopilotManager
import com.aliothmoon.maameow.utils.i18n.UiText
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        coEvery { repository.loadConfig() } returns CopilotConfig(useCopilotList = listMode)
        coEvery { repository.loadTaskList() } returns taskList
        val vm = CopilotViewModel(
            appContext = mockk(relaxed = true),
            copilotManager = CopilotManager(api, repository),
            compositionService = mockk(relaxed = true) { every { state } returns MutableStateFlow(mockk()) },
            repository = repository,
            resourceDataManager = resources,
            copilotResourceProvider = mockk(relaxed = true),
            runtimeStateStore = mockk(relaxed = true) {
                every { hasRequirementIgnored } returns MutableStateFlow(false)
                every { currentCopilotIndex } returns MutableStateFlow(-1)
                every { taskSuccessToken } returns MutableStateFlow(0)
            },
            checkGameReadiness = mockk(relaxed = true),
            chainState = mockk(relaxed = true),
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
}
