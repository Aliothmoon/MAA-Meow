package com.aliothmoon.maameow.domain.launch

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.models.UnlockCredential
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.MaaNotificationCenter
import com.aliothmoon.maameow.domain.service.ScreenSaverController
import com.aliothmoon.maameow.domain.service.TaskEndRegistry
import com.aliothmoon.maameow.domain.service.UnlockGestureReader
import com.aliothmoon.maameow.domain.service.WakeUnlockEngine
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.service.ScheduleTriggerLogger
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
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
    private lateinit var gestures: UnlockGestureReader
    private lateinit var chainState: TaskChainState
    private lateinit var composition: MaaCompositionService
    private lateinit var logger: ScheduleTriggerLogger
    private lateinit var logSession: ScheduleTriggerLogger.Session
    private lateinit var repository: ScheduleStrategyRepository
    private lateinit var startTaskChain: StartTaskChainUseCase
    private lateinit var screenSaver: ScreenSaverController
    private lateinit var taskEndRegistry: TaskEndRegistry
    private lateinit var notificationCenter: MaaNotificationCenter

    private val keyguardLocked = java.util.concurrent.atomic.AtomicBoolean(false)
    private val deviceLocked = java.util.concurrent.atomic.AtomicBoolean(false)
    private val screenInteractive = java.util.concurrent.atomic.AtomicBoolean(true)
    private val startCalls = AtomicInteger(0)
    private val recorded = CopyOnWriteArrayList<ExecutionResult>()
    private val stopCalls = AtomicInteger(0)
    private val uiLaunches = AtomicInteger(0)
    private val displayPowerCommands = CopyOnWriteArrayList<Boolean>()

    @Volatile
    private var remoteBlocker: BackendBlock? = null

    private val blacklist = MutableStateFlow<Set<String>>(emptySet())
    private val foregroundProbes = AtomicInteger(0)

    @Volatile
    private var foregroundPkg: String? = null

    @Volatile
    private var probeTimesOut = false

    @Volatile
    private var lastPresentation: LaunchPresentation? = null

    private var current: LaunchPipeline? = null

    private val runMode = MutableStateFlow(RunMode.BACKGROUND)
    private val unlockType = MutableStateFlow("swipe")
    private val wakeCred = MutableStateFlow("")
    private val compositionState = MutableStateFlow(MaaExecutionState.IDLE)
    private val closeAppOnTaskEnd = MutableStateFlow(false)
    private val useHardwareScreenOff = MutableStateFlow(false)
    @Volatile
    private var stopOrigin = MaaCompositionService.StopOrigin.USER
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
            lastPresentation = (current?.session?.value as? LaunchSession.InFlight)?.presentation
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
        uiLaunches.set(0)
        displayPowerCommands.clear()
        useHardwareScreenOff.value = false
        remoteBlocker = null
        blacklist.value = emptySet()
        foregroundPkg = null
        foregroundProbes.set(0)
        probeTimesOut = false
        lastPresentation = null
        recorded.clear()
        keyguardLocked.set(false)
        deviceLocked.set(false)
        screenInteractive.set(true)
        unlockType.value = "swipe"
        runMode.value = RunMode.BACKGROUND
        compositionState.value = MaaExecutionState.IDLE
        closeAppOnTaskEnd.value = false
        stopOrigin = MaaCompositionService.StopOrigin.USER

        settings = mockk(relaxed = true) {
            every { runMode } returns this@LaunchPipelineTest.runMode
            every { closeAppOnTaskEnd } returns this@LaunchPipelineTest.closeAppOnTaskEnd
            every { useHardwareScreenOff } returns this@LaunchPipelineTest.useHardwareScreenOff
            every { wakeCredential } returns wakeCred
            every { wakeUnlockType } returns unlockType
            every { scheduleAppBlacklist } returns blacklist
        }
        wake = mockk(relaxed = true)
        coEvery { wake.unlock(any()) } returns WakeUnlockEngine.WakeResult.OK
        coEvery { wake.lockAndSleep() } returns WakeUnlockEngine.WakeResult.OK

        gestures = mockk(relaxed = true)
        coEvery { gestures.readJson() } returns ""

        screenSaver = mockk(relaxed = true)
        // relaxed 的 Boolean 默认为 false
        every { screenSaver.isShowing() } returns false
        coEvery { screenSaver.show() } returns true

        chainState = mockk(relaxed = true) {
            every { isLoaded } returns this@LaunchPipelineTest.isLoaded
            every { profileId } returns this@LaunchPipelineTest.profileId
            every { chain } returns this@LaunchPipelineTest.chain
            every { profiles } returns MutableStateFlow(emptyList())
            coEvery { switchProfile(any()) } just runs
        }
        composition = mockk(relaxed = true) {
            every { state } returns compositionState
            every { lastStopOrigin } answers { stopOrigin }
            coEvery { stop(any()) } coAnswers {
                stopCalls.incrementAndGet()
                stopOrigin = firstArg()
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
        logSession = mockk(relaxed = true) {
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
        notificationCenter = mockk(relaxed = true)
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
        unlockGestures = gestures,
        chainState = chainState,
        compositionService = composition,
        triggerLogger = logger,
        scheduleRepository = repository,
        startTaskChain = startTaskChain,
        countdownUI = countdown,
        screenSaver = screenSaver,
        taskEndRegistry = taskEndRegistry,
        notificationCenter = notificationCenter,
        keyguardLocked = { keyguardLocked.get() },
        deviceLocked = { deviceLocked.get() },
        screenInteractive = { screenInteractive.get() },
        activityLauncher = {
            uiLaunches.incrementAndGet()
            true
        },
        remoteAccessBlocker = { remoteBlocker },
        foregroundPackage = {
            foregroundProbes.incrementAndGet()
            if (probeTimesOut) withTimeout(1) { delay(1_000) }
            foregroundPkg
        },
        appLabel = { "label:$it" },
        setDisplayPower = { displayPowerCommands.add(it) },
    ).also { current = it }

    private fun givenWakeGate(
        interactive: Boolean = true,
        keyguard: Boolean = false,
        locked: Boolean = false,
        saverShowing: Boolean = false,
        type: String = "swipe",
        pin: String = "",
        gestureJson: String = "",
    ) {
        screenInteractive.set(interactive)
        keyguardLocked.set(keyguard)
        deviceLocked.set(locked)
        every { screenSaver.isShowing() } returns saverShowing
        unlockType.value = type
        wakeCred.value = pin
        coEvery { gestures.readJson() } returns gestureJson
    }

    private fun scheduleRequest(
        id: String = "req-1",
        force: Boolean = false,
        autoSleep: Boolean = false,
        skipIfAwake: Boolean = false,
        autoScreenSaver: Boolean = false,
        closeGame: Boolean = false,
        silent: Boolean = false,
    ) = LaunchRequest(
        requestId = id,
        source = LaunchSource.Schedule,
        profileId = "profile-1",
        displayName = "Test",
        scheduledTimeMs = 1_000L,
        forceStart = force,
        autoScreenSaver = autoScreenSaver,
        silentStartWhenInUse = silent,
        closeGameAfterTask = closeGame,
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

    /** 模拟停止：STOPPING → IDLE，来源由 [origin] 决定 */
    private suspend fun driveTaskToStop(origin: MaaCompositionService.StopOrigin) {
        delay(200)
        compositionState.value = MaaExecutionState.RUNNING
        delay(200)
        stopOrigin = origin
        compositionState.value = MaaExecutionState.STOPPING
        delay(200)
        compositionState.value = MaaExecutionState.IDLE
    }

    // Shizuku 没起来时解锁注入必失败，以前被报成锁屏 / 拉起界面失败
    @Test
    fun remoteUnavailable_failsStartBeforeUnlock() = runBlocking<Unit> {
        val reason = uiTextOf(R.string.runlog_backend_unavailable, "Shizuku")
        remoteBlocker = BackendBlock(reason)
        givenWakeGate(interactive = false, keyguard = true, locked = true)

        pipeline().execute(scheduleRequest()).join()

        assertEquals(listOf(ExecutionResult.FAILED_START), recorded.toList())
        verify { logSession.end(ExecutionResult.FAILED_START, reason) }
        coVerify(exactly = 0) { wake.unlock(any()) }
        assertEquals(0, uiLaunches.get())
        assertEquals(0, startCalls.get())
        // 界面没拉起来，toast 没人看得到
        verify(exactly = 1) {
            notificationCenter.notifyLaunchNotStarted("Test", ExecutionResult.FAILED_START, reason)
        }
    }

    // 技术原因只进触发日志，通知里不放英文串
    @Test
    fun connectFailure_detailOnlyInTriggerLog() = runBlocking<Unit> {
        val reason = uiTextOf(R.string.runlog_backend_connect_failed, "Shizuku")
        remoteBlocker = BackendBlock(reason, detail = "launcher exited early code=1")

        pipeline().execute(scheduleRequest()).join()

        verify { logSession.append(uiTextOf(R.string.schedule_log_backend_connect_cause, "launcher exited early code=1")) }
        verify { logSession.end(ExecutionResult.FAILED_START, reason) }
        verify(exactly = 1) {
            notificationCenter.notifyLaunchNotStarted("Test", ExecutionResult.FAILED_START, reason)
        }
    }

    // 强制启动若先停在跑的再查后端，后端挂了就两头落空
    @Test
    fun forceStart_remoteUnavailable_keepsRunningTask() = runBlocking<Unit> {
        compositionState.value = MaaExecutionState.RUNNING
        remoteBlocker = BackendBlock(uiTextOf(R.string.runlog_backend_unavailable, "Shizuku"))

        pipeline().execute(scheduleRequest(force = true)).join()

        assertEquals(listOf(ExecutionResult.FAILED_START), recorded.toList())
        assertEquals(0, stopCalls.get())
        assertEquals(MaaExecutionState.RUNNING, compositionState.value)
    }

    @Test
    fun forcePreempt_remoteUnavailable_keepsInFlightLaunch() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val p = pipeline(countdown = gatedCountdown(entered, release))
        val first = p.execute(scheduleRequest("a"))
        withTimeout(5_000) { entered.await() }

        remoteBlocker = BackendBlock(uiTextOf(R.string.runlog_backend_unavailable, "Shizuku"))
        p.execute(scheduleRequest("b", force = true)).join()
        assertEquals(listOf(ExecutionResult.FAILED_START), recorded.toList())

        // 第一个没被抢占，照常跑完
        remoteBlocker = null
        release.complete(Unit)
        first.join()
        assertEquals(listOf(ExecutionResult.FAILED_START, ExecutionResult.STARTED), recorded.toList())
    }

    // Core 启动阶段已发过不带策略名的「任务出错」，只留带策略名的这条
    @Test
    fun coreStartFailure_replacesStartFailureNotification() = runBlocking<Unit> {
        val reason = uiTextOf(R.string.task_start_error_start_failed)
        coEvery {
            startTaskChain.invoke(chain = any(), context = any(), scheduleLabel = any())
        } returns StartTaskChainUseCase.Result.Failed(
            executionResult = ExecutionResult.FAILED_START,
            message = reason,
            startFailureNotified = true,
        )

        pipeline().execute(scheduleRequest()).join()

        verify(exactly = 1) {
            notificationCenter.notifyLaunchNotStarted(
                "Test", ExecutionResult.FAILED_START, reason, replacesStartFailure = true,
            )
        }
    }

    @Test
    fun remoteAvailable_launchesUiAndStarts() = runBlocking<Unit> {
        pipeline().execute(scheduleRequest()).join()

        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        assertEquals(1, uiLaunches.get())
        assertEquals(1, startCalls.get())
        verify(exactly = 0) { notificationCenter.notifyLaunchNotStarted(any(), any(), any()) }
    }

    @Test
    fun switchProfile_logsProfileName() = runBlocking<Unit> {
        profileId.value = "profile-0"
        every { chainState.profiles } returns MutableStateFlow(
            listOf(TaskProfile(id = "profile-1", name = "Daily", chain = emptyList())),
        )
        pipeline().execute(scheduleRequest()).join()
        verify { logSession.append(uiTextOf(R.string.schedule_log_switch_profile, "Daily")) }
    }

    /** 目标配置已删时退回 ID */
    @Test
    fun switchProfile_missingProfile_logsProfileId() = runBlocking<Unit> {
        profileId.value = "profile-0"
        pipeline().execute(scheduleRequest()).join()
        verify { logSession.append(uiTextOf(R.string.schedule_log_switch_profile, "profile-1")) }
    }

    @Test
    fun closeGame_naturalEnd_stopsVirtualDisplay() = runBlocking<Unit> {
        pipeline().execute(scheduleRequest(closeGame = true)).join()
        driveTaskToEnd()
        coVerify(timeout = 5_000, exactly = 1) { composition.stopVirtualDisplay() }
    }

    /** 掉线等回调侧中止视同结束，须关游戏 */
    @Test
    fun closeGame_callbackAbort_stopsVirtualDisplay() = runBlocking<Unit> {
        pipeline().execute(scheduleRequest(closeGame = true)).join()
        driveTaskToStop(MaaCompositionService.StopOrigin.CALLBACK)
        coVerify(timeout = 5_000, exactly = 1) { composition.stopVirtualDisplay() }
    }

    /** 用户手动停止保留游戏 */
    @Test
    fun closeGame_manualStop_keepsGame() = runBlocking<Unit> {
        pipeline().execute(scheduleRequest(closeGame = true)).join()
        driveTaskToStop(MaaCompositionService.StopOrigin.USER)
        delay(500)
        coVerify(exactly = 0) { composition.stopVirtualDisplay() }
    }

    /** 全局开关开启时由后台任务页负责，这里不重复关 */
    @Test
    fun closeGame_globalSettingOn_leftToTaskEndRegistry() = runBlocking<Unit> {
        closeAppOnTaskEnd.value = true
        pipeline().execute(scheduleRequest(closeGame = true)).join()
        driveTaskToEnd()
        delay(500)
        coVerify(exactly = 0) { composition.stopVirtualDisplay() }
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

    @Test
    fun hardwareScreenOff_waitsForCountdown_thenRestoresOnTaskEnd() = runBlocking<Unit> {
        screenInteractive.set(false)
        useHardwareScreenOff.value = true
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val p = pipeline(gatedCountdown(entered, release))
        coEvery {
            startTaskChain.invoke(chain = any(), context = any(), scheduleLabel = any())
        } coAnswers {
            assertEquals(listOf(false), displayPowerCommands.toList())
            startCalls.incrementAndGet()
            compositionState.value = MaaExecutionState.RUNNING
            StartTaskChainUseCase.Result.Success
        }
        val job = p.execute(scheduleRequest(autoScreenSaver = true))
        withTimeout(5_000) { entered.await() }
        assertEquals(1, uiLaunches.get())
        assertTrue(displayPowerCommands.isEmpty())
        release.complete(Unit)
        job.join()
        assertEquals(1, startCalls.get())
        coVerify(exactly = 0) { screenSaver.show() }
        driveTaskToEnd()
        withTimeout(5_000) { while (displayPowerCommands.size < 2) delay(10) }
        assertEquals(listOf(false, true), displayPowerCommands.toList())
    }

    @Test
    fun hardwareScreenOff_cancelDuringCountdown_neverSendsCommand() = runBlocking<Unit> {
        screenInteractive.set(false)
        useHardwareScreenOff.value = true
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val p = pipeline(gatedCountdown(entered, release))
        val job = p.execute(scheduleRequest(autoScreenSaver = true))
        withTimeout(5_000) { entered.await() }
        p.submit(LaunchUserEvent.Cancel)
        release.complete(Unit)
        job.join()
        assertEquals(listOf(ExecutionResult.CANCELLED), recorded.toList())
        assertTrue(displayPowerCommands.isEmpty())
        assertEquals(0, startCalls.get())
    }

    @Test
    fun hardwareScreenOff_startNow_closesDisplayAfterCountdown() = runBlocking<Unit> {
        screenInteractive.set(false)
        useHardwareScreenOff.value = true
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val p = pipeline(gatedCountdown(entered, release))
        val job = p.execute(scheduleRequest(autoScreenSaver = true))
        withTimeout(5_000) { entered.await() }
        p.submit(LaunchUserEvent.StartNow)
        release.complete(Unit)
        job.join()
        assertEquals(listOf(false), displayPowerCommands.toList())
        assertEquals(1, startCalls.get())
    }

    @Test
    fun hardwareScreenOff_strategyDisabled_doesNotOverrideStrategy() = runBlocking<Unit> {
        screenInteractive.set(false)
        useHardwareScreenOff.value = true
        pipeline().execute(scheduleRequest()).join()
        assertTrue(displayPowerCommands.isEmpty())
        coVerify(exactly = 0) { screenSaver.show() }
    }

    @Test
    fun hardwareScreenOff_inUseOrForeground_neverClosesDisplay() = runBlocking<Unit> {
        useHardwareScreenOff.value = true
        val p = pipeline()
        p.execute(scheduleRequest("in-use", autoScreenSaver = true)).join()
        compositionState.value = MaaExecutionState.IDLE
        screenInteractive.set(false)
        runMode.value = RunMode.FOREGROUND
        p.execute(scheduleRequest("foreground", autoScreenSaver = true)).join()
        assertEquals(2, startCalls.get())
        assertTrue(displayPowerCommands.isEmpty())
        coVerify(exactly = 0) { screenSaver.show() }
    }

    @Test
    fun hardwareScreenOff_startFails_restoresDisplay() = runBlocking<Unit> {
        screenInteractive.set(false)
        useHardwareScreenOff.value = true
        coEvery {
            startTaskChain.invoke(chain = any(), context = any(), scheduleLabel = any())
        } returns StartTaskChainUseCase.Result.Failed(
            executionResult = ExecutionResult.FAILED_START,
            message = mockk(relaxed = true),
        )
        pipeline().execute(scheduleRequest(autoScreenSaver = true)).join()
        assertEquals(listOf(ExecutionResult.FAILED_START), recorded.toList())
        assertEquals(listOf(false, true), displayPowerCommands.toList())
    }

    @Test
    fun hardwareScreenOff_sideTaskOnly_restoresWithoutCoreEndEvent() = runBlocking<Unit> {
        screenInteractive.set(false)
        useHardwareScreenOff.value = true
        coEvery {
            startTaskChain.invoke(chain = any(), context = any(), scheduleLabel = any())
        } returns StartTaskChainUseCase.Result.SuccessWithoutCore(null)
        pipeline().execute(scheduleRequest(autoScreenSaver = true)).join()
        withTimeout(5_000) { while (displayPowerCommands.size < 2) delay(10) }
        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        assertEquals(listOf(false, true), displayPowerCommands.toList())
    }

    @Test
    fun hardwareScreenOff_jobCancelledDuringStart_restoresDisplay() = runBlocking<Unit> {
        screenInteractive.set(false)
        useHardwareScreenOff.value = true
        val entered = CompletableDeferred<Unit>()
        coEvery {
            startTaskChain.invoke(chain = any(), context = any(), scheduleLabel = any())
        } coAnswers {
            entered.complete(Unit)
            delay(60_000)
            StartTaskChainUseCase.Result.Success
        }
        val job = pipeline().execute(scheduleRequest(autoScreenSaver = true))
        withTimeout(5_000) { entered.await() }
        job.cancel()
        job.join()
        assertEquals(listOf(false, true), displayPowerCommands.toList())
    }

    @Test
    fun hardwareScreenOff_manualStop_restoresDisplay() = runBlocking<Unit> {
        screenInteractive.set(false)
        useHardwareScreenOff.value = true
        pipeline().execute(scheduleRequest(autoScreenSaver = true)).join()
        composition.stop()
        withTimeout(5_000) { while (displayPowerCommands.size < 2) delay(10) }
        assertEquals(listOf(false, true), displayPowerCommands.toList())
    }

    @Test
    fun hardwareScreenOff_withAutoSleep_releasesPowerBeforeSystemSleep() = runBlocking<Unit> {
        screenInteractive.set(false)
        useHardwareScreenOff.value = true
        val commandsAtSleep = CompletableDeferred<List<Boolean>>()
        coEvery { wake.lockAndSleep() } coAnswers {
            commandsAtSleep.complete(displayPowerCommands.toList())
            WakeUnlockEngine.WakeResult.OK
        }
        pipeline().execute(scheduleRequest(autoScreenSaver = true, autoSleep = true)).join()
        driveTaskToEnd()
        assertEquals(listOf(false, true), withTimeout(5_000) { commandsAtSleep.await() })
        coVerify(exactly = 1) { wake.lockAndSleep() }
    }

    @Test
    fun hardwareScreenOff_forceStart_releasesOldCommandBeforeNewOne() = runBlocking<Unit> {
        screenInteractive.set(false)
        useHardwareScreenOff.value = true
        val p = pipeline()
        p.execute(scheduleRequest("a", autoSleep = true, autoScreenSaver = true)).join()
        p.execute(scheduleRequest("b", force = true, autoScreenSaver = true)).join()
        assertEquals(listOf(false, true, false), displayPowerCommands.toList())
        coVerify(exactly = 0) { wake.lockAndSleep() }
        driveTaskToEnd()
        withTimeout(5_000) { while (displayPowerCommands.size < 4) delay(10) }
        assertEquals(listOf(false, true, false, true), displayPowerCommands.toList())
    }

    @Test
    fun hardwareScreenOff_existingUserScreenSaver_isKept() = runBlocking<Unit> {
        givenWakeGate(interactive = false, saverShowing = true)
        useHardwareScreenOff.value = true
        pipeline().execute(scheduleRequest(autoScreenSaver = true)).join()
        driveTaskToEnd()
        delay(100)
        assertTrue(displayPowerCommands.isEmpty())
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

    // --- 三种常见用法 ---

    @Test
    fun usageHangSaver_multiRound_doesNotHideOrUnlock() = runBlocking<Unit> {
        // 手动开熄屏挂机后连跑：不解系统锁、不收屏保
        givenWakeGate(keyguard = true, locked = true, saverShowing = true, type = "swipe")
        val p = pipeline()
        p.execute(scheduleRequest("r1")).join()
        compositionState.value = MaaExecutionState.IDLE
        p.execute(scheduleRequest("r2")).join()
        assertEquals(
            listOf(ExecutionResult.STARTED, ExecutionResult.STARTED),
            recorded.toList(),
        )
        io.mockk.coVerify(exactly = 0) { wake.unlock(any()) }
        io.mockk.coVerify(exactly = 0) { screenSaver.hide() }
    }

    @Test
    fun usageHangSaver_strategyAutoSaver_keepsUserSaver() = runBlocking<Unit> {
        givenWakeGate(interactive = false, keyguard = true, locked = true, saverShowing = true)
        pipeline().execute(scheduleRequest(autoScreenSaver = true)).join()
        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        io.mockk.coVerify(exactly = 0) { screenSaver.show() }
        driveTaskToEnd()
        delay(500)
        io.mockk.coVerify(exactly = 0) { screenSaver.hide() }
    }

    @Test
    fun usageHangSaver_skipsAutoSleepSoLaterRoundsKeepHang() = runBlocking<Unit> {
        givenWakeGate(keyguard = true, locked = true, saverShowing = true)
        pipeline().execute(scheduleRequest(autoSleep = true)).join()
        driveTaskToEnd()
        delay(500)
        io.mockk.coVerify(exactly = 0) { wake.lockAndSleep() }
        io.mockk.coVerify(exactly = 0) { screenSaver.hide() }
    }

    @Test
    fun usageSwipeLock_unlocksEmptyAndStarts() = runBlocking<Unit> {
        givenWakeGate(keyguard = true, locked = false, type = "swipe")
        pipeline().execute(scheduleRequest()).join()
        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        io.mockk.coVerify { wake.unlock(UnlockCredential.Swipe) }
    }

    @Test
    fun usageSwipeLock_screenOff_unlocksAndStarts() = runBlocking<Unit> {
        givenWakeGate(interactive = false, locked = false, type = "swipe")
        pipeline().execute(scheduleRequest()).join()
        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        io.mockk.coVerify { wake.unlock(UnlockCredential.Swipe) }
    }

    @Test
    fun usagePinLock_injectsPinAndStarts() = runBlocking<Unit> {
        givenWakeGate(keyguard = true, locked = true, type = "pin", pin = "2580")
        pipeline().execute(scheduleRequest()).join()
        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        io.mockk.coVerify { wake.unlock(UnlockCredential.Pin("2580")) }
    }

    @Test
    fun usageGestureLock_replaysRecordedGestureAndStarts() = runBlocking<Unit> {
        givenWakeGate(keyguard = true, locked = true, type = "gesture", gestureJson = GESTURE_JSON)
        pipeline().execute(scheduleRequest()).join()
        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        io.mockk.coVerify { wake.unlock(UnlockCredential.Gesture(GESTURE_JSON)) }
    }

    @Test
    fun usageGestureLock_withoutRecording_fallsBackToPlainUnlock() = runBlocking<Unit> {
        // 没录成也得把屏点亮，否则无锁屏的机器反而跑不起来
        givenWakeGate(keyguard = true, locked = false, type = "gesture", gestureJson = "")
        pipeline().execute(scheduleRequest()).join()
        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        io.mockk.coVerify { wake.unlock(UnlockCredential.Swipe) }
    }

    @Test
    fun usageGestureLock_replayRejected_skipsAsLocked() = runBlocking<Unit> {
        givenWakeGate(keyguard = true, locked = true, type = "gesture", gestureJson = GESTURE_JSON)
        coEvery { wake.unlock(any()) } returns WakeUnlockEngine.WakeResult.CREDENTIAL_REJECTED
        pipeline().execute(scheduleRequest()).join()
        assertEquals(listOf(ExecutionResult.SKIPPED_LOCKED), recorded.toList())
        assertEquals(0, startCalls.get())
    }

    @Test
    fun schedulePasswordLock_swipeSetting_triesUnlockThenSkips() = runBlocking<Unit> {
        givenWakeGate(keyguard = true, locked = true, type = "swipe")
        coEvery { wake.unlock(any()) } returns WakeUnlockEngine.WakeResult.CREDENTIAL_REQUIRED
        pipeline().execute(scheduleRequest()).join()
        assertEquals(listOf(ExecutionResult.SKIPPED_LOCKED), recorded.toList())
        io.mockk.coVerify { wake.unlock(UnlockCredential.Swipe) }
    }

    @Test
    fun scheduleDeviceLocked_pinTypeButBlank_triesUnlockThenSkips() = runBlocking<Unit> {
        givenWakeGate(keyguard = true, locked = true, type = "pin", pin = "")
        coEvery { wake.unlock(any()) } returns WakeUnlockEngine.WakeResult.CREDENTIAL_REQUIRED
        pipeline().execute(scheduleRequest()).join()
        assertEquals(listOf(ExecutionResult.SKIPPED_LOCKED), recorded.toList())
        // 空 PIN 照样试一把（注入空串），但不算备好凭证
        io.mockk.coVerify { wake.unlock(UnlockCredential.Pin("")) }
    }

    @Test
    fun scheduleScreenOff_passwordLockWithoutPin_triesUnlockThenSkips() = runBlocking<Unit> {
        givenWakeGate(interactive = false, keyguard = true, locked = true, type = "swipe")
        coEvery { wake.unlock(any()) } returns WakeUnlockEngine.WakeResult.CREDENTIAL_REQUIRED
        pipeline().execute(scheduleRequest()).join()
        assertEquals(listOf(ExecutionResult.SKIPPED_LOCKED), recorded.toList())
        io.mockk.coVerify { wake.unlock(UnlockCredential.Swipe) }
    }

    @Test
    fun scheduleForeground_passwordLockWithoutPin_triesUnlockThenSkips() = runBlocking<Unit> {
        runMode.value = RunMode.FOREGROUND
        givenWakeGate(keyguard = true, locked = true, type = "swipe")
        coEvery { wake.unlock(any()) } returns WakeUnlockEngine.WakeResult.CREDENTIAL_REQUIRED
        pipeline().execute(scheduleRequest()).join()
        assertEquals(listOf(ExecutionResult.SKIPPED_LOCKED), recorded.toList())
        assertEquals(0, startCalls.get())
    }

    @Test
    fun scheduleWakeFailed_stillLocked_skips() = runBlocking<Unit> {
        givenWakeGate(keyguard = true, locked = false, type = "swipe")
        coEvery { wake.unlock(any()) } returns WakeUnlockEngine.WakeResult.CREDENTIAL_REQUIRED
        pipeline().execute(scheduleRequest()).join()
        assertEquals(listOf(ExecutionResult.SKIPPED_LOCKED), recorded.toList())
        assertEquals(0, startCalls.get())
    }

    @Test
    fun scheduleWakeFailed_keyguardGone_starts() = runBlocking<Unit> {
        givenWakeGate(keyguard = false, locked = false, type = "swipe")
        coEvery { wake.unlock(any()) } returns WakeUnlockEngine.WakeResult.WAKE_FAILED
        pipeline().execute(scheduleRequest()).join()
        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        assertEquals(1, startCalls.get())
    }

    @Test
    fun scheduleWakeFailed_stillLocked_skipsAsLocked() = runBlocking<Unit> {
        givenWakeGate(interactive = false, keyguard = true, locked = true, type = "pin", pin = "1234")
        coEvery { wake.unlock(any()) } returns WakeUnlockEngine.WakeResult.WAKE_FAILED
        pipeline().execute(scheduleRequest()).join()
        assertEquals(listOf(ExecutionResult.SKIPPED_LOCKED), recorded.toList())
        assertEquals(0, startCalls.get())
        io.mockk.coVerify { wake.unlock(UnlockCredential.Pin("1234")) }
    }

    @Test
    fun scheduleAutoScreenSaverNotShowing_passwordLock_skipsBeforeShow() = runBlocking<Unit> {
        givenWakeGate(keyguard = true, locked = true, type = "swipe", saverShowing = false)
        coEvery { wake.unlock(any()) } returns WakeUnlockEngine.WakeResult.CREDENTIAL_REQUIRED
        pipeline().execute(scheduleRequest(autoScreenSaver = true)).join()
        assertEquals(listOf(ExecutionResult.SKIPPED_LOCKED), recorded.toList())
        io.mockk.coVerify(exactly = 0) { screenSaver.show() }
    }

    @Test
    fun scheduleUnlocked_swipe_startsAndUnlocks() = runBlocking<Unit> {
        givenWakeGate(type = "swipe")
        pipeline().execute(scheduleRequest()).join()
        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        io.mockk.coVerify { wake.unlock(UnlockCredential.Swipe) }
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

    /** 前台无倒计时，直接启动 */
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

    /** 后台无论 Schedule/External 都有 Dialog 倒计时 */
    @Test
    fun background_alwaysPresentUiCountdown() = runBlocking<Unit> {
        runMode.value = RunMode.BACKGROUND
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val p = pipeline(countdown = gatedCountdown(entered, release))
        val job = p.execute(scheduleRequest("bg-1"))
        withTimeout(5_000) { entered.await() }
        val session = p.session.value
        assertTrue(session is LaunchSession.InFlight && session.presentation == LaunchPresentation.DIALOG)
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

    @Test
    fun blacklistHit_inUse_skipsBeforeUnlockAndUi() = runBlocking<Unit> {
        blacklist.value = setOf("com.game")
        foregroundPkg = "com.game"

        pipeline().execute(scheduleRequest()).join()

        val reason = uiTextOf(R.string.schedule_log_blacklist_hit, "label:com.game")
        assertEquals(listOf(ExecutionResult.SKIPPED_BLACKLIST), recorded.toList())
        verify { logSession.end(ExecutionResult.SKIPPED_BLACKLIST, reason) }
        verify(exactly = 1) {
            notificationCenter.notifyLaunchNotStarted("Test", ExecutionResult.SKIPPED_BLACKLIST, reason, any())
        }
        coVerify(exactly = 0) { wake.unlock(any()) }
        assertEquals(0, uiLaunches.get())
        assertEquals(0, startCalls.get())
    }

    @Test
    fun blacklistMiss_inUse_starts() = runBlocking<Unit> {
        blacklist.value = setOf("com.game")
        foregroundPkg = "com.other"

        pipeline().execute(scheduleRequest()).join()

        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        assertEquals(1, foregroundProbes.get())
        assertEquals(1, uiLaunches.get())
    }

    /** 名单命中前台应用，但这些场景不该去探测 */
    private suspend fun assertStartsWithoutProbe(request: LaunchRequest) {
        blacklist.value = setOf("com.game")
        foregroundPkg = "com.game"
        pipeline().execute(request).join()
        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        assertEquals(0, foregroundProbes.get())
    }

    // 待机时顶层是熄屏前留下的应用，不代表在用
    @Test
    fun blacklist_idleDevice_notChecked() = runBlocking<Unit> {
        givenWakeGate(interactive = false)
        assertStartsWithoutProbe(scheduleRequest())
    }

    @Test
    fun blacklist_lockedDevice_notChecked() = runBlocking<Unit> {
        givenWakeGate(keyguard = true)
        assertStartsWithoutProbe(scheduleRequest())
    }

    @Test
    fun blacklist_foregroundMode_notChecked() = runBlocking<Unit> {
        runMode.value = RunMode.FOREGROUND
        assertStartsWithoutProbe(scheduleRequest())
    }

    @Test
    fun blacklist_externalLaunch_notChecked() = runBlocking<Unit> {
        assertStartsWithoutProbe(externalRequest())
    }

    @Test
    fun blacklistEmpty_skipsProbe() = runBlocking<Unit> {
        foregroundPkg = "com.game"

        pipeline().execute(scheduleRequest()).join()

        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        assertEquals(0, foregroundProbes.get())
    }

    @Test
    fun blacklist_foregroundUnknown_startsAndLogs() = runBlocking<Unit> {
        blacklist.value = setOf("com.game")
        foregroundPkg = null

        pipeline().execute(scheduleRequest()).join()

        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        verify { logSession.append(uiTextOf(R.string.schedule_log_blacklist_unknown)) }
    }

    // 探测超时也是 CancellationException，不能被当成被抢占
    @Test
    fun blacklist_probeTimeout_treatedAsUnknown() = runBlocking<Unit> {
        blacklist.value = setOf("com.game")
        probeTimesOut = true

        pipeline().execute(scheduleRequest()).join()

        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        verify { logSession.append(uiTextOf(R.string.schedule_log_blacklist_unknown)) }
    }

    // 命中就别把在跑的任务停了
    @Test
    fun blacklistHit_forceStart_keepsRunningTask() = runBlocking<Unit> {
        compositionState.value = MaaExecutionState.RUNNING
        blacklist.value = setOf("com.game")
        foregroundPkg = "com.game"

        pipeline().execute(scheduleRequest(force = true)).join()

        assertEquals(listOf(ExecutionResult.SKIPPED_BLACKLIST), recorded.toList())
        assertEquals(0, stopCalls.get())
        coVerify(exactly = 0) { composition.stopVirtualDisplay() }
    }

    // 也别抢占在途的启动
    @Test
    fun blacklistHit_forcePreempt_keepsInFlightLaunch() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val p = pipeline(countdown = gatedCountdown(entered, release))
        val first = p.execute(scheduleRequest("a"))
        withTimeout(5_000) { entered.await() }

        blacklist.value = setOf("com.game")
        foregroundPkg = "com.game"
        p.execute(scheduleRequest("b", force = true)).join()
        assertEquals(listOf(ExecutionResult.SKIPPED_BLACKLIST), recorded.toList())
        assertEquals(0, stopCalls.get())

        release.complete(Unit)
        first.join()
        assertEquals(listOf(ExecutionResult.SKIPPED_BLACKLIST, ExecutionResult.STARTED), recorded.toList())
    }

    @Test
    fun silent_inUse_noUiCountdownViaNotification() = runBlocking<Unit> {
        pipeline().execute(scheduleRequest(silent = true)).join()

        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        assertEquals(0, uiLaunches.get())
        assertEquals(LaunchPresentation.NOTIFICATION, lastPresentation)
        verify { logSession.append(uiTextOf(R.string.schedule_log_silent_start)) }
    }

    // 待机时要靠拉起的界面保持亮屏
    @Test
    fun silent_idleDevice_stillLaunchesUi() = runBlocking<Unit> {
        givenWakeGate(interactive = false)

        pipeline().execute(scheduleRequest(silent = true)).join()

        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
        assertEquals(1, uiLaunches.get())
        assertEquals(LaunchPresentation.DIALOG, lastPresentation)
    }

    @Test
    fun silentOff_inUse_launchesUi() = runBlocking<Unit> {
        pipeline().execute(scheduleRequest()).join()

        assertEquals(1, uiLaunches.get())
        assertEquals(LaunchPresentation.DIALOG, lastPresentation)
    }

    // 通知里的旧按钮不能误伤别的请求
    @Test
    fun notificationCancel_staleRequestIgnored() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val p = pipeline(countdown = gatedCountdown(entered, release))
        val job = p.execute(scheduleRequest(silent = true))
        withTimeout(5_000) { entered.await() }
        p.submit(LaunchUserEvent.Cancel, "stale")
        release.complete(Unit)
        job.join()
        assertEquals(listOf(ExecutionResult.STARTED), recorded.toList())
    }

    @Test
    fun notificationCancel_matchingRequestCancels() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        val p = pipeline(countdown = object : CountdownUI {
            override suspend fun await(
                request: LaunchRequest,
                onTick: (remainingSeconds: Int) -> Unit,
                shouldAbort: () -> Boolean,
            ): Boolean {
                entered.complete(Unit)
                withTimeout(5_000) {
                    while (!shouldAbort()) delay(10)
                }
                return false
            }
        })
        val job = p.execute(scheduleRequest("req-silent", silent = true))
        withTimeout(5_000) { entered.await() }
        p.submit(LaunchUserEvent.Cancel, "req-silent")
        job.join()
        assertEquals(listOf(ExecutionResult.CANCELLED), recorded.toList())
        assertEquals(0, startCalls.get())
    }

    private companion object {
        const val GESTURE_JSON = """{"screenWidth":1080,"screenHeight":2220,"rotation":0,"steps":[]}"""
    }
}
