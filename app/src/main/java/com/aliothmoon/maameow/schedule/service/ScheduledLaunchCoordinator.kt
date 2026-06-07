package com.aliothmoon.maameow.schedule.service

import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.models.OverlayControlMode
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.CountdownState
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.model.ScheduledExecutionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class ScheduledLaunchCoordinator(
    private val scope: CoroutineScope,
    private val scheduleRepository: ScheduleStrategyRepository,
    private val compositionService: MaaCompositionService,
    private val appSettingsManager: AppSettingsManager,
    private val chainState: TaskChainState,
    private val triggerLogger: ScheduleTriggerLogger,
) {
    private val _countdownState = MutableStateFlow<CountdownState>(CountdownState.Idle)
    val countdownState: StateFlow<CountdownState> = _countdownState.asStateFlow()

    private val _pendingExecution = MutableStateFlow<ScheduledExecutionRequest?>(null)
    val pendingExecution: StateFlow<ScheduledExecutionRequest?> = _pendingExecution.asStateFlow()

    private val _feedbackMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val feedbackMessages: SharedFlow<String> = _feedbackMessages.asSharedFlow()

    private var activeRequest: ScheduledExecutionRequest? = null
    private var startingRequestId: String? = null
    private var lastHandledRequestId: String? = null
    private var countdownJob: Job? = null

    fun onLaunch(request: ScheduledExecutionRequest) {
        scope.launch { handleLaunch(request) }
    }

    fun onCancel() {
        val request = activeRequest ?: return
        if (_countdownState.value !is CountdownState.Counting) return
        lastHandledRequestId = request.requestId
        triggerLogger.append("用户取消了定时任务执行")
        scope.launch {
            finishFlow(request, ExecutionResult.CANCELLED, "用户取消了定时任务执行")
        }
    }

    fun onStartNow() {
        val request = activeRequest ?: return
        if (_countdownState.value !is CountdownState.Counting) return
        triggerLogger.append("用户点击立即执行")
        promote(request)
    }

    /**
     * 由 View 在导航到后台任务页后调用。
     * [execute] 执行实际的任务启动，返回错误信息或 null 表示成功。
     */
    fun onPageReady(requestId: String, execute: suspend (ScheduledExecutionRequest) -> String?) {
        val request = _pendingExecution.value ?: return
        if (request.requestId != requestId || startingRequestId == requestId) return

        scope.launch {
            startingRequestId = requestId
            _pendingExecution.value = null
            triggerLogger.append("页面就绪，开始执行任务")
            val message = execute(request)
            if (message == null) {
                triggerLogger.append("任务启动成功")
                finishFlow(request, ExecutionResult.STARTED)
            } else {
                triggerLogger.append("任务启动失败: $message")
                finishFlow(request, ExecutionResult.FAILED_START, message)
            }
        }
    }

    fun cancel() {
        cancelCountdown()
    }

    // ==================== Internal ====================

    private suspend fun handleLaunch(request: ScheduledExecutionRequest) {
        if (isDuplicate(request)) return

        triggerLogger.append("收到启动请求: ${request.strategyName}")

        if (hasPendingFlow()) {
            if (request.forceStart) {
                triggerLogger.append("强制启动: 清除当前待处理流程")
                clearFlow()
            } else {
                reject(request, ExecutionResult.SKIPPED_BUSY, "已有定时任务正在处理中")
                return
            }
        }

        if (compositionService.state.value == MaaExecutionState.RUNNING
            || compositionService.state.value == MaaExecutionState.STARTING
            || compositionService.state.value == MaaExecutionState.STOPPING
        ) {
            if (request.forceStart) {
                triggerLogger.append("强制启动: 停止当前运行任务")
                compositionService.stop()
                compositionService.stopVirtualDisplay()
            } else {
                reject(request, ExecutionResult.SKIPPED_BUSY, "有任务正在运行")
                return
            }
        }

        val isBackgroundMode = appSettingsManager.runMode.value == RunMode.BACKGROUND
        val allowForeground = appSettingsManager.allowForegroundScheduledTask.value
        val isFloatBall = appSettingsManager.overlayControlMode.value == OverlayControlMode.FLOAT_BALL

        if (!isBackgroundMode) {
            if (!allowForeground) {
                reject(request, ExecutionResult.FAILED_VALIDATION, "当前运行模式不是后台模式，且未开启“允许前台定时任务”开关")
                return
            }
            if (!isFloatBall) {
                reject(request, ExecutionResult.FAILED_VALIDATION, "当前运行模式不是后台模式，且控制模式不是悬浮球模式")
                return
            }
        }

        triggerLogger.append("等待任务配置加载...")
        chainState.isLoaded.filter { it }.first()
        if (chainState.activeProfileId.value != request.profileId) {
            triggerLogger.append("切换任务配置: ${request.profileId}")
            chainState.switchProfile(request.profileId)
        }
        if (chainState.activeProfileId.value != request.profileId) {
            reject(request, ExecutionResult.FAILED_VALIDATION, "关联的任务配置已被删除")
            return
        }

        val enabledNodes = chainState.chain.value.filter { it.enabled }
        if (enabledNodes.isEmpty()) {
            reject(request, ExecutionResult.FAILED_VALIDATION, "关联的任务配置中没有启用任务")
            return
        }

        triggerLogger.append("前置条件通过，启用任务 ${enabledNodes.size} 项")
        lastHandledRequestId = request.requestId
        activeRequest = request
        startCountdown(request)
    }

    private fun isDuplicate(request: ScheduledExecutionRequest): Boolean {
        return request.requestId == lastHandledRequestId
                || activeRequest?.requestId == request.requestId
                || _pendingExecution.value?.requestId == request.requestId
                || startingRequestId == request.requestId
    }

    private fun hasPendingFlow(): Boolean {
        return activeRequest != null
                || _pendingExecution.value != null
                || startingRequestId != null
                || _countdownState.value is CountdownState.Counting
    }

    private fun startCountdown(request: ScheduledExecutionRequest) {
        cancelCountdown()
        triggerLogger.append("开始倒计时 (${ScheduledExecutionRequest.COUNTDOWN_SECONDS}s)")
        countdownJob = scope.launch {
            for (remaining in ScheduledExecutionRequest.COUNTDOWN_SECONDS downTo 1) {
                if (activeRequest?.requestId != request.requestId) return@launch
                _countdownState.update {
                    CountdownState.Counting(
                        strategyName = request.strategyName,
                        remainingSeconds = remaining,
                    )
                }
                delay(1000)
            }
            if (activeRequest?.requestId == request.requestId) {
                triggerLogger.append("倒计时结束，提交执行")
                promote(request)
            }
        }
    }

    private fun promote(request: ScheduledExecutionRequest) {
        cancelCountdown()
        activeRequest = request
        _countdownState.value = CountdownState.Idle
        _pendingExecution.value = request
    }

    private suspend fun reject(
        request: ScheduledExecutionRequest,
        result: ExecutionResult,
        message: String,
    ) {
        lastHandledRequestId = request.requestId
        triggerLogger.append("跳过: $message")
        triggerLogger.end(result, message)
        scheduleRepository.recordExecutionResult(
            strategyId = request.strategyId,
            result = result,
            message = message,
        )
        _feedbackMessages.tryEmit("定时任务「${request.strategyName}」: $message")
    }

    private suspend fun finishFlow(
        request: ScheduledExecutionRequest,
        result: ExecutionResult,
        message: String? = null,
    ) {
        try {
            triggerLogger.end(result, message)
            scheduleRepository.recordExecutionResult(
                strategyId = request.strategyId,
                result = result,
                message = message,
            )
        } finally {
            clearFlow()
        }
    }

    private fun clearFlow() {
        cancelCountdown()
        activeRequest = null
        startingRequestId = null
        _countdownState.value = CountdownState.Idle
        _pendingExecution.value = null
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }
}
