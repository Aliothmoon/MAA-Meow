package com.aliothmoon.maameow.domain.launch

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.MaaCompositionService
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
import kotlinx.coroutines.flow.filter
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
    private val chainState: TaskChainState,
    private val compositionService: MaaCompositionService,
    private val triggerLogger: ScheduleTriggerLogger,
    private val scheduleRepository: ScheduleStrategyRepository,
    private val startTaskChain: StartTaskChainUseCase,
    private val countdownUI: CountdownUI,
    private val keyguardLocked: () -> Boolean,
    private val deviceSecure: () -> Boolean,
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
        var backgroundRun = false
        // 每次触发独占一个日志 Session（独立文件），无全局 writer 竞态
        val log = triggerLogger.open(
            strategyId = request.strategyId,
            strategyName = request.displayName,
            scheduledTimeMs = request.scheduledTimeMs,
            runMode = appSettingsManager.runMode.value.name,
        )

        try {
            setPhase(request, LaunchSession.Phase.DevicePrep, presentUi = true)
            log.append(uiTextOf(R.string.schedule_log_received, request.displayName))

            // 任务忙
            val state = compositionService.state.value
            if (state == MaaExecutionState.RUNNING
                || state == MaaExecutionState.STARTING
                || state == MaaExecutionState.STOPPING
            ) {
                if (request.forceStart) {
                    log.append(uiTextOf(R.string.schedule_log_force_stop_running))
                    compositionService.stop()
                    compositionService.stopVirtualDisplay()
                } else {
                    terminalResult = ExecutionResult.SKIPPED_BUSY
                    terminalMessage = uiTextOf(R.string.schedule_log_task_running_busy)
                    return
                }
            }

            // wake + keyguard：仅 Schedule；默认滑动解锁，有密码且未配 PIN 则跳过
            if (request.source == LaunchSource.Schedule) {
                val unlockType = appSettingsManager.wakeUnlockType.value
                val pin = appSettingsManager.wakeCredential.value
                val pinReady = unlockType == "pin" && pin.isNotBlank()
                if (deviceSecure() && !pinReady) {
                    log.append(uiTextOf(R.string.schedule_log_wake_pin_required))
                    terminalResult = ExecutionResult.SKIPPED_LOCKED
                    terminalMessage = uiTextOf(R.string.notification_schedule_pin_required)
                    return
                }
                val credential = if (unlockType == "pin") pin else ""
                log.append(uiTextOf(R.string.schedule_log_wake_start))
                val wake = wakeUnlockEngine.unlock(credential)
                if (wake.isSuccess) {
                    log.append(uiTextOf(R.string.schedule_log_wake_ok))
                    log.append(uiTextOf(R.string.schedule_log_keyguard_skipped))
                } else {
                    log.append(uiTextOf(R.string.schedule_log_wake_failed, wake.message))
                    if (keyguardLocked()) {
                        terminalResult = ExecutionResult.SKIPPED_LOCKED
                        terminalMessage = uiTextOf(R.string.notification_schedule_device_locked)
                        return
                    }
                }
            }

            // profile
            log.append(uiTextOf(R.string.schedule_log_wait_profile))
            chainState.isLoaded.filter { it }.first()
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

            // 前台：无倒计时、不拉主界面；后台：Dialog 倒计时 + 可拉 UI
            // 前台定时/LAUNCH_PROFILE 不会调用 OverlayController.show
            // 悬浮球与音量快捷键依赖用户曾在首页手动启动控制层（_isActive）
            // 未启动时任务仍可跑，但无悬浮球/边框/快捷键，需先开控制层再挂机
            val isForeground = appSettingsManager.runMode.value == RunMode.FOREGROUND
            presentUi = !isForeground
            backgroundRun = !isForeground
            val needsActivityLaunch = request.source == LaunchSource.Schedule && presentUi

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

            // start
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
            // 协程已 cancel 时仍须落库 / 关本 Session
            withContext(NonCancellable) {
                finalizePipeline(request, log, terminalResult, terminalMessage, backgroundRun)
            }
        }
    }

    private suspend fun finalizePipeline(
        request: LaunchRequest,
        log: ScheduleTriggerLogger.Session,
        terminalResult: ExecutionResult?,
        terminalMessage: UiText?,
        backgroundRun: Boolean,
    ) {
        val result = terminalResult ?: ExecutionResult.CANCELLED
        try {
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
            // 全局「任务自动结束时关闭游戏」已开时由后台任务页统一处理，这里不重复关
            val closeGame = backgroundRun
                    && request.closeGameAfterTask
                    && !appSettingsManager.closeAppOnTaskEnd.value
            if (result == ExecutionResult.STARTED
                && (closeGame || request.autoSleepAfterTask)
            ) {
                scope.launch { awaitTaskEnd(closeGame, request.autoSleepAfterTask) }
            }
        }
    }

    private suspend fun preemptInFlight(incoming: LaunchRequest) {
        val held = mutex.current
        if (held != null) {
            val oldJob = jobs[held.requestId]
            oldJob?.cancel(CancellationException("preempted by ${incoming.requestId}"))
            // 等旧 finally 关自己的 log Session / release mutex
            withTimeoutOrNull(PREEMPT_JOIN_TIMEOUT_MS) {
                oldJob?.join()
            } ?: Timber.w(
                "LaunchPipeline: preempt join timed out for %s",
                held.requestId,
            )
        }
        compositionService.stop()
        compositionService.stopVirtualDisplay()
        mutex.releaseAny()
        Timber.i("LaunchPipeline: force preempt for %s", incoming.requestId)
    }

    /** mutex 未拿到时的旁路结果：独立 [writeClosed] 文件，不碰他人 Session */
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

                else -> cur // 已被其他 request 占用，不覆盖
            }
        }
    }

    /** 关游戏只认自然结束（RUNNING → IDLE/ERROR），手动停止不关；熄屏不区分结束方式 */
    private suspend fun awaitTaskEnd(closeGame: Boolean, autoSleep: Boolean) {
        var prev = MaaExecutionState.IDLE
        var started = false
        val endedNaturally = withTimeoutOrNull(POST_TASK_TIMEOUT_MS) {
            compositionService.state.first { cur ->
                val ended = started
                        && (cur == MaaExecutionState.IDLE || cur == MaaExecutionState.ERROR)
                if (!ended) {
                    started = started || cur != MaaExecutionState.IDLE
                    prev = cur
                }
                ended
            }
            prev == MaaExecutionState.RUNNING
        }
        Timber.i(
            "LaunchPipeline: task end natural=%s closeGame=%s autoSleep=%s",
            endedNaturally, closeGame, autoSleep,
        )
        if (closeGame && endedNaturally == true) {
            compositionService.stopVirtualDisplay()
        }
        if (autoSleep) {
            wakeUnlockEngine.lockAndSleep()
        }
    }

    companion object {
        private const val POST_TASK_TIMEOUT_MS = 30L * 60 * 1000
        private const val PREEMPT_JOIN_TIMEOUT_MS = 15_000L
    }
}
