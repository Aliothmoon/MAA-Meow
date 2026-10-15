package com.aliothmoon.maameow.presentation.viewmodel

import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskStopObserverTest {
    private val state = MutableStateFlow(MaaExecutionState.RUNNING)
    private var token = 1L
    private var origin = MaaCompositionService.StopOrigin.USER
    private var result: MaaCompositionService.StopResult? = null
    private val service = mockk<MaaCompositionService> {
        every { state } returns this@TaskStopObserverTest.state
        every { currentRunToken } answers { token }
        every { lastStopOrigin } answers { origin }
        every { lastStopResult } answers { result }
    }

    @Test
    fun stopFromAnotherPageUpdatesOwnerWithoutStoppingAgain() = runTest {
        val events = mutableListOf<String>()
        TaskStopObserver(service, backgroundScope).track(
            runToken = token,
            onStopping = { events.add("stopping") },
            onStopped = { assertSame(result, it); events.add("stopped") },
            onFinished = { events.add("finished") },
        )
        state.value = MaaExecutionState.STOPPING
        runCurrent()
        assertEquals(listOf("stopping"), events)

        result = MaaCompositionService.StopResult.Success
        state.value = MaaExecutionState.IDLE
        runCurrent()
        assertEquals(listOf("stopping", "finished", "stopped"), events)
        coVerify(exactly = 0) { service.stop(any()) }
    }

    @Test
    fun fastFailedStopStillReportsResultWhenStoppingIsConflated() = runTest {
        val results = mutableListOf<MaaCompositionService.StopResult>()
        TaskStopObserver(service, backgroundScope).track(token, {}, { results.add(it) })
        state.value = MaaExecutionState.STOPPING
        result = MaaCompositionService.StopResult.Failed
        state.value = MaaExecutionState.IDLE
        runCurrent()
        assertEquals(listOf(MaaCompositionService.StopResult.Failed), results)
    }

    @Test
    fun naturalCompletionCleansUpWithoutClaimingManualStop() = runTest {
        val events = mutableListOf<String>()
        TaskStopObserver(service, backgroundScope).track(
            token,
            { events.add("stopping") },
            { events.add("stopped") },
            { events.add("finished") },
        )
        state.value = MaaExecutionState.IDLE
        runCurrent()
        assertEquals(listOf("finished"), events)
    }

    @Test
    fun callbackStopDoesNotClaimUserStoppedTheTask() = runTest {
        val events = mutableListOf<String>()
        TaskStopObserver(service, backgroundScope).track(
            token,
            { events.add("stopping") },
            { events.add("stopped") },
            { events.add("finished") },
        )
        origin = MaaCompositionService.StopOrigin.CALLBACK
        state.value = MaaExecutionState.STOPPING
        runCurrent()
        result = MaaCompositionService.StopResult.Success
        state.value = MaaExecutionState.IDLE
        runCurrent()
        assertEquals(listOf("finished"), events)
    }

    @Test
    fun delayedPreviousRunDoesNotResetNewRunStatus() = runTest {
        val events = mutableListOf<String>()
        TaskStopObserver(service, backgroundScope).track(
            token,
            { events.add("stopping") },
            { events.add("stopped") },
            { events.add("finished") },
        )
        state.value = MaaExecutionState.STOPPING
        runCurrent()
        result = MaaCompositionService.StopResult.Success
        state.value = MaaExecutionState.IDLE
        token++
        result = null
        state.value = MaaExecutionState.STARTING
        runCurrent()
        assertEquals(listOf("stopping", "finished"), events)
    }

    @Test
    fun registrationAfterNextRunStartsDoesNotBindOldCallbacksToIt() = runTest {
        val successfulStart = MaaCompositionService.StartResult.Success("test", token)
        token++
        val events = mutableListOf<String>()
        TaskStopObserver(service, backgroundScope).track(
            successfulStart.runToken,
            { events.add("stopping") },
            { events.add("stopped") },
            { events.add("finished") },
        )
        state.value = MaaExecutionState.STOPPING
        runCurrent()
        result = MaaCompositionService.StopResult.Success
        state.value = MaaExecutionState.IDLE
        runCurrent()
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun staleRegistrationDoesNotCancelObserverForCurrentRun() = runTest {
        val observer = TaskStopObserver(service, backgroundScope)
        val oldToken = token
        token++
        val events = mutableListOf<String>()
        observer.track(token, {}, { events.add("current") })
        observer.track(oldToken, {}, { events.add("old") })
        result = MaaCompositionService.StopResult.Success
        state.value = MaaExecutionState.IDLE
        runCurrent()
        assertEquals(listOf("current"), events)
    }
}
