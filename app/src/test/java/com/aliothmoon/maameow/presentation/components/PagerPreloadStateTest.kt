package com.aliothmoon.maameow.presentation.components

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PagerPreloadStateTest {
    @Test
    fun verticalScrollAndTouchHoldPostponePreloading() = runTest {
        val state = PagerPreloadState { testScheduler.currentTime }
        var ready = false
        state.onPointerInput(true)
        val job = launch { state.awaitIdle(MutableStateFlow(false)); ready = true }
        advanceTimeBy(500)
        assertFalse(ready)
        state.onPointerInput(false)
        advanceTimeBy(100)
        state.onScroll()
        advanceTimeBy(149)
        runCurrent()
        assertFalse(ready)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(ready)
        job.join()
    }

    @Test
    fun pagersShareOnePreloadPerFrameAndRecheckNewInput() = runTest {
        val state = PagerPreloadState { testScheduler.currentTime }
        advanceTimeBy(150)
        assertTrue(state.tryPreload(1))
        assertFalse(state.tryPreload(1))
        assertTrue(state.tryPreload(2))
        state.onPointerInput(true)
        assertFalse(state.tryPreload(3))
        state.onPointerInput(false)
        advanceTimeBy(149)
        assertFalse(state.tryPreload(4))
        advanceTimeBy(1)
        assertTrue(state.tryPreload(5))
    }

    @Test
    fun programmaticPagerTransitionAlsoBlocksPreloading() = runTest {
        val state = PagerPreloadState { testScheduler.currentTime }
        val moving = MutableStateFlow(true)
        var ready = false
        val job = launch { state.awaitIdle(moving); ready = true }
        advanceTimeBy(500)
        assertFalse(ready)
        moving.value = false
        runCurrent()
        assertTrue(ready)
        job.join()
    }
}
