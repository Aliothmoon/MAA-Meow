package com.aliothmoon.maameow.schedule.service

import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.domain.usecase.PrepareTaskStartUseCase
import com.aliothmoon.maameow.domain.usecase.TaskStartContext
import com.aliothmoon.maameow.domain.usecase.TaskStartDecision
import com.aliothmoon.maameow.domain.usecase.TaskStartMode
import com.aliothmoon.maameow.overlay.OverlayController
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.CountdownState
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.model.ScheduledExecutionRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * 专供前台悬浮球模式使用的静默启动器。
 */
class ForegroundScheduleStarter(
    private val overlayController: OverlayController,
    private val prepareTaskStartUseCase: PrepareTaskStartUseCase,
    private val chainState: TaskChainState,
    private val compositionService: MaaCompositionService,
    private val triggerLogger: ScheduleTriggerLogger,
    private val scheduleRepository: ScheduleStrategyRepository
) {
    private val mutex = Mutex()

    suspend fun executeSilentStart(request: ScheduledExecutionRequest) {
        mutex.withLock {
            Timber.i("SilentStarter: 接管前台悬浮球定时请求 ${request.requestId}")

            if (compositionService.state.value == MaaExecutionState.RUNNING ||
                compositionService.state.value == MaaExecutionState.STARTING) {
                if (request.forceStart) {
                    triggerLogger.append("强制启动: 停止当前运行任务")
                    compositionService.stop()
                } else {
                    val busyMsg = "有任务正在运行，跳过定时执行"
                    triggerLogger.append(busyMsg)
                    recordResult(request, ExecutionResult.SKIPPED_BUSY, busyMsg)
                    return
                }
            }

            chainState.isLoaded.first { it }
            if (chainState.activeProfileId.value != request.profileId) {
                triggerLogger.append("切换任务配置: ${request.profileId}")
                chainState.switchProfile(request.profileId)
            }

            // 倒计时并同步到悬浮球
            var isStartingNow = false

            triggerLogger.append("开始倒计时 (${ScheduledExecutionRequest.COUNTDOWN_SECONDS}s)")

            try {
                overlayController.setTemporaryCountdownListener {
                    isStartingNow = true
                    triggerLogger.append("用户点击立即执行")
                }

                for (remaining in ScheduledExecutionRequest.COUNTDOWN_SECONDS downTo 1) {
                    if (isStartingNow) break
                    overlayController.updateCountdownState(
                        CountdownState.Counting(request.strategyName, remaining)
                    )
                    delay(1000)
                }
            } finally {
                overlayController.updateCountdownState(CountdownState.Idle)
                overlayController.setTemporaryCountdownListener(null)
            }

            triggerLogger.append("倒计时结束，开始准备执行")
            val chain = chainState.chain.value.filter { it.enabled }
            if (chain.isEmpty()) {
                val emptyMsg = "关联的任务配置中没有启用任务"
                triggerLogger.append(emptyMsg)
                recordResult(request, ExecutionResult.FAILED_VALIDATION, emptyMsg)
                return
            }

            try {
                val startContext = TaskStartContext(mode = TaskStartMode.SCHEDULED)
                val decision = prepareTaskStartUseCase.invoke(chain, startContext)

                when (decision) {
                    is TaskStartDecision.Ready -> {
                        triggerLogger.append("前置条件通过，启用任务 ${chain.size} 项，正在启动 MAA 核心服务...")

                        val result = compositionService.start(
                            tasks = decision.plan.params,
                            clientType = decision.plan.clientType,
                            isScheduled = true
                        )

                        if (result is MaaCompositionService.StartResult.Success) {
                            triggerLogger.append("任务启动成功，MAA版本: ${result.version}")
                            recordResult(request, ExecutionResult.STARTED)
                        } else {
                            val failMsg = "MaaCore 启动失败: $result"
                            triggerLogger.append(failMsg)
                            recordResult(request, ExecutionResult.FAILED_START, failMsg)
                        }
                    }
                    is TaskStartDecision.Blocked -> {
                        val blockMsg = "任务被拦截，原因: ${decision.reason}"
                        triggerLogger.append(blockMsg)
                        recordResult(request, ExecutionResult.FAILED_VALIDATION, blockMsg)
                    }
                    is TaskStartDecision.RequiresConfirmation -> {
                        val confirmMsg = "需要确认，但前台模式无法自动确认，取消执行"
                        triggerLogger.append(confirmMsg)
                        recordResult(request, ExecutionResult.FAILED_VALIDATION, confirmMsg)
                    }
                }
            } catch (e: Exception) {
                val errMsg = "解析任务并启动时发生异常: ${e.message}"
                triggerLogger.append(errMsg)
                recordResult(request, ExecutionResult.FAILED_START, errMsg)
            }
        }
    }

    /**
     * 向日志器和数据库同时写入最终状态，闭合日志会话
     */
    private suspend fun recordResult(
        request: ScheduledExecutionRequest,
        result: ExecutionResult,
        message: String? = null
    ) {
        triggerLogger.end(result, message)
        scheduleRepository.recordExecutionResult(
            strategyId = request.strategyId,
            result = result,
            message = message
        )
    }
}