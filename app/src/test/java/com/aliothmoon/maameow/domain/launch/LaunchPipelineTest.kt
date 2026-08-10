package com.aliothmoon.maameow.domain.launch

import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.ScreenSaverController
import com.aliothmoon.maameow.domain.service.TaskEndRegistry
import com.aliothmoon.maameow.domain.service.WakeUnlockEngine
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.service.ScheduleTriggerLogger
import com.aliothmoon.maameow.utils.i18n.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * 驱动真实 [LaunchPipeline] 入口，验证互斥 / 模式 / keyguard / 幂等 / force。
 */
class LaunchPipelineTest {

    private lateinit var scope: CoroutineScope
    private lateinit var mutex: LaunchMutex
    private lateinit var settings: AppSettingsManager
    private lateinit var wake: WakeUnlockEngine
    private lateinit var chainState: TaskChainState
    private lateinit var composition: MaaCompositionService
    private lateinit var logger: ScheduleTriggerLogger
    private lateinit var repository: ScheduleStrategyRepository
    private lateinit var startTaskChain: StartTaskChainUseCase
    private lateinit var screenSaver: ScreenSaverController
    private lateinit var taskEndRegistry: TaskEndRegistry

    private val keyguardLocked = java.util.concurrent.atomic.AtomicBoolean(false)
    private val deviceSecure = java.util.concurrent.atomic.AtomicBoolean(false)
    private val screenInteractive = java.util.concurrent.atomic.AtomicBoolean(true)
    private val startCalls = AtomicInteger(0)
    private val recorded = CopyOnWriteArrayList<ExecutionResult>()
    private val stopCalls = AtomicInteger(0)

    private val runMode = MutableStateFlow(RunMode.BACKGROUND)
    private val unlockType = MutableStateFlow("swipe")
    private val wakeCred = MutableStateFlow("")
    private val compositionState = MutableStateFlow(MaaExecutionState.IDLE)
    private val profileId = MutableStateFlow("profile-1")
    private val isLoaded = MutableStateFlow(true)
    private val chain = MutableStateFlow(
        listOf(
            mockk<TaskChainNode>(relaxed = true) {
                every { enabled } returns true
            },
        ),
    )

    private fun instantCountdown(): CountdownUI = object : CountdownUI {
        override suspend fun await(
            request: LaunchRequest,
            onTick: (remainingSeconds: Int) -> Unit,
            shouldAbort: () -> Boolean,
        ): Boolean {
            onTick(1)
            return false
        }
    }

    /** 进入倒计时后挂起，直到 [release] 完成。 */
    private fun gatedCountdown(entered: CompletableDeferred<Unit>, release: CompletableDeferred<Unit>) =
        object : CountdownUI {
            override suspend fun await(
                request: LaunchRequest,
                onTick: (remainingSeconds: Int) -> Unit,
                shouldAbort: () -> Boolean,
            ): Boolean {
                onTick(1)
                entered.complete(Unit)
                release.await()
                return false
            }
        }

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        mutex = LaunchMutex()
        startCalls.set(0)
        stopCalls.set(0)
        recorded.clear()
        keyguardLocked.set(false)
        deviceSecure.set(false)
        screenInteractive.set(true)
        unlockType.value = "swipe"
        runMode.value = RunMode.BACKGROUND
        compositionState.value = MaaExecutionState.IDLE

        settings = mockk(relaxed = true) {
            every { runMode } returns this@LaunchPipelineTest.runMode
            every { wakeCredential } returns wakeCred
            every { wakeUnlockType } returns unlockType
        }
        wake = mockk(relaxed = true)
        coEvery { wake.unlock(any()) } returns WakeUnlockEngine.WakeResult.OK
        coEvery { wake.lockAndSleep() } returns WakeUnlockEngine.WakeResult.OK

        screenSaver = mockk(relaxed = true)
        // relaxed 的 Boolean 默认为 false
        coEvery { screenSaver.show() } returns true

