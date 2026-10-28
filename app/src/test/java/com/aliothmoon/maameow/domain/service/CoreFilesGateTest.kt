package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.domain.state.MaaExecutionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoreFilesGateTest {

    private val mutex = Mutex()
    private val state = MutableStateFlow(MaaExecutionState.IDLE)

    @Test
    fun idle_runsAtOnce() = runTest {
        assertEquals(1, mutex.withLockWhenIdle(state) { 1 })
    }

    @Test
    fun running_waitsUntilIdle() = runTest {
        state.value = MaaExecutionState.RUNNING
        val write = async { mutex.withLockWhenIdle(state) { "written" } }
        runCurrent()
        assertFalse(write.isCompleted)

        state.value = MaaExecutionState.ERROR
        assertEquals("written", write.await())
    }

    @Test
    fun startHoldingLock_blocksWrite_andWriteSeesTheRunAfterwards() = runTest {
        // 模拟 executeStart：持锁期间从 IDLE 走到 RUNNING
        mutex.lock()
        val write = async { mutex.withLockWhenIdle(state) { "written" } }
        runCurrent()
        state.value = MaaExecutionState.STARTING
        state.value = MaaExecutionState.RUNNING
        mutex.unlock()
        runCurrent()
        assertFalse(write.isCompleted)

        state.value = MaaExecutionState.IDLE
        assertEquals("written", write.await())
    }
}
