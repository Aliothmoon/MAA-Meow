package com.aliothmoon.maameow.domain.launch

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.models.UnlockCredential
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.ScreenSaverController
import com.aliothmoon.maameow.domain.service.TaskEndRegistry
import com.aliothmoon.maameow.domain.service.UnlockGestureReader
import com.aliothmoon.maameow.domain.service.WakeUnlockEngine
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.domain.usecase.TaskStartContext
import com.aliothmoon.maameow.domain.usecase.TaskStartMode
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.service.ScheduleTriggerLogger
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 统一自动化启动管线（定时 + 外部 Intent）
 * Job 挂在 [scope]（进程级），Service 可 join 返回的 Job
 */
class LaunchPipeline(
    private val scope: CoroutineScope,
    private val mutex: LaunchMutex,
    private val appSettingsManager: AppSettingsManager,
    private val wakeUnlockEngine: WakeUnlockEngine,
    private val unlockGestures: UnlockGestureReader,
    private val chainState: TaskChainState,
    private val compositionService: MaaCompositionService,
    private val triggerLogger: ScheduleTriggerLogger,
    private val scheduleRepository: ScheduleStrategyRepository,
    private val startTaskChain: StartTaskChainUseCase,
    private val countdownUI: CountdownUI,
    private val screenSaver: ScreenSaverController,
    private val taskEndRegistry: TaskEndRegistry,
    private val keyguardLocked: () -> Boolean,
    /** 此刻要密码才能进桌面；不是 isDeviceSecure（只说明设过密码） */
    private val deviceLocked: () -> Boolean,
    private val screenInteractive: () -> Boolean,
    private val activityLauncher: suspend (LaunchRequest) -> Boolean,
) {
    private val _session = MutableStateFlow<LaunchSession>(LaunchSession.Idle)
    val session: StateFlow<LaunchSession> = _session.asStateFlow()

    private val _effects = Channel<LaunchEffect>(capacity = Channel.BUFFERED)
    val effects: Flow<LaunchEffect> = _effects.receiveAsFlow()

    private val executeLock = Any()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val activeRequestId = AtomicReference<String?>(null)
    private val lastCompletedRequestId = AtomicReference<String?>(null)
    private val cancelRequested = AtomicBoolean(false)
    private val startNowRequested = AtomicBoolean(false)

    fun execute(request: LaunchRequest): Job {
        synchronized(executeLock) {
            // 同 requestId 幂等（check + launch 同一把锁，缩小竞窗）
            val inflight = _session.value

            if (inflight is LaunchSession.InFlight && inflight.request.requestId == request.requestId) {
                Timber.i("LaunchPipeline: idempotent skip in-flight %s", request.requestId)
                return jobs[request.requestId] ?: scope.launch { }
            }
            if (lastCompletedRequestId.get() == request.requestId) {
                Timber.i("LaunchPipeline: idempotent skip completed %s", request.requestId)
                return scope.launch { }
            }
            val existing = jobs[request.requestId]
            if (existing != null && existing.isActive) {
                Timber.i("LaunchPipeline: idempotent skip active job %s", request.requestId)
                return existing
            }

            val job = scope.launch {
                runPipeline(request)
            }
            jobs[request.requestId] = job
            job.invokeOnCompletion { jobs.remove(request.requestId, job) }
            return job
        }
    }

    fun submit(event: LaunchUserEvent) {
        when (event) {
            LaunchUserEvent.Cancel -> cancelRequested.set(true)
            LaunchUserEvent.StartNow -> startNowRequested.set(true)
        }
    }

    private suspend fun runPipeline(request: LaunchRequest) {
        cancelRequested.set(false)
        startNowRequested.set(false)

        if (!mutex.tryAcquire(request.requestId)) {
            if (request.forceStart) {
                preemptInFlight(request)
                mutex.forceAcquire(request.requestId)
            } else {
                finishWithoutHold(
                    request,
                    ExecutionResult.SKIPPED_BUSY,
                    uiTextOf(R.string.schedule_log_skipped_busy),
                )
                return
            }
        }

        activeRequestId.set(request.requestId)
        var terminalResult: ExecutionResult? = null
        var terminalMessage: UiText? = null
        var presentUi = true
        // finally 要用，声明在 try 外
        val outcome = RunOutcome()
        val log = triggerLogger.open(
            strategyId = request.strategyId,
            strategyName = request.displayName,
            scheduledTimeMs = request.scheduledTimeMs,
            runMode = appSettingsManager.runMode.value.name,
        )

        try {
            setPhase(request, LaunchSession.Phase.DevicePrep, presentUi = true)
            log.append(uiTextOf(R.string.schedule_log_received, request.displayName))

            val state = compositionService.state.value
            if (state == MaaExecutionState.RUNNING
                || state == MaaExecutionState.STARTING
                || state == MaaExecutionState.STOPPING
            ) {
                if (request.forceStart) {
                    log.append(uiTextOf(R.string.schedule_log_force_stop_running))
                    takeOverFromPreviousRun()
                    compositionService.stop()
                    compositionService.stopVirtualDisplay()
                } else {
                    terminalResult = ExecutionResult.SKIPPED_BUSY
                    terminalMessage = uiTextOf(R.string.schedule_log_task_running_busy)
                    return
                }
            }

            // 须在唤醒前采样；无锁屏时熄屏也不上锁，亮屏与 keyguard 都看
            outcome.tookOverIdleDevice = !screenInteractive() || keyguardLocked()

            if (request.source == LaunchSource.Schedule) {
                val unlockType = appSettingsManager.wakeUnlockType.value
                val credential = UnlockCredential.of(
                    type = unlockType,
                    pin = appSettingsManager.wakeCredential.value,
                    gestureJson = if (unlockType == UnlockCredential.TYPE_GESTURE) {
                        unlockGestures.readJson()
                    } else {
                        ""
                    },
                )
                // 1. 快捷选项熄屏挂机已盖上：KEEP_SCREEN_ON，多轮定时不解、不收
                // 2. 其余交给凭证自己决定注入 PIN、回放手势还是只试 dismissKeyguard，
                //    别拿 isDeviceLocked 预跳过
                val saverKeepScreenOn = screenSaver.isShowing()
                if (saverKeepScreenOn) {
                    log.append(uiTextOf(R.string.schedule_log_wake_skipped_screensaver))
                } else {
                    log.append(uiTextOf(R.string.schedule_log_wake_start))
                    val wake = wakeUnlockEngine.unlock(credential)
                    if (wake.isSuccess) {
                        log.append(uiTextOf(R.string.schedule_log_wake_ok))
                    } else {
                        log.append(uiTextOf(R.string.schedule_log_wake_failed, wake.message))
                        if (keyguardLocked()) {
                            terminalResult = ExecutionResult.SKIPPED_LOCKED
                            terminalMessage = if (deviceLocked() && !credential.isReady) {
                                uiTextOf(R.string.notification_schedule_pin_required)
                            } else {
                                uiTextOf(R.string.notification_schedule_device_locked)
                            }
                            return
                        }
                    }
                }
            }

            log.append(uiTextOf(R.string.schedule_log_wait_profile))
            chainState.isLoaded.first { it }
            if (chainState.profileId.value != request.profileId) {
                log.append(uiTextOf(R.string.schedule_log_switch_profile, request.profileId))
                chainState.switchProfile(request.profileId)
            }
            if (chainState.profileId.value != request.profileId) {
                terminalResult = ExecutionResult.FAILED_VALIDATION
                terminalMessage = uiTextOf(R.string.schedule_log_profile_missing)
                return
            }
            val enabled = chainState.chain.value.filter { it.enabled }
            if (enabled.isEmpty()) {
                terminalResult = ExecutionResult.FAILED_VALIDATION
                terminalMessage = uiTextOf(R.string.schedule_log_empty_chain)
                return
            }

            // 前台无倒计时；后台 Dialog 倒计时，控制层需用户曾手动启动
            val isForeground = appSettingsManager.runMode.value == RunMode.FOREGROUND
            presentUi = !isForeground
            outcome.backgroundRun = !isForeground
            val needsActivityLaunch = request.source == LaunchSource.Schedule && presentUi

            // 后台 + 待机才盖；用户已开的熄屏挂机不收走，好连跑多轮
            if (request.autoScreenSaver && outcome.backgroundRun && outcome.tookOverIdleDevice) {
                if (screenSaver.isShowing()) {
                    log.append(uiTextOf(R.string.schedule_log_screen_saver_kept))
                } else {
                    outcome.screenSaverEngaged = screenSaver.show()
                    log.append(
                        if (outcome.screenSaverEngaged) {
                            uiTextOf(R.string.schedule_log_screen_saver_on)
                        } else {
                            uiTextOf(R.string.schedule_log_screen_saver_failed)
                        },
                    )
                }
            }

            if (needsActivityLaunch) {
                log.append(uiTextOf(R.string.schedule_log_launch_ui))
                val launched = activityLauncher(request)
                if (!launched) {
                    terminalResult = ExecutionResult.FAILED_UI_LAUNCH
                    terminalMessage = uiTextOf(R.string.schedule_log_ui_launch_failed)
                    return
                }
            }

            if (!isForeground) {
                log.append(
                    uiTextOf(R.string.schedule_log_countdown_start, request.countdownSeconds),
                )
                val startNow = countdownUI.await(
                    request = request,
                    onTick = { remaining ->
                        setPhase(request, LaunchSession.Phase.Counting(remaining), presentUi)
                    },
                    shouldAbort = {
                        cancelRequested.get() || startNowRequested.get()
                                || activeRequestId.get() != request.requestId
                    },
                )

                if (cancelRequested.get() && !startNowRequested.get()) {
                    terminalResult = ExecutionResult.CANCELLED
                    terminalMessage = uiTextOf(R.string.schedule_log_user_cancelled)
                    return
                }
                if (startNow || startNowRequested.get()) {
                    log.append(uiTextOf(R.string.schedule_log_start_now))
                } else {
                    log.append(uiTextOf(R.string.schedule_log_countdown_done))
                }
            }

            setPhase(request, LaunchSession.Phase.Preparing, presentUi)
            setPhase(request, LaunchSession.Phase.Starting, presentUi)
            log.append(uiTextOf(R.string.schedule_log_starting_tasks, enabled.size))

            when (
                val result = startTaskChain(
                    chain = enabled,
                    context = TaskStartContext(mode = TaskStartMode.SCHEDULED),
                    scheduleLabel = request.displayName,
                )
            ) {
                StartTaskChainUseCase.Result.Success -> {
                    terminalResult = ExecutionResult.STARTED
                    terminalMessage = null
                    log.append(uiTextOf(R.string.schedule_log_start_success))
                }

                is StartTaskChainUseCase.Result.Failed -> {
                    terminalResult = result.executionResult
                    terminalMessage = result.message
                    log.append(uiTextOf(R.string.schedule_log_start_failed, result.message))
                }
            }
        } catch (e: CancellationException) {
            terminalResult = ExecutionResult.CANCELLED
            terminalMessage = uiTextOf(R.string.schedule_log_cancelled_preempt)
            throw e
        } catch (e: Exception) {
            Timber.e(e, "LaunchPipeline failed")
            terminalResult = ExecutionResult.FAILED_START
            terminalMessage = uiTextOf(
                R.string.schedule_log_exception,
                e.message ?: e.javaClass.simpleName,
            )
        } finally {
            withContext(NonCancellable) {
                finalizePipeline(request, log, terminalResult, terminalMessage, outcome)
            }
        }
    }

    private suspend fun finalizePipeline(
        request: LaunchRequest,
        log: ScheduleTriggerLogger.Session,
        terminalResult: ExecutionResult?,
        terminalMessage: UiText?,
        outcome: RunOutcome,
    ) {
        val result = terminalResult ?: ExecutionResult.CANCELLED
        val skipSleep = request.skipAutoSleepIfAwake && !outcome.tookOverIdleDevice
        // 用户熄屏挂机还在就别 lockAndSleep，否则拆掉后面几轮
        val userSaverHang = screenSaver.isShowing() && !outcome.screenSaverEngaged
        val autoSleep = request.autoSleepAfterTask && !skipSleep && !userSaverHang
        try {
            if (result == ExecutionResult.STARTED && request.autoSleepAfterTask && skipSleep) {
                log.append(uiTextOf(R.string.schedule_log_auto_sleep_skipped_awake))
            } else if (result == ExecutionResult.STARTED && request.autoSleepAfterTask && userSaverHang) {
                log.append(uiTextOf(R.string.schedule_log_auto_sleep_skipped_screensaver))
            }
            log.end(result, terminalMessage)
            if (request.strategyId.isNotEmpty()) {
                scheduleRepository.recordExecutionResult(
                    strategyId = request.strategyId,
                    result = result,
                    message = triggerLogger.resolveMessage(terminalMessage),
                )
            }
            if (result != ExecutionResult.STARTED && result != ExecutionResult.CANCELLED) {
                _effects.trySend(
                    LaunchEffect.Feedback(
                        uiTextOf(
                            R.string.notification_schedule_detail,
                            request.displayName,
                            terminalMessage ?: uiTextOf(R.string.schedule_result_failed_start),
                        ),
                    ),
                )
            }
        } finally {
            lastCompletedRequestId.set(request.requestId)
            activeRequestId.compareAndSet(request.requestId, null)
            mutex.release(request.requestId)
            _session.update { cur ->
                if (cur is LaunchSession.InFlight
                    && cur.request.requestId == request.requestId
                ) {
                    LaunchSession.Idle
                } else {
                    cur
                }
            }
            // 全局关游戏由后台任务页处理，这里不重复
            val closeGame = outcome.backgroundRun
                    && request.closeGameAfterTask
                    && !appSettingsManager.closeAppOnTaskEnd.value
            val screenSaverEngaged = outcome.screenSaverEngaged
            if (result != ExecutionResult.STARTED) {
                if (screenSaverEngaged) screenSaver.hide()
            } else if (closeGame || autoSleep || screenSaverEngaged) {
                taskEndRegistry.armOnce { reason ->
                    onTaskEnd(reason, closeGame, autoSleep, screenSaverEngaged)
                }
            }
        }
    }

    private suspend fun preemptInFlight(incoming: LaunchRequest) {
        val held = mutex.current
        if (held != null) {
            val oldJob = jobs[held.requestId]
            oldJob?.cancel(CancellationException("preempted by ${incoming.requestId}"))
            withTimeoutOrNull(PREEMPT_JOIN_TIMEOUT_MS) {
                oldJob?.join()
            } ?: Timber.w(
                "LaunchPipeline: preempt join timed out for %s",
                held.requestId,
            )
        }
        takeOverFromPreviousRun()
        compositionService.stop()
        compositionService.stopVirtualDisplay()
        mutex.releaseAny()
        Timber.i("LaunchPipeline: force preempt for %s", incoming.requestId)
    }

    /** 抢占前撤掉上一轮收尾与屏保，避免 stop 边沿触发旧 autoSleep */
    private suspend fun takeOverFromPreviousRun() {
        taskEndRegistry.disarmOnce()
        screenSaver.hide()
    }

    /** mutex 未拿到时的旁路结果，独立 writeClosed */
    private suspend fun finishWithoutHold(
        request: LaunchRequest,
        result: ExecutionResult,
        message: UiText,
    ) {
        withContext(NonCancellable) {
            triggerLogger.writeClosed(
                strategyId = request.strategyId,
                strategyName = request.displayName,
                scheduledTimeMs = request.scheduledTimeMs,
                result = result,
                message = message,
                runMode = appSettingsManager.runMode.value.name,
            )
            if (request.strategyId.isNotEmpty()) {
                scheduleRepository.recordExecutionResult(
                    strategyId = request.strategyId,
                    result = result,
                    message = triggerLogger.resolveMessage(message),
                )
            }
            _effects.trySend(
                LaunchEffect.Feedback(
                    uiTextOf(R.string.notification_schedule_detail, request.displayName, message),
                ),
            )
            lastCompletedRequestId.set(request.requestId)
        }
    }

    private fun setPhase(
        request: LaunchRequest,
        phase: LaunchSession.Phase,
        presentUi: Boolean,
    ) {
        _session.update { cur ->
            when {
                cur is LaunchSession.Idle ->
                    LaunchSession.InFlight(request, phase, presentUi)

                cur is LaunchSession.InFlight
                        && cur.request.requestId == request.requestId ->
                    LaunchSession.InFlight(request, phase, presentUi)

                else -> cur
            }
        }
    }

    private suspend fun onTaskEnd(
        reason: TaskEndRegistry.Reason,
        closeGame: Boolean,
        autoSleep: Boolean,
        releaseScreenSaver: Boolean,
    ) {
        Timber.i(
            "LaunchPipeline: task end reason=%s closeGame=%s autoSleep=%s saver=%s",
            reason, closeGame, autoSleep, releaseScreenSaver,
        )
        // 关游戏只认自然结束
        if (closeGame && reason == TaskEndRegistry.Reason.NATURAL) {
            compositionService.stopVirtualDisplay()
        }
        // 屏保有 KEEP_SCREEN_ON，须先关再熄屏
        if (releaseScreenSaver) {
            screenSaver.hide()
        }
        if (autoSleep) {
            wakeUnlockEngine.lockAndSleep()
        }
    }

    private class RunOutcome {
        var backgroundRun = false

        /** 启动采样：熄屏或锁屏 */
        var tookOverIdleDevice = false
        var screenSaverEngaged = false
    }

    companion object {
        private const val PREEMPT_JOIN_TIMEOUT_MS = 15_000L
    }
}
