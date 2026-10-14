package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskRemovalCoordinatorTest {

    @Test
    fun enabledOnAndroid14KeepsTaskAndRemoteProcess() = runTest {
        val env = environment(
            state = MaaExecutionState.RUNNING,
            continueAfterRemoval = true,
            sdkInt = 34,
        )

        assertFalse(requestAndWait(env))

        coVerify(exactly = 0) { env.composition.stop(any()) }
        assertFalse(env.unbound)
    }

    @Test
    fun removalKeepsOriginalBehaviorBelowAndroid14() = runTest {
        val env = environment(
            state = MaaExecutionState.RUNNING,
            continueAfterRemoval = true,
            sdkInt = 33,
        )

        assertFalse(requestAndWait(env))

        coVerify(exactly = 0) { env.composition.stop(any()) }
        assertFalse(env.unbound)
        assertTrue(env.terminateCount == 0)
    }

    @Test
    fun runningTaskStopsBeforeRemoteProcessIsUnbound() = runTest {
        val events = mutableListOf<String>()
        val env = environment(state = MaaExecutionState.RUNNING, events = events)

        assertTrue(requestAndWait(env))

        assertTrue(env.unbound)
        assertTrue(events.indexOf("stop") < events.indexOf("unbind"))
    }

    @Test
    fun stoppingTaskIsTakenOverInsteadOfWaitingForever() = runTest {
        val env = environment(state = MaaExecutionState.STOPPING)

        assertTrue(requestAndWait(env))

        coVerify(exactly = 1) {
            env.composition.stop(MaaCompositionService.StopOrigin.USER)
        }
        assertTrue(env.unbound)
    }

    @Test
    fun stopFailureStillUnbindsAndFinishesLocalState() = runTest {
        val env = environment(state = MaaExecutionState.RUNNING)
        coEvery { env.composition.stop(any()) } throws IllegalStateException("stop failed")

        assertTrue(requestAndWait(env))

        assertTrue(env.unbound)
        coVerify(exactly = 1) { env.composition.completeStopAfterRemoteExit() }
    }

    @Test
    fun stopTimeoutTerminatesAppWithoutWaitingOnTheSameRemoteBinder() = runTest {
        val env = environment(state = MaaExecutionState.STOPPING, stopTimeoutMs = 1_000L)
        coEvery { env.composition.stop(any()) } coAnswers { awaitCancellation() }

        assertTrue(requestAndWait(env))

        assertFalse(env.unbound)
        assertTrue(env.terminateCount == 1)
        coVerify(exactly = 0) { env.composition.completeStopAfterRemoteExit() }
    }

    @Test
    fun duplicateCallbacksShareOneCleanup() = runTest {
        val env = environment(state = MaaExecutionState.RUNNING)
        val firstDone = CompletableDeferred<Boolean>()
        val secondDone = CompletableDeferred<Boolean>()

        env.coordinator.requestTaskRemoval { firstDone.complete(it) }
        env.coordinator.requestTaskRemoval { secondDone.complete(it) }

        assertTrue(firstDone.await())
        assertTrue(secondDone.await())

        coVerify(exactly = 1) { env.composition.stop(any()) }
        assertTrue(env.terminateCount == 1)
    }

    @Test
    fun waitsForSettingsBeforeChoosingCleanupBehavior() = runTest {
        val env = environment(state = MaaExecutionState.RUNNING)
        coEvery { env.settings.awaitLoaded() } coAnswers {
            env.continueAfterRemoval.value = true
        }

        assertFalse(requestAndWait(env))

        coVerify(exactly = 0) { env.composition.stop(any()) }
        assertFalse(env.unbound)
        assertTrue(env.terminateCount == 0)
    }

    @Test
    fun reopeningAppBeforeCleanupFinishesKeepsNewProcess() = runTest {
        val env = environment(state = MaaExecutionState.RUNNING)
        val allowStop = CompletableDeferred<Unit>()
        coEvery { env.composition.stop(any()) } coAnswers {
            allowStop.await()
            MaaCompositionService.StopResult.Success
        }
        val done = CompletableDeferred<Boolean>()

        env.coordinator.requestTaskRemoval { done.complete(it) }
        runCurrent()
        env.coordinator.onAppForeground()
        allowStop.complete(Unit)

        assertFalse(done.await())
        assertFalse(env.unbound)
        assertTrue(env.terminateCount == 0)
    }

    @Test
    fun delayedRemovalCallbackDoesNotCleanUpForegroundApp() = runTest {
        val env = environment(state = MaaExecutionState.RUNNING)
        env.coordinator.onAppForeground()

        assertFalse(requestAndWait(env))

        coVerify(exactly = 0) { env.composition.stop(any()) }
        assertFalse(env.unbound)
        assertTrue(env.terminateCount == 0)
    }

    @Test
    fun secondRemovalAfterReopenQueuesFreshCleanup() = runTest {
        val env = environment(state = MaaExecutionState.RUNNING)
        val allowFirstStop = CompletableDeferred<Unit>()
        coEvery { env.composition.stop(any()) } coAnswers {
            allowFirstStop.await()
            MaaCompositionService.StopResult.Success
        }
        val firstDone = CompletableDeferred<Boolean>()
        val secondDone = CompletableDeferred<Boolean>()

        env.coordinator.requestTaskRemoval { firstDone.complete(it) }
        runCurrent()
        env.coordinator.onAppForeground()
        env.coordinator.onAppBackground()
        env.coordinator.requestTaskRemoval { secondDone.complete(it) }
        allowFirstStop.complete(Unit)

        assertFalse(firstDone.await())
        assertTrue(secondDone.await())
        assertTrue(env.unbound)
        assertTrue(env.terminateCount == 1)
    }

    private suspend fun requestAndWait(env: Environment): Boolean {
        val done = CompletableDeferred<Boolean>()
        env.coordinator.requestTaskRemoval { done.complete(it) }
        return done.await()
    }

    private fun TestScope.environment(
        state: MaaExecutionState,
        continueAfterRemoval: Boolean = false,
        sdkInt: Int = 34,
        stopTimeoutMs: Long = 10_000L,
        events: MutableList<String> = mutableListOf(),
    ): Environment {
        val continueAfterRemovalFlow = MutableStateFlow(continueAfterRemoval)
        val settings = mockk<AppSettingsManager> {
            every { continueTaskAfterTaskRemoved } returns continueAfterRemovalFlow
            coJustRun { awaitLoaded() }
        }
        val composition = mockk<MaaCompositionService> {
            every { this@mockk.state } returns MutableStateFlow(state)
            coEvery { stop(any()) } coAnswers {
                events += "stop"
                MaaCompositionService.StopResult.Success
            }
            coJustRun { completeStopAfterRemoteExit() }
        }
        val env = Environment(
            composition = composition,
            settings = settings,
            continueAfterRemoval = continueAfterRemovalFlow,
        )
        env.coordinator = TaskRemovalCoordinator(
            appSettings = settings,
            compositionService = composition,
            sdkInt = sdkInt,
            stopTimeoutMs = stopTimeoutMs,
            unbindRemote = {
                events += "unbind"
                env.unbound = true
            },
            terminateApp = { env.terminateCount++ },
            cleanupScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )
        return env
    }

    private class Environment(
        val composition: MaaCompositionService,
        val settings: AppSettingsManager,
        val continueAfterRemoval: MutableStateFlow<Boolean>,
    ) {
        lateinit var coordinator: TaskRemovalCoordinator
        var unbound: Boolean = false
        var terminateCount: Int = 0
    }
}
