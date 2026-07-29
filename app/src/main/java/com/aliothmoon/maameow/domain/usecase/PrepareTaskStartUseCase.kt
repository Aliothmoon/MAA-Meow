package com.aliothmoon.maameow.domain.usecase

import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.utils.i18n.UiText

/**
 * 主任务链的启动决策：链分析([AnalyzeTaskChainUseCase]) + 游戏就绪性闸门([CheckGameReadinessUseCase])。
 */
class PrepareTaskStartUseCase(
    private val analyzeTaskChainUseCase: AnalyzeTaskChainUseCase,
    private val checkGameReadiness: CheckGameReadinessUseCase,
) {
    suspend operator fun invoke(
        chain: List<TaskChainNode>,
        context: TaskStartContext,
    ): TaskStartDecision {
        val plan = when (val analyzeResult = analyzeTaskChainUseCase(chain)) {
            is AnalyzeTaskChainResult.Ready -> analyzeResult.plan
            is AnalyzeTaskChainResult.Blocked -> {
                return TaskStartDecision.Blocked(
                    reason = analyzeResult.reason.toDecisionReason(),
                    clientTypes = analyzeResult.clientTypes,
                    details = analyzeResult.logs.map { it.first },
                )
            }
        }

        return when (val readiness = checkGameReadiness(plan.clientType, plan.launchesGame, context)) {
            is GameReadiness.Ready ->
                TaskStartDecision.Ready(plan.copy(gameAliveBeforeStart = readiness.gameAliveBeforeStart))

            is GameReadiness.RequiresConfirmation ->
                TaskStartDecision.RequiresConfirmation(readiness.acknowledgement)

            is GameReadiness.Blocked ->
                TaskStartDecision.Blocked(readiness.reason)
        }
    }
}

data class TaskStartContext(
    val mode: TaskStartMode,
    val acknowledgements: Set<TaskStartAcknowledgement> = emptySet(),
) {
    fun acknowledged(acknowledgement: TaskStartAcknowledgement): TaskStartContext {
        return copy(acknowledgements = acknowledgements + acknowledgement)
    }
}

enum class TaskStartMode {
    MANUAL,
    SCHEDULED,
}

enum class TaskStartAcknowledgement {
    GAME_NOT_RUNNING_WITHOUT_WAKE_UP,
    GAME_NOT_INSTALLED,
}

enum class TaskStartDecisionReason {
    NO_TASK_SELECTED,
    CONFLICTING_CLIENT_TYPES,
    NO_EXECUTABLE_TASKS,
    GAME_NOT_RUNNING_WITHOUT_WAKE_UP,
    GAME_NOT_INSTALLED,
    GAME_NOT_ON_BACKGROUND_DISPLAY,
}

sealed interface TaskStartDecision {
    data class Ready(val plan: TaskChainPlan) : TaskStartDecision

    data class RequiresConfirmation(
        val acknowledgement: TaskStartAcknowledgement,
    ) : TaskStartDecision

    data class Blocked(
        val reason: TaskStartDecisionReason,
        val clientTypes: List<String> = emptyList(),
        /** 拦截的具体原因明细，附在拦截文案之后（如库存保持逐条计划为何被跳过） */
        val details: List<UiText> = emptyList(),
    ) : TaskStartDecision
}

private fun AnalyzeTaskChainFailureReason.toDecisionReason(): TaskStartDecisionReason {
    return when (this) {
        AnalyzeTaskChainFailureReason.NO_TASK_SELECTED -> TaskStartDecisionReason.NO_TASK_SELECTED
        AnalyzeTaskChainFailureReason.CONFLICTING_CLIENT_TYPES -> {
            TaskStartDecisionReason.CONFLICTING_CLIENT_TYPES
        }

        AnalyzeTaskChainFailureReason.NO_EXECUTABLE_TASKS -> TaskStartDecisionReason.NO_EXECUTABLE_TASKS
    }
}