        chainState = mockk(relaxed = true) {
            every { isLoaded } returns this@LaunchPipelineTest.isLoaded
            every { profileId } returns this@LaunchPipelineTest.profileId
            every { chain } returns this@LaunchPipelineTest.chain
            coEvery { switchProfile(any()) } just runs
        }
        composition = mockk(relaxed = true) {
            every { state } returns compositionState
            coEvery { stop() } coAnswers {
                stopCalls.incrementAndGet()
                // STOPPING → IDLE 须留窗口，否则 StateFlow 合并
                compositionState.value = MaaExecutionState.STOPPING
                delay(100)
                compositionState.value = MaaExecutionState.IDLE
                delay(100)
                mockk(relaxed = true)
            }
            coEvery { stopVirtualDisplay() } just runs
        }
        taskEndRegistry = TaskEndRegistry(composition, scope).apply { start() }
        val logSession = mockk<ScheduleTriggerLogger.Session>(relaxed = true) {
            every { append(any()) } just runs
            every { end(any(), any()) } just runs
        }
        logger = mockk(relaxed = true) {
            every { open(any(), any(), any(), any()) } returns logSession
            every {
                writeClosed(
                    strategyId = any(),
                    strategyName = any(),
                    scheduledTimeMs = any(),
                    result = any(),
                    message = any(),
                    runMode = any(),
                )
            } just runs
            every { resolveMessage(any()) } answers { firstArg<UiText?>()?.toString() }
        }
        repository = mockk(relaxed = true)
        // B1: 真正挂起，确保 cancel 后仍能在 NonCancellable 下完成落库
        coEvery {
            repository.recordExecutionResult(any(), any(), any(), any())
        } coAnswers {
            yield()
            recorded.add(secondArg())
        }

