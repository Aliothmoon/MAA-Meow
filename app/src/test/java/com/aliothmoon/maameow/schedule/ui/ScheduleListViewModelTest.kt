package com.aliothmoon.maameow.schedule.ui

import android.app.Application
import android.app.KeyguardManager
import androidx.lifecycle.ViewModelStore
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.permission.PermissionState
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.preferences.UnlockGestureStore
import com.aliothmoon.maameow.domain.models.UnlockGesture
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.ScheduleHealthIssue
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import com.aliothmoon.maameow.schedule.service.ScheduleAlarmManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val strategy = ScheduleStrategy(name = "Daily", profileId = "p", autoScreenSaver = true)
    private val strategies = MutableStateFlow(listOf(strategy))
    private val hardwareScreenOff = MutableStateFlow(false)
    private lateinit var viewModel: ScheduleListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val keyguard = mockk<KeyguardManager> { every { isDeviceSecure } returns false }
        val app = mockk<Application> { every { getSystemService(KeyguardManager::class.java) } returns keyguard }
        val repository = mockk<ScheduleStrategyRepository> { every { strategies } returns this@ScheduleListViewModelTest.strategies }
        val chain = mockk<TaskChainState> { every { profiles } returns MutableStateFlow(emptyList<TaskProfile>()) }
        val alarms = mockk<ScheduleAlarmManager>(relaxed = true) { every { canScheduleExact() } returns true }
        val permissions = mockk<PermissionManager> {
            every { state } returns MutableStateFlow(
                PermissionState(shizukuAvailable = true, shizuku = true, batteryWhitelist = true, notification = true),
            )
        }
        val settings = mockk<AppSettingsManager> {
            every { useHardwareScreenOff } returns hardwareScreenOff
            every { wakeUnlockType } returns MutableStateFlow("swipe")
            every { wakeCredential } returns MutableStateFlow("")
        }
        val gestures = mockk<UnlockGestureStore> { every { gesture } returns MutableStateFlow<UnlockGesture?>(null) }
        viewModel = ScheduleListViewModel(app, repository, chain, alarms, permissions, settings, gestures)
        store.put("list", viewModel)
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun healthCard_tracksHardwareSettingWithoutStrategyChanges() = runTest(dispatcher) {
        runCurrent()
        assertEquals(listOf(ScheduleHealthIssue.OVERLAY), viewModel.state.value.healthIssues)
        hardwareScreenOff.value = true
        runCurrent()
        assertTrue(viewModel.state.value.healthIssues.isEmpty())
        hardwareScreenOff.value = false
        runCurrent()
        assertEquals(listOf(ScheduleHealthIssue.OVERLAY), viewModel.state.value.healthIssues)
        strategies.value = listOf(strategy.copy(enabled = false))
        runCurrent()
        assertTrue(viewModel.state.value.healthIssues.isEmpty())
    }
}
