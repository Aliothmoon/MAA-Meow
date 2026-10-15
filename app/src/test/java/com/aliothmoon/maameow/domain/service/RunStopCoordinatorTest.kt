package com.aliothmoon.maameow.domain.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RunStopCoordinatorTest {
    @Test
    fun competingOriginsShareOneStopAndItsFailedResult() = runTest {
        for (firstOrigin in MaaCompositionService.StopOrigin.entries) {
            val coordinator = RunStopCoordinator()
            val completion = CompletableDeferred<MaaCompositionService.StopResult>()
            var origin: MaaCompositionService.StopOrigin? = null
            var calls = 0
            val requests = MaaCompositionService.StopOrigin.entries
                .sortedBy { it != firstOrigin }
                .map { requestedOrigin ->
                    async {
                        coordinator.stop(1, { true }) {
                            origin = requestedOrigin
                            calls++
                            completion.await()
                        }
                    }
                }
            runCurrent()
            assertEquals(1, calls)
            assertEquals(firstOrigin, origin)
            completion.complete(MaaCompositionService.StopResult.Failed)
            requests.forEach { assertSame(MaaCompositionService.StopResult.Failed, it.await()) }
            assertEquals(1, calls)
            assertEquals(firstOrigin, origin)
        }
    }

    @Test
    fun delayedRequestForFinishedRunCannotStopNextRun() = runTest {
        val coordinator = RunStopCoordinator()
        var currentToken = 1L
        var calls = 0
        val first = coordinator.stop(1, { currentToken == 1L }) {
            calls++
            MaaCompositionService.StopResult.Failed
        }
        currentToken = 2L
        val delayed = coordinator.stop(1, { currentToken == 1L }) {
            error("The previous run must not stop the new run")
        }
        assertSame(first, delayed)
        coordinator.stop(2, { currentToken == 2L }) {
            calls++
            MaaCompositionService.StopResult.Success
        }
        assertEquals(2, calls)
    }

    @Test
    fun inactiveAndStaleRequestsDoNotExecuteOrChangeOrigin() = runTest {
        val coordinator = RunStopCoordinator()
        coordinator.stop(1, { false }) { error("No active matching run") }
        var calls = 0
        coordinator.stop(2, { true }) {
            calls++
            MaaCompositionService.StopResult.Success
        }
        coordinator.stop(1, { false }) { error("Stale callback") }
        assertEquals(1, calls)
    }
}
