package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.domain.state.MaaExecutionState
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 使用服务捕获的结束事件检查原因映射与收尾，StateFlow 只提供当前界面状态。
 */
class TaskEndRegistryTest {

    private lateinit var scope: CoroutineScope
    private lateinit var composition: MaaCompositionService
    private lateinit var registry: TaskEndRegistry

    private val state = MutableStateFlow(MaaExecutionState.IDLE)
    private val endEvents = MutableSharedFlow<TaskEndRegistry.EndEvent>(extraBufferCapacity = 16)
    @Volatile
    private var currentRun: TaskEndRegistry.Run? = null
    private var nextRunId = 0L
    @Volatile
    private var closeGameOnEnd = false

    @Volatile
    private var stopOrigin = MaaCompositionService.StopOrigin.USER
    private val emitted = CopyOnWriteArrayList<TaskEndRegistry.Reason>()
    private val pendingSeen = CopyOnWriteArrayList<TaskEndRegistry.Reason>()

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        state.value = MaaExecutionState.IDLE
        stopOrigin = MaaCompositionService.StopOrigin.USER
        emitted.clear()
        pendingSeen.clear()
        composition = mockk(relaxed = true) {
            every { state } returns this@TaskEndRegistryTest.state
            every { taskEndEvents } returns endEvents
            every { currentRun } answers { this@TaskEndRegistryTest.currentRun }
        }
        registry = TaskEndRegistry(composition, scope, closeGameOnEnd = { closeGameOnEnd }).apply { start() }
        scope.launch { registry.taskEnded.collect { emitted.add(it.reason) } }
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** 模拟服务在终态入口登记事件；原因来自生产代码 Run.finish，不由 Registry 推断边沿。 */
    private suspend fun drive(vararg states: MaaExecutionState) {
        for (s in states) {
            delay(150)
            val previous = state.value
            if (s == MaaExecutionState.STARTING || (s == MaaExecutionState.RUNNING &&
                    (currentRun == null || currentRun?.endEvent != null))
            ) currentRun = TaskEndRegistry.Run(++nextRunId)
            val event = if (s == MaaExecutionState.IDLE || s == MaaExecutionState.ERROR) {
                currentRun?.finish(s, previous, stopOrigin)
            } else null
            state.value = s
            if (event != null && (previous == MaaExecutionState.RUNNING || previous == MaaExecutionState.STOPPING)) {
                endEvents.emit(event)
            }
        }
        delay(300)
    }

    @Test
    fun runningToIdle_isNatural() = runBlocking<Unit> {
        registry.armOnce { pendingSeen.add(it) }
        drive(MaaExecutionState.STARTING, MaaExecutionState.RUNNING, MaaExecutionState.IDLE)
        assertEquals(listOf(TaskEndRegistry.Reason.NATURAL), emitted)
        assertEquals(listOf(TaskEndRegistry.Reason.NATURAL), pendingSeen)
    }

    @Test
    fun runningToError_isNatural() = runBlocking<Unit> {
        drive(MaaExecutionState.RUNNING, MaaExecutionState.ERROR)
        assertEquals(listOf(TaskEndRegistry.Reason.NATURAL), emitted)
    }

    @Test
    fun userStop_isManual() = runBlocking<Unit> {
        drive(MaaExecutionState.RUNNING)
        // 运行中登记，避免 IDLE 下的补跑
        registry.armOnce { pendingSeen.add(it) }
        stopOrigin = MaaCompositionService.StopOrigin.USER
        drive(MaaExecutionState.STOPPING, MaaExecutionState.IDLE)
        assertEquals(listOf(TaskEndRegistry.Reason.MANUAL), emitted)
        assertEquals(listOf(TaskEndRegistry.Reason.MANUAL), pendingSeen)
    }

    @Test
    fun callbackStop_isAborted() = runBlocking<Unit> {
        drive(MaaExecutionState.RUNNING)
        // 运行中登记，避免 IDLE 下的补跑
        registry.armOnce { pendingSeen.add(it) }
        stopOrigin = MaaCompositionService.StopOrigin.CALLBACK
        drive(MaaExecutionState.STOPPING, MaaExecutionState.IDLE)
        assertEquals(listOf(TaskEndRegistry.Reason.ABORTED), emitted)
        assertEquals(listOf(TaskEndRegistry.Reason.ABORTED), pendingSeen)
    }

    @Test
    fun callbackStopToError_isAborted() = runBlocking<Unit> {
        drive(MaaExecutionState.RUNNING)
        stopOrigin = MaaCompositionService.StopOrigin.CALLBACK
        drive(MaaExecutionState.STOPPING, MaaExecutionState.ERROR)
        assertEquals(listOf(TaskEndRegistry.Reason.ABORTED), emitted)
    }

    @Test
    fun durationLimitStop_isDurationLimit() = runBlocking<Unit> {
        drive(MaaExecutionState.RUNNING)
        registry.armOnce { pendingSeen.add(it) }
        stopOrigin = MaaCompositionService.StopOrigin.RUN_DURATION_LIMIT
        drive(MaaExecutionState.STOPPING, MaaExecutionState.IDLE)
        assertEquals(listOf(TaskEndRegistry.Reason.DURATION_LIMIT), emitted)
        assertEquals(listOf(TaskEndRegistry.Reason.DURATION_LIMIT), pendingSeen)
    }

    @Test
    fun startingToIdle_emitsNothing() = runBlocking<Unit> {
        drive(MaaExecutionState.STARTING, MaaExecutionState.IDLE)
        assertEquals(emptyList<TaskEndRegistry.Reason>(), emitted)
    }

    @Test
    fun closeGameOn_naturalEnd_stopsVirtualDisplay() = runBlocking<Unit> {
        closeGameOnEnd = true
        drive(MaaExecutionState.RUNNING, MaaExecutionState.IDLE)
        coVerify(exactly = 1) { composition.stopVirtualDisplay() }
    }

    @Test
    fun closeGameOn_manualStop_keepsGame() = runBlocking<Unit> {
        closeGameOnEnd = true
        stopOrigin = MaaCompositionService.StopOrigin.USER
        drive(MaaExecutionState.RUNNING, MaaExecutionState.STOPPING, MaaExecutionState.IDLE)
        coVerify(exactly = 0) { composition.stopVirtualDisplay() }
    }

    @Test
    fun closeGameOff_naturalEnd_keepsGame() = runBlocking<Unit> {
        drive(MaaExecutionState.RUNNING, MaaExecutionState.IDLE)
        coVerify(exactly = 0) { composition.stopVirtualDisplay() }
    }
}
