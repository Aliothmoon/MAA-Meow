package com.aliothmoon.maameow.domain.launch

import com.aliothmoon.maameow.R
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
import com.aliothmoon.maameow.domain.usecase.TaskStartContext
import com.aliothmoon.maameow.domain.usecase.TaskStartMode
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.service.ScheduleTriggerLogger
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val notificationCenter: MaaNotificationCenter,
    private val keyguardLocked: () -> Boolean,
    /** 此刻要密码才能进桌面；不是 isDeviceSecure（只说明设过密码） */
    private val deviceLocked: () -> Boolean,
    private val screenInteractive: () -> Boolean,
    private val activityLauncher: suspend (LaunchRequest) -> Boolean,
    /** 提权后端用不了的原因，null = 已连上；会申请授权、等连接 */
    private val remoteAccessBlocker: suspend () -> BackendBlock?,
    /** 主屏顶层应用包名，判不出为 null */
    private val foregroundPackage: suspend () -> String?,
    private val appLabel: (String) -> String,
    /** 沿用手动熄屏挂机的异步指令；正常返回仅表示已发送。 */
    private val setDisplayPower: (Boolean) -> Unit,
) {
    private val _session = MutableStateFlow<LaunchSession>(LaunchSession.Idle)
    val session: StateFlow<LaunchSession> = _session.asStateFlow()

    private val executeLock = Any()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val activeRequestId = AtomicReference<String?>(null)
    private val lastCompletedRequestId = AtomicReference<String?>(null)
    private val cancelRequested = AtomicBoolean(false)
    private val startNowRequested = AtomicBoolean(false)
    private val hardwareScreenOffOwner = AtomicReference<String?>(null)

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

    /** 通知按钮用：留在通知栏的旧按钮不能误伤下一轮 */
    fun submit(event: LaunchUserEvent, requestId: String) {
        if (activeRequestId.get() != requestId) {
            Timber.i("LaunchPipeline: stale %s for %s", event, requestId)
            return
        }
        submit(event)
    }

    private suspend fun runPipeline(request: LaunchRequest) {
        cancelRequested.set(false)
        startNowRequested.set(false)

        if (!mutex.tryAcquire(request.requestId)) {
            if (request.forceStart) {
                // 后端用不了还抢占，只会把别人停了、自己也起不来
                remoteAccessBlocker()?.let {
                    finishWithoutHold(request, ExecutionResult.FAILED_START, it.reason)
                    return
                }
                // 命中黑名单也别抢占在途的
                if (inUseBackgroundSchedule(request)) {
                    (checkForeground() as? ForegroundCheck.Blacklisted)?.let {
                        finishWithoutHold(request, ExecutionResult.SKIPPED_BLACKLIST, it.message)
                        return
                    }
                }
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
        var presentation = LaunchPresentation.DIALOG
        // finally 要用，声明在 try 外
        val outcome = RunOutcome()
        val log = triggerLogger.open(
            strategyId = request.strategyId,
            strategyName = request.displayName,
            scheduledTimeMs = request.scheduledTimeMs,
            runMode = appSettingsManager.runMode.value.name,
        )

        try {
            setPhase(request, LaunchSession.Phase.DevicePrep, presentation)
            log.append(uiTextOf(R.string.schedule_log_received, request.displayName))

            val state = compositionService.state.value
            val busy = state == MaaExecutionState.RUNNING
                    || state == MaaExecutionState.STARTING
                    || state == MaaExecutionState.STOPPING
            if (busy && !request.forceStart) {
                terminalResult = ExecutionResult.SKIPPED_BUSY
                terminalMessage = uiTextOf(R.string.schedule_log_task_running_busy)
                return
            }

            // 解锁、拉起界面、跑任务都靠提权进程，后端没起来就别往下走，否则会被报成锁屏或拉起失败
            // 强制启动也得先查，免得把在跑的停了、自己又起不来
            log.append(uiTextOf(R.string.schedule_log_backend_connecting))
            remoteAccessBlocker()?.let { block ->
                block.detail?.let { log.append(uiTextOf(R.string.schedule_log_backend_connect_cause, it)) }
                terminalResult = ExecutionResult.FAILED_START
                terminalMessage = block.reason
                return
            }

            // 须在唤醒前采样；无锁屏时熄屏也不上锁，亮屏与 keyguard 都看
            outcome.tookOverIdleDevice = !screenInteractive() || keyguardLocked()
            val isForeground = appSettingsManager.runMode.value == RunMode.FOREGROUND
            val inUseSchedule = inUseBackgroundSchedule(request)

            // 先于强制启动的接管，命中就别把在跑的停了
            if (inUseSchedule) {
                when (val check = checkForeground()) {
                    is ForegroundCheck.Blacklisted -> {
                        terminalResult = ExecutionResult.SKIPPED_BLACKLIST
                        terminalMessage = check.message
                        return
                    }

                    ForegroundCheck.Unknown -> log.append(uiTextOf(R.string.schedule_log_blacklist_unknown))
                    ForegroundCheck.Clear -> Unit
                }
            }

            if (busy) {
                log.append(uiTextOf(R.string.schedule_log_force_stop_running))
                takeOverFromPreviousRun()
                compositionService.stop()
                compositionService.stopVirtualDisplay()
            }

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
                // 目标配置可能已删，取不到名字退回 ID
                val profileName = chainState.profiles.value
                    .find { it.id == request.profileId }?.name ?: request.profileId
                log.append(uiTextOf(R.string.schedule_log_switch_profile, profileName))
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
            // 静默只在用手机时：待机没人碰屏幕，要靠拉起的界面保持常亮
            presentation = when {
                isForeground -> LaunchPresentation.NONE
                inUseSchedule && request.silentStartWhenInUse -> LaunchPresentation.NOTIFICATION
                else -> LaunchPresentation.DIALOG
            }
            outcome.backgroundRun = !isForeground
            val needsActivityLaunch = request.source == LaunchSource.Schedule
                    && presentation == LaunchPresentation.DIALOG
            if (presentation == LaunchPresentation.NOTIFICATION) {
                log.append(uiTextOf(R.string.schedule_log_silent_start))
            }

            // 后台 + 待机才盖；用户已开的熄屏挂机不收走，好连跑多轮
            val hardwareScreenOff = appSettingsManager.useHardwareScreenOff.value
            if (request.autoScreenSaver && outcome.backgroundRun && outcome.tookOverIdleDevice) {
                if (screenSaver.isShowing()) {
                    log.append(uiTextOf(R.string.schedule_log_screen_saver_kept))
                } else if (hardwareScreenOff) {
                    // 物理关屏留到倒计时后，期间仍可取消或立即开始。
                    outcome.hardwareScreenOffRequested = true
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
                        setPhase(request, LaunchSession.Phase.Counting(remaining), presentation)
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

            if (outcome.hardwareScreenOffRequested) {
                // 记录归属后再发指令，取消/失败也能释放；不推断底层是否真的关屏。
                outcome.hardwareScreenOffEngaged = true
                withContext(Dispatchers.IO) {
                    synchronized(hardwareScreenOffOwner) {
                        hardwareScreenOffOwner.set(request.requestId)
                        setDisplayPower(false)
                    }
                }
                log.append(uiTextOf(R.string.schedule_log_hardware_screen_off_sent))
            }

            setPhase(request, LaunchSession.Phase.Preparing, presentation)
            setPhase(request, LaunchSession.Phase.Starting, presentation)
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

                is StartTaskChainUseCase.Result.SuccessWithoutCore -> {
                    terminalResult = ExecutionResult.STARTED
                    terminalMessage = null
                    log.append(uiTextOf(R.string.schedule_log_side_task_only))
                    // 旁路结果只有定时触发日志能持久保存
                    result.message?.let { log.append(it) }
                }

                is StartTaskChainUseCase.Result.Failed -> {
                    outcome.startFailureNotified = result.startFailureNotified
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
                notificationCenter.notifyLaunchNotStarted(
                    request.displayName, result, terminalMessage,
                    replacesStartFailure = outcome.startFailureNotified,
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
            // 全局关游戏由 TaskEndRegistry 处理，这里不重复
            val closeGame = outcome.backgroundRun
                    && request.closeGameAfterTask
                    && !appSettingsManager.closeAppOnTaskEnd.value
            val screenSaverEngaged = outcome.screenSaverEngaged
            // Core 未起时 armOnce 看到 IDLE 会当场补跑，不能整段跳过，否则息屏锁屏不执行
            if (result != ExecutionResult.STARTED) {
                releaseHardwareScreenOff(request.requestId)
                if (screenSaverEngaged) screenSaver.hide()
            } else if (closeGame || autoSleep || screenSaverEngaged || outcome.hardwareScreenOffEngaged) {
                taskEndRegistry.armOnce { reason ->
                    onTaskEnd(request.requestId, reason, closeGame, autoSleep, screenSaverEngaged)
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

    private fun inUseBackgroundSchedule(request: LaunchRequest): Boolean =
        request.source == LaunchSource.Schedule
                && appSettingsManager.runMode.value != RunMode.FOREGROUND
                && screenInteractive() && !keyguardLocked()

    private sealed interface ForegroundCheck {
        data object Clear : ForegroundCheck
        data object Unknown : ForegroundCheck
        data class Blacklisted(val message: UiText) : ForegroundCheck
    }

    private suspend fun checkForeground(): ForegroundCheck {
        val blacklist = appSettingsManager.scheduleAppBlacklist.value
        if (blacklist.isEmpty()) return ForegroundCheck.Clear
        val pkg = try {
            foregroundPackage()
        } catch (e: TimeoutCancellationException) {
            // 探测超时不是被抢占，按判不出处理
            Timber.w(e, "LaunchPipeline: foreground probe timed out")
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "LaunchPipeline: foreground probe failed")
            null
        }
        return when (pkg) {
            null -> ForegroundCheck.Unknown
            in blacklist -> ForegroundCheck.Blacklisted(
                uiTextOf(R.string.schedule_log_blacklist_hit, appLabel(pkg)),
            )

            else -> ForegroundCheck.Clear
        }
    }

    /** 抢占前撤掉上一轮收尾与屏保，避免 stop 边沿触发旧 autoSleep */
    private suspend fun takeOverFromPreviousRun() {
        taskEndRegistry.disarmOnce()
        hardwareScreenOffOwner.get()?.let { releaseHardwareScreenOff(it) }
        screenSaver.hide()
    }

    /** 旧轮次的收尾不能恢复新轮次的屏幕；与发关屏指令串行。 */
    private suspend fun releaseHardwareScreenOff(requestId: String): Unit = withContext(Dispatchers.IO) {
        synchronized(hardwareScreenOffOwner) {
            if (!hardwareScreenOffOwner.compareAndSet(requestId, null)) return@synchronized
            runCatching { setDisplayPower(true) }
                .onFailure { Timber.e(it, "Failed to send screen power restore") }
        }
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
            notificationCenter.notifyLaunchNotStarted(request.displayName, result, message)
            lastCompletedRequestId.set(request.requestId)
        }
    }

    private fun setPhase(
        request: LaunchRequest,
        phase: LaunchSession.Phase,
        presentation: LaunchPresentation,
    ) {
        _session.update { cur ->
            when {
                cur is LaunchSession.Idle ->
                    LaunchSession.InFlight(request, phase, presentation)

                cur is LaunchSession.InFlight
                        && cur.request.requestId == request.requestId ->
                    LaunchSession.InFlight(request, phase, presentation)

                else -> cur
            }
        }
    }

    private suspend fun onTaskEnd(
        requestId: String,
        reason: TaskEndRegistry.Reason,
        closeGame: Boolean,
        autoSleep: Boolean,
        releaseScreenSaver: Boolean,
    ) {
        Timber.i(
            "LaunchPipeline: task end reason=%s closeGame=%s autoSleep=%s saver=%s",
            reason, closeGame, autoSleep, releaseScreenSaver,
        )
        // 手动停止不关游戏，其余结束（自然完成 / 掉线中止 / 到达时长上限）都关
        try {
            if (closeGame && reason != TaskEndRegistry.Reason.MANUAL) {
                compositionService.stopVirtualDisplay()
            }
        } finally {
            // 先释放本次遮屏/关屏，再交给系统锁屏息屏，避免物理关屏状态残留。
            releaseHardwareScreenOff(requestId)
            if (releaseScreenSaver) {
                screenSaver.hide()
            }
            if (autoSleep) {
                wakeUnlockEngine.lockAndSleep()
            }
        }
    }

    private class RunOutcome {
        var backgroundRun = false

        /** 启动采样：熄屏或锁屏 */
        var tookOverIdleDevice = false
        var screenSaverEngaged = false
        var hardwareScreenOffRequested = false
        var hardwareScreenOffEngaged = false
        var startFailureNotified = false
    }

    companion object {
        private const val PREEMPT_JOIN_TIMEOUT_MS = 15_000L
    }
}