        startTaskChain = mockk(relaxed = true)
        coEvery {
            startTaskChain.invoke(
                chain = any(),
                context = any(),
                scheduleLabel = any(),
            )
        } coAnswers {
            startCalls.incrementAndGet()
            compositionState.value = MaaExecutionState.RUNNING
            StartTaskChainUseCase.Result.Success
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun pipeline(countdown: CountdownUI = instantCountdown()) = LaunchPipeline(
        scope = scope,
        mutex = mutex,
        appSettingsManager = settings,
        wakeUnlockEngine = wake,
        chainState = chainState,
        compositionService = composition,
        triggerLogger = logger,
        scheduleRepository = repository,
        startTaskChain = startTaskChain,
        countdownUI = countdown,
        screenSaver = screenSaver,
        taskEndRegistry = taskEndRegistry,
        keyguardLocked = { keyguardLocked.get() },
        deviceSecure = { deviceSecure.get() },
        screenInteractive = { screenInteractive.get() },
        activityLauncher = { true },
    )

    private fun scheduleRequest(
        id: String = "req-1",
        force: Boolean = false,
        autoSleep: Boolean = false,
        skipIfAwake: Boolean = false,
        autoScreenSaver: Boolean = false,
    ) = LaunchRequest(
        requestId = id,
        source = LaunchSource.Schedule,
        profileId = "profile-1",
        displayName = "Test",
        scheduledTimeMs = 1_000L,
        forceStart = force,
        autoScreenSaver = autoScreenSaver,
        autoSleepAfterTask = autoSleep,
        skipAutoSleepIfAwake = skipIfAwake,
        strategyId = "strat-1",
        countdownSeconds = 1,
    )

    /** StateFlow 合并，赋值间须留窗口 */
    private suspend fun driveTaskToEnd() {
        delay(200)
        compositionState.value = MaaExecutionState.RUNNING
        delay(200)
        compositionState.value = MaaExecutionState.IDLE
    }

    @Test
    fun autoSleepWithoutSkipOption_sleepsEvenWhenAwake() = runBlocking<Unit> {
        screenInteractive.set(true)
        keyguardLocked.set(false)
        pipeline().execute(scheduleRequest(autoSleep = true)).join()
        driveTaskToEnd()
        coVerify(timeout = 5_000) { wake.lockAndSleep() }
    }

    @Test
    fun autoSleepSkipIfAwake_awakeAndUnlocked_skipsSleep() = runBlocking<Unit> {
        screenInteractive.set(true)
        keyguardLocked.set(false)
        pipeline().execute(scheduleRequest(autoSleep = true, skipIfAwake = true)).join()
        driveTaskToEnd()
        delay(500)
        assertEquals(1, startCalls.get())
        coVerify(exactly = 0) { wake.lockAndSleep() }
    }

    @Test
    fun autoSleepSkipIfAwake_screenOff_stillSleeps() = runBlocking<Unit> {
        screenInteractive.set(false)
        keyguardLocked.set(false)
        pipeline().execute(scheduleRequest(autoSleep = true, skipIfAwake = true)).join()
        driveTaskToEnd()
        coVerify(timeout = 5_000) { wake.lockAndSleep() }
    }

    @Test
    fun autoSleepSkipIfAwake_awakeButLocked_stillSleeps() = runBlocking<Unit> {
        screenInteractive.set(true)
        keyguardLocked.set(true)
        pipeline().execute(scheduleRequest(autoSleep = true, skipIfAwake = true)).join()
        driveTaskToEnd()
        coVerify(timeout = 5_000) { wake.lockAndSleep() }
    }

    @Test
    fun autoSleepSkipIfAwake_screenOffAndLocked_stillSleeps() = runBlocking<Unit> {
        screenInteractive.set(false)
        keyguardLocked.set(true)
        pipeline().execute(scheduleRequest(autoSleep = true, skipIfAwake = true)).join()
        driveTaskToEnd()
        coVerify(timeout = 5_000) { wake.lockAndSleep() }
    }

    @Test
    fun autoScreenSaver_idleDevice_showsThenHides() = runBlocking<Unit> {
        screenInteractive.set(false)
        pipeline().execute(scheduleRequest(autoScreenSaver = true)).join()
        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        coVerify(exactly = 1) { screenSaver.show() }
        driveTaskToEnd()
        coVerify(timeout = 5_000, exactly = 1) { screenSaver.hide() }
    }

    @Test
    fun autoScreenSaver_foreground_neverShows() = runBlocking<Unit> {
        runMode.value = RunMode.FOREGROUND
        screenInteractive.set(false)
        pipeline().execute(scheduleRequest(autoScreenSaver = true)).join()
        assertEquals(1, startCalls.get())
        coVerify(exactly = 0) { screenSaver.show() }
    }

    @Test
    fun autoScreenSaver_awakeAndUnlocked_neverShows() = runBlocking<Unit> {
        screenInteractive.set(true)
        keyguardLocked.set(false)
        pipeline().execute(scheduleRequest(autoScreenSaver = true)).join()
        assertEquals(1, startCalls.get())
        coVerify(exactly = 0) { screenSaver.show() }
    }

    @Test
    fun autoScreenSaver_startFails_hidesImmediately() = runBlocking<Unit> {
        screenInteractive.set(false)
        coEvery {
            startTaskChain.invoke(chain = any(), context = any(), scheduleLabel = any())
        } returns StartTaskChainUseCase.Result.Failed(
            executionResult = ExecutionResult.FAILED_START,
            message = mockk(relaxed = true),
        )
        pipeline().execute(scheduleRequest(autoScreenSaver = true)).join()
        assertEquals(listOf(ExecutionResult.FAILED_START), recorded.toList())
        coVerify(exactly = 1) { screenSaver.show() }
        coVerify(exactly = 1) { screenSaver.hide() }
    }

    @Test
    fun autoScreenSaver_withAutoSleep_hidesBeforeSleeping() = runBlocking<Unit> {
        screenInteractive.set(false)
        pipeline().execute(scheduleRequest(autoScreenSaver = true, autoSleep = true)).join()
        driveTaskToEnd()
        coVerify(timeout = 5_000) { wake.lockAndSleep() }
        coVerifyOrder {
            screenSaver.hide()
            wake.lockAndSleep()
        }
    }

    @Test
    fun autoScreenSaverDisabled_neverShows() = runBlocking<Unit> {
        screenInteractive.set(false)
        pipeline().execute(scheduleRequest()).join()
        assertEquals(1, startCalls.get())
        coVerify(exactly = 0) { screenSaver.show() }
    }

    @Test
    fun autoScreenSaver_showFails_doesNotArmRelease() = runBlocking<Unit> {
        screenInteractive.set(false)
        coEvery { screenSaver.show() } returns false
        pipeline().execute(scheduleRequest(autoScreenSaver = true)).join()
        driveTaskToEnd()
        delay(500)
        coVerify(exactly = 0) { screenSaver.hide() }
    }

    /** force 须先 disarm，否则旧 autoSleep 会落在新一轮 */
    @Test
    fun forceStart_dropsPreviousRunPostActions() = runBlocking<Unit> {
        screenInteractive.set(false)
        val p = pipeline()
        p.execute(scheduleRequest("a", autoSleep = true, autoScreenSaver = true)).join()
        assertEquals(1, startCalls.get())
        delay(200)

        p.execute(scheduleRequest("b", force = true)).join()
        delay(500)
        assertEquals(2, startCalls.get())
        coVerify(exactly = 0) { wake.lockAndSleep() }
    }

    /** arm 前任务已结束须补跑收尾 */
    @Test
    fun taskEndsBeforeArming_stillRunsPostActions() = runBlocking<Unit> {
        screenInteractive.set(false)
        coEvery {
            startTaskChain.invoke(chain = any(), context = any(), scheduleLabel = any())
        } coAnswers {
            startCalls.incrementAndGet()
            compositionState.value = MaaExecutionState.RUNNING
            delay(100)
            compositionState.value = MaaExecutionState.IDLE
            delay(100)
            StartTaskChainUseCase.Result.Success
        }
        pipeline().execute(scheduleRequest(autoScreenSaver = true)).join()
        coVerify(timeout = 5_000, exactly = 1) { screenSaver.hide() }
    }

    private fun externalRequest(id: String = "ext-1") = LaunchRequest(
        requestId = id,
        source = LaunchSource.External,
        profileId = "profile-1",
        displayName = "External",
        scheduledTimeMs = 1_000L,
        strategyId = "strat-ext",
        countdownSeconds = 1,
    )

    @Test
    fun concurrentWithoutForce_secondIsSkippedBusy() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val p = pipeline(countdown = gatedCountdown(entered, release))
        val first = p.execute(scheduleRequest("a"))
        withTimeout(5_000) { entered.await() }
        p.execute(scheduleRequest("b")).join()
        assertEquals(listOf(ExecutionResult.SKIPPED_BUSY), recorded.toList())
        // busy：writeClosed 独立文件；in-flight 用 open 一次
        io.mockk.verify(exactly = 1) { logger.open(any(), any(), any(), any()) }
        io.mockk.verify(exactly = 1) {
            logger.writeClosed(
                strategyId = any(),
                strategyName = any(),
                scheduledTimeMs = any(),
                result = ExecutionResult.SKIPPED_BUSY,
                message = any(),
                runMode = any(),
            )
        }
        release.complete(Unit)
        first.join()
        io.mockk.verify(exactly = 1) { logger.open(any(), any(), any(), any()) }
    }

