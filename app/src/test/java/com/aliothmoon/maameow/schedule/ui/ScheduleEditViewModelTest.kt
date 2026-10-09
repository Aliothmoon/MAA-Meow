package com.aliothmoon.maameow.schedule.ui

import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.permission.PermissionState
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import com.aliothmoon.maameow.schedule.model.ScheduleType
import com.aliothmoon.maameow.schedule.service.ScheduleAlarmManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleEditViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModelStore = ViewModelStore()
    private val context = mockk<Context>()
    private val profiles = listOf(
        TaskProfile(id = "profile-1", name = "First", chain = emptyList()),
        TaskProfile(id = "profile-2", name = "Active", chain = emptyList()),
    )
    private val strategy = ScheduleStrategy(
        id = "schedule-1",
        name = "Daily",
        enabled = false,
        daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
        executionTimes = listOf(LocalTime.of(8, 30), LocalTime.of(20, 0)),
        profileId = profiles.last().id,
        forceStart = true,
        autoScreenSaver = true,
        autoSleepAfterTask = true,
        skipAutoSleepIfAwake = true,
        closeGameAfterTask = true,
        createdAt = 100L,
        lastExecutedAt = 200L,
        lastResult = ExecutionResult.STARTED,
        lastResultMessage = "Started",
    )
    private val profilesLoaded = MutableStateFlow(false)
    private val strategiesLoaded = MutableStateFlow(false)
    private val storedStrategies = MutableStateFlow(listOf(strategy))
    private val activeProfileId = MutableStateFlow(profiles.last().id)
    private val repository = mockk<ScheduleStrategyRepository> {
        every { isLoaded } returns strategiesLoaded
        every { strategies } returns storedStrategies
    }
    private val taskChainState = mockk<TaskChainState> {
        every { isLoaded } returns profilesLoaded
        every { profiles } returns MutableStateFlow(this@ScheduleEditViewModelTest.profiles)
        every { profileId } returns activeProfileId
    }
    private val alarms = mockk<ScheduleAlarmManager>(relaxed = true)
    private val permissions = mockk<PermissionManager>(relaxed = true) {
        every { permissions } returns PermissionState()
    }
    private val runMode = MutableStateFlow(RunMode.BACKGROUND)
    private val useHardwareScreenOff = MutableStateFlow(false)
    private val settings = mockk<AppSettingsManager> {
        every { runMode } returns this@ScheduleEditViewModelTest.runMode
        every { closeAppOnTaskEnd } returns MutableStateFlow(false)
        every { useHardwareScreenOff } returns this@ScheduleEditViewModelTest.useHardwareScreenOff
    }
    private lateinit var viewModel: ScheduleEditViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = ScheduleEditViewModel(repository, taskChainState, alarms, permissions, settings)
        viewModelStore.put("editor", viewModel)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun screenSaverEffect_tracksStrategyGlobalSettingAndRunMode() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.screenSaverEffect.collect { }
        }
        useHardwareScreenOff.value = true
        runCurrent()
        assertEquals(ScreenSaverEffect.Inactive, viewModel.screenSaverEffect.value)
        viewModel.onAutoScreenSaverChanged(true)
        runCurrent()
        assertEquals(ScreenSaverEffect.HardwareScreenOff, viewModel.screenSaverEffect.value)
        useHardwareScreenOff.value = false
        runCurrent()
        assertEquals(ScreenSaverEffect.ScreenSaver, viewModel.screenSaverEffect.value)
        runMode.value = RunMode.FOREGROUND
        runCurrent()
        assertEquals(ScreenSaverEffect.ForegroundInactive, viewModel.screenSaverEffect.value)
    }

    @Test
    fun existingStrategyPublishesCompleteFormOnlyAfterBothSourcesLoad() = runTest(dispatcher) {
        val observed = mutableListOf<ScheduleEditUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.toList(observed)
        }
        viewModel.loadStrategy(context, strategy.id)
        runCurrent()
        assertTrue(viewModel.state.value.isLoading)

        profilesLoaded.value = true
        runCurrent()
        assertTrue(viewModel.state.value.isLoading)
        strategiesLoaded.value = true
        runCurrent()

        assertEquals(listOf(true, false), observed.map { it.isLoading })
        assertEquals(
            ScheduleEditUiState(
                isLoading = false,
                isNew = false,
                strategyId = strategy.id,
                name = strategy.name,
                daysOfWeek = strategy.daysOfWeek,
                executionTimes = strategy.executionTimes,
                profiles = profiles,
                selectedProfileId = strategy.profileId,
                forceStart = true,
                autoScreenSaver = true,
                autoSleepAfterTask = true,
                skipAutoSleepIfAwake = true,
                closeGameAfterTask = true,
            ),
            observed.last(),
        )
        coVerify(exactly = 0) { repository.getById(any()) }
    }

    @Test
    fun intervalStrategyNeverPublishesReadyFixedTimeForm() = runTest(dispatcher) {
        val interval = strategy.copy(
            scheduleType = ScheduleType.INTERVAL,
            startTimeMs = 1_800_000_000_000L,
            intervalMinutes = (2 * 24 + 3) * 60,
        )
        storedStrategies.value = listOf(interval)
        strategiesLoaded.value = true
        val observed = mutableListOf<ScheduleEditUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.toList(observed)
        }
        viewModel.loadStrategy(context, interval.id)
        runCurrent()
        assertTrue(viewModel.state.value.isLoading)

        profilesLoaded.value = true
        runCurrent()

        val ready = observed.filterNot { it.isLoading }.single()
        assertFalse(ready.isNew)
        assertEquals(interval.id, ready.strategyId)
        assertEquals(ScheduleType.INTERVAL, ready.scheduleType)
        assertEquals(interval.startTimeMs, ready.startTimeMs)
        assertEquals(2, ready.intervalDays)
        assertEquals(3, ready.intervalHours)
    }

    @Test
    fun newStrategyWaitsForStoredCountAndSelectsActiveProfile() = runTest(dispatcher) {
        every { context.getString(R.string.schedule_default_name, 2) } returns "策略2"
        profilesLoaded.value = true
        storedStrategies.value = emptyList()
        viewModel.loadStrategy(context, null)
        runCurrent()
        assertTrue(viewModel.state.value.isLoading)

        storedStrategies.value = listOf(strategy)
        strategiesLoaded.value = true
        runCurrent()

        val ready = viewModel.state.value
        assertFalse(ready.isLoading)
        assertTrue(ready.isNew)
        assertNull(ready.strategyId)
        assertEquals("策略2", ready.name)
        assertEquals(profiles, ready.profiles)
        assertEquals(profiles.last().id, ready.selectedProfileId)
    }

    @Test
    fun newStrategySelectsFirstProfileWhenActiveProfileIsEmpty() = runTest(dispatcher) {
        every { context.getString(R.string.schedule_default_name, 2) } returns "策略2"
        activeProfileId.value = ""
        profilesLoaded.value = true
        strategiesLoaded.value = true
        viewModel.loadStrategy(context, null)
        runCurrent()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(profiles.first().id, viewModel.state.value.selectedProfileId)
    }

    @Test
    fun repeatedLoadPreservesUnsavedEdits() = runTest(dispatcher) {
        viewModel.loadStrategy(context, strategy.id)
        viewModel.loadStrategy(context, strategy.id)
        profilesLoaded.value = true
        strategiesLoaded.value = true
        runCurrent()
        viewModel.onNameChanged("Unsaved name")
        viewModel.onForceStartChanged(false)
        viewModel.onToggleDay(DayOfWeek.TUESDAY)
        val edited = viewModel.state.value

        viewModel.loadStrategy(context, strategy.id)
        runCurrent()

        assertEquals(edited, viewModel.state.value)
    }

    @Test
    fun saveDuringLoadingDoesNotValidateOrPersistDefaultForm() = runTest(dispatcher) {
        viewModel.loadStrategy(context, strategy.id)
        runCurrent()
        val loading = viewModel.state.value

        viewModel.onSave(context)
        runCurrent()

        assertEquals(loading, viewModel.state.value)
        coVerify(exactly = 0) { repository.add(any()) }
        coVerify(exactly = 0) { repository.update(any()) }
    }

    @Test
    fun savePreservesStrategyMetadataAndIgnoresRepeatedClicks() = runTest(dispatcher) {
        val finishSave = CompletableDeferred<Unit>()
        coEvery { repository.update(any()) } coAnswers { finishSave.await() }
        profilesLoaded.value = true
        strategiesLoaded.value = true
        viewModel.loadStrategy(context, strategy.id)
        runCurrent()
        viewModel.onNameChanged("Renamed")

        viewModel.onSave(context)
        runCurrent()
        assertTrue(viewModel.state.value.isSaving)
        viewModel.onSave(context)
        runCurrent()
        coVerify(exactly = 1) { repository.update(strategy.copy(name = "Renamed")) }
        coVerify(exactly = 0) { repository.add(any()) }

        finishSave.complete(Unit)
        runCurrent()
        assertTrue(viewModel.state.value.saveSuccess)
        assertFalse(viewModel.state.value.isSaving)
    }
}
