package com.aliothmoon.maameow.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.launch.LaunchPipeline
import com.aliothmoon.maameow.domain.launch.LaunchSession
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundTaskStopTest {
    private val state = MutableStateFlow(MaaExecutionState.RUNNING)
    private val service = mockk<MaaCompositionService>(relaxed = true) {
        every { state } returns this@BackgroundTaskStopTest.state
    }
    private val achievements = mockk<AchievementReporter>(relaxed = true)

    private fun viewModel() = BackgroundTaskViewModel(
        chainState = mockk<TaskChainState> {
            every { chain } returns MutableStateFlow(emptyList())
        },
        prepareTaskStart = mockk(),
        compositionService = service,
        sessionLogger = mockk { every { logs } returns MutableStateFlow(emptyList()) },
        appSettingsManager = mockk {
            every { showTouchPreview } returns MutableStateFlow(false)
            every { closeAppOnTaskEnd } returns MutableStateFlow(false)
        },
        pathConfig = mockk(),
        achievementReporter = achievements,
        gameMuteCoordinator = mockk { every { isMuted } returns MutableStateFlow(false) },
        gameFpsWatcher = mockk { every { fps } returns MutableStateFlow(null) },
        launchPipeline = mockk<LaunchPipeline> {
            every { session } returns MutableStateFlow<LaunchSession>(LaunchSession.Idle)
        },
        taskEndRegistry = mockk { every { taskEnded } returns MutableSharedFlow() },
        application = mockk(),
        sideTaskRunner = mockk(),
        screenPower = mockk(),
    )

    @Test
    fun nonRunningStatesNeverStopOrRecordAnAchievement() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        try {
            for (current in MaaExecutionState.entries.filter { it != MaaExecutionState.RUNNING }) {
                state.value = current
                vm.onStopTasks()
            }
            coVerify(exactly = 0) { service.stop(any()) }
            verify(exactly = 0) { achievements.reportTaskStopped() }
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun repeatedClicksAndRequestsInFlightStopOnlyOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val completion = CompletableDeferred<MaaCompositionService.StopResult>()
        coEvery { service.stop(any()) } coAnswers { completion.await() }
        val vm = viewModel()
        try {
            repeat(3) { vm.onStopTasks() }
            runCurrent()
            repeat(3) { vm.onStopTasks() }
            coVerify(exactly = 1) { service.stop(MaaCompositionService.StopOrigin.USER) }
            verify(exactly = 1) { achievements.reportTaskStopped() }
            state.value = MaaExecutionState.IDLE
            completion.complete(MaaCompositionService.StopResult.Success)
            runCurrent()
            vm.onStopTasks()
            coVerify(exactly = 1) { service.stop(any()) }
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
