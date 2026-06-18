package com.aliothmoon.maameow.schedule.service

import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.OverlayControlMode
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.service.AchievementReporter
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
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean


class ForegroundScheduleStarter(
    private val overlayController: OverlayController,
    private val prepareTaskStartUseCase: PrepareTaskStartUseCase,
    private val chainState: TaskChainState,
    private val compositionService: MaaCompositionService,
    private val triggerLogger: ScheduleTriggerLogger,
    private val scheduleRepository: ScheduleStrategyRepository,
    private val appSettingsManager: AppSettingsManager,
    private val achievementReporter: AchievementReporter,
) {
    private val executing = AtomicBoolean(false)

    suspend fun executeSilentStart(request: ScheduledExecutionRequest) {
        if (!executing.compareAndSet(false, true)) {
            triggerLogger.append("已有定时任务正在准备执行，跳过本次请求")
            recordResult(request, ExecutionResult.SKIPPED_BUSY, "另一个定时任务正在执行")
            return
        }
        try {
            Timber.i("接管前台定时请求 ${request.requestId}")

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

            // 无论哪种模式，都执行倒计时等待到整点
            var isStartingNow = false
            val isFloatBall = appSettingsManager.overlayControlMode.value == OverlayControlMode.FLOAT_BALL

            if (isFloatBall) {
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
            } else {
                // 非悬浮球模式：静默等待，不更新 UI（但仍保证任务在整点开始）
                triggerLogger.append("非悬浮球模式，静默倒计时")
                delay(ScheduledExecutionRequest.COUNTDOWN_SECONDS * 1000L)
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
                when (val decision = prepareTaskStartUseCase.invoke(chain, startContext)) {
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
        } finally {
            executing.set(false)
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
        achievementReporter.reportScheduleResult(result)
        scheduleRepository.recordExecutionResult(
            strategyId = request.strategyId,
            result = result,
            message = message
        )
    }
}