    @Test
    fun scheduleKeyguardLocked_skipsWhenSecureWithoutPin() = runBlocking<Unit> {
        keyguardLocked.set(true)
        deviceSecure.set(true)
        unlockType.value = "swipe"
        pipeline().execute(scheduleRequest()).join()
        assertEquals(listOf(ExecutionResult.SKIPPED_LOCKED), recorded.toList())
        assertEquals(0, startCalls.get())
    }

    @Test
    fun externalKeyguardLocked_doesNotSkip() = runBlocking<Unit> {
        keyguardLocked.set(true)
        pipeline().execute(externalRequest()).join()
        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        assertEquals(1, startCalls.get())
    }

    @Test
    fun foregroundSchedule_isAllowed() = runBlocking<Unit> {
        runMode.value = RunMode.FOREGROUND
        pipeline().execute(scheduleRequest()).join()
        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        assertEquals(1, startCalls.get())
    }

    @Test
    fun sameRequestId_isIdempotent() = runBlocking<Unit> {
        val p = pipeline()
        p.execute(scheduleRequest("same")).join()
        p.execute(scheduleRequest("same")).join()
        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        assertEquals(1, startCalls.get())
    }

    @Test
    fun cancelDuringCountdown_endsCancelled() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        val p = pipeline(countdown = object : CountdownUI {
            override suspend fun await(
                request: LaunchRequest,
                onTick: (remainingSeconds: Int) -> Unit,
                shouldAbort: () -> Boolean,
            ): Boolean {
                onTick(2)
                entered.complete(Unit)
                withTimeout(5_000) {
                    while (!shouldAbort()) {
                        kotlinx.coroutines.delay(10)
                    }
                }
                return false
            }
        })
        val job = p.execute(scheduleRequest())
        withTimeout(5_000) { entered.await() }
        p.submit(LaunchUserEvent.Cancel)
        job.join()
        assertEquals(listOf(ExecutionResult.CANCELLED), recorded.toList())
    }

    @Test
    fun forceStartStopsRunningTask() = runBlocking<Unit> {
        compositionState.value = MaaExecutionState.RUNNING
        pipeline().execute(scheduleRequest(force = true)).join()
        assertTrue(stopCalls.get() >= 1)
        coVerify { composition.stopVirtualDisplay() }
        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
    }

    @Test
    fun runningWithoutForce_skippedBusy() = runBlocking<Unit> {
        compositionState.value = MaaExecutionState.RUNNING
        pipeline().execute(scheduleRequest(force = false)).join()
        assertEquals(listOf(ExecutionResult.SKIPPED_BUSY), recorded.toList())
        assertEquals(0, startCalls.get())
        // 已拿到 mutex 并 open 后发现 composition 忙：本 Session end（非 writeClosed）
        io.mockk.verify(exactly = 1) { logger.open(any(), any(), any(), any()) }
        io.mockk.verify(exactly = 0) {
            logger.writeClosed(
                strategyId = any(),
                strategyName = any(),
                scheduledTimeMs = any(),
                result = any(),
                message = any(),
                runMode = any(),
            )
        }
    }

    /** 前台无倒计时：不 presentUi，直接启动。 */
    @Test
    fun foreground_skipsCountdownAndStarts() = runBlocking<Unit> {
        runMode.value = RunMode.FOREGROUND
        var countdownCalled = false
        val p = pipeline(countdown = object : CountdownUI {
            override suspend fun await(
                request: LaunchRequest,
                onTick: (remainingSeconds: Int) -> Unit,
                shouldAbort: () -> Boolean,
            ): Boolean {
                countdownCalled = true
                return false
            }
        })
        p.execute(scheduleRequest("fg-1")).join()
        assertTrue(!countdownCalled)
        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        assertEquals(1, startCalls.get())
    }

    /** LAUNCH_PROFILE 前台同样跳过倒计时。 */
    @Test
    fun foregroundExternal_alsoSkipsCountdown() = runBlocking<Unit> {
        runMode.value = RunMode.FOREGROUND
        var countdownCalled = false
        val p = pipeline(countdown = object : CountdownUI {
            override suspend fun await(
                request: LaunchRequest,
                onTick: (remainingSeconds: Int) -> Unit,
                shouldAbort: () -> Boolean,
            ): Boolean {
                countdownCalled = true
                return false
            }
        })
        p.execute(externalRequest("ext-fg")).join()
        assertTrue(!countdownCalled)
        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
    }

    /** 后台无论 Schedule/External 都有 Dialog 倒计时（presentUi=true）。 */
    @Test
    fun background_alwaysPresentUiCountdown() = runBlocking<Unit> {
        runMode.value = RunMode.BACKGROUND
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val p = pipeline(countdown = gatedCountdown(entered, release))
        val job = p.execute(scheduleRequest("bg-1"))
        withTimeout(5_000) { entered.await() }
        val session = p.session.value
        assertTrue(session is LaunchSession.InFlight && session.presentUi)
        release.complete(Unit)
        job.join()
        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
    }

    @Test
    fun mutexReleasedAfterStart() = runBlocking<Unit> {
        val p = pipeline()
        p.execute(scheduleRequest()).join()
        assertNull(mutex.current)
        // 第一轮任务得先跑完，否则第二轮会被 busy 挡掉，测不到 mutex
        compositionState.value = MaaExecutionState.IDLE
        p.execute(scheduleRequest("req-2")).join()
        assertEquals(2, startCalls.get())
        assertEquals(
            listOf(ExecutionResult.STARTED, ExecutionResult.STARTED),
            recorded.toList(),
        )
    }

    @Test
    fun startNowDuringCountdown_stillStarts() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        val p = pipeline(countdown = object : CountdownUI {
            override suspend fun await(
                request: LaunchRequest,
                onTick: (remainingSeconds: Int) -> Unit,
                shouldAbort: () -> Boolean,
            ): Boolean {
                onTick(3)
                entered.complete(Unit)
                withTimeout(5_000) {
                    while (!shouldAbort()) {
                        kotlinx.coroutines.delay(10)
                    }
                }
                return true
            }
        })
        val job = p.execute(scheduleRequest())
        withTimeout(5_000) { entered.await() }
        p.submit(LaunchUserEvent.StartNow)
        job.join()
        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        assertEquals(1, startCalls.get())
    }

    /**
     * 准则 2：倒计时中途 forceStart 必须抢占旧流并启动新流；
     * 旧 finally 不得擦掉新 session / 关掉新 journal（由 join + journal CAS 保证）。
     */
    @Test
    fun forceStartWhileCountdown_preemptsPriorAndStartsNew() = runBlocking<Unit> {
        val firstEntered = CompletableDeferred<Unit>()
        val p = pipeline(
            countdown = object : CountdownUI {
                override suspend fun await(
                    request: LaunchRequest,
                    onTick: (remainingSeconds: Int) -> Unit,
                    shouldAbort: () -> Boolean,
                ): Boolean {
                    onTick(5)
                    if (request.requestId == "a") {
                        firstEntered.complete(Unit)
                        // 被 cancel 时 shouldAbort 或 Job 取消
                        withTimeout(10_000) {
                            while (!shouldAbort()) {
                                kotlinx.coroutines.delay(10)
                            }
                        }
                    }
                    return false
                }
            },
        )
        val first = p.execute(scheduleRequest("a", force = false))
        withTimeout(5_000) { firstEntered.await() }
        // 旧流仍持 mutex + InFlight Counting
        assertTrue(mutex.current?.requestId == "a")
        assertTrue(p.session.value is LaunchSession.InFlight)

        p.execute(scheduleRequest("b", force = true)).join()
        first.join()

        // 新流应成功启动；旧流 CANCELLED；session 最终 Idle；mutex 释放
        assertEquals(1, startCalls.get())
        assertTrue(recorded.contains(ExecutionResult.STARTED))
        assertTrue(
            recorded.contains(ExecutionResult.CANCELLED)
                || recorded.count { it == ExecutionResult.STARTED } == 1,
        )
        // STARTED 必须是最后一次成功记录之一；至少有两次 record（旧取消 + 新启动）
        assertTrue(recorded.size >= 2)
        assertEquals(ExecutionResult.STARTED, recorded.last())
        assertNull(mutex.current)
        assertTrue(p.session.value is LaunchSession.Idle)
    }
}
