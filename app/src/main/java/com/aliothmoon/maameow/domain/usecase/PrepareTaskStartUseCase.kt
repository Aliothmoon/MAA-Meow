package com.aliothmoon.maameow.domain.usecase

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextJoin
import com.aliothmoon.maameow.utils.i18n.uiTextLines
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import com.aliothmoon.maameow.utils.i18n.wakeUpClientTypeLabel

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
        val plan = when (val analyzeResult = analyzeTaskChainUseCase(chain, context.fromNodeId)) {
            is AnalyzeTaskChainResult.Ready -> analyzeResult.plan
            is AnalyzeTaskChainResult.Blocked -> {
                return TaskStartDecision.Blocked(
                    reason = analyzeResult.reason.toDecisionReason(),
                    details = listOfNotNull(analyzeResult.clientTypes.toClientTypesLine()) +
                            analyzeResult.logs.map { it.first },
                )
            }
        }

        // 只有旁路任务时不碰游戏，就绪闸门跳过
        if (plan.params.isEmpty()) {
            return TaskStartDecision.Ready(plan)
        }

        return when (val readiness =
            checkGameReadiness(plan.clientType, plan.launchesGame, context)) {
            is GameReadiness.Ready ->
                TaskStartDecision.Ready(plan.copy(gameAliveBeforeStart = readiness.gameAliveBeforeStart))

            is GameReadiness.RequiresConfirmation -> {
                val acknowledgement = readiness.acknowledgement
                // 走到这里说明本轮不拉起游戏；链上却有启用的唤醒，只能是被「从此处运行」跳过了
                val wakeUpSkipped =
                    acknowledgement == TaskStartAcknowledgement.GAME_NOT_RUNNING_WITHOUT_WAKE_UP &&
                            chain.any { it.enabled && (it.config as? WakeUpConfig)?.startGameEnabled == true }
                TaskStartDecision.RequiresConfirmation(
                    acknowledgement = acknowledgement,
                    message = if (wakeUpSkipped) {
                        uiTextOf(R.string.task_start_warning_wake_up_skipped)
                    } else {
                        acknowledgement.message
                    },
                )
            }

            is GameReadiness.Blocked ->
                TaskStartDecision.Blocked(readiness.reason)
        }
    }
}

data class TaskStartContext(
    val mode: TaskStartMode,
    val acknowledgements: Set<TaskStartAcknowledgement> = emptySet(),
    /** 从此节点起运行，之前的节点本轮跳过；null 为整条链 */
    val fromNodeId: String? = null,
) {
    fun acknowledged(acknowledgement: TaskStartAcknowledgement): TaskStartContext {
        return copy(acknowledgements = acknowledgements + acknowledgement)
    }
}

enum class TaskStartMode {
    MANUAL,
    SCHEDULED,
}

/** 手动模式下需用户确认的警告 */
enum class TaskStartAcknowledgement(val message: UiText) {
    GAME_NOT_RUNNING_WITHOUT_WAKE_UP(uiTextOf(R.string.task_start_warning_game_not_running)),
    GAME_NOT_INSTALLED(uiTextOf(R.string.task_start_warning_game_not_installed)),
    EYE_PROTECTION_ENABLED(uiTextOf(R.string.task_start_warning_eye_protection_enabled)),
    SMART_RESOLUTION_ENABLED(uiTextOf(R.string.task_start_warning_smart_resolution_enabled)),
    ADB_INPUT_RESTRICTED(uiTextOf(R.string.task_start_warning_adb_input_restricted)),
}

/** 拦截原因，文案随枚举走，定时与手动入口共用 */
enum class TaskStartDecisionReason(val message: UiText) {
    NO_TASK_SELECTED(uiTextOf(R.string.task_start_error_no_task_selected)),
    CONFLICTING_CLIENT_TYPES(uiTextOf(R.string.task_start_error_conflicting_client_types)),
    NO_EXECUTABLE_TASKS(uiTextOf(R.string.task_start_error_no_executable_tasks)),
    GAME_NOT_RUNNING_WITHOUT_WAKE_UP(uiTextOf(R.string.task_start_error_scheduled_no_wakeup)),
    GAME_NOT_INSTALLED(uiTextOf(R.string.task_start_error_scheduled_game_not_installed)),
    GAME_NOT_ON_BACKGROUND_DISPLAY(uiTextOf(R.string.task_start_error_game_not_on_background_display)),
}

sealed interface TaskStartDecision {
    data class Ready(val plan: TaskChainPlan) : TaskStartDecision

    data class RequiresConfirmation(
        val acknowledgement: TaskStartAcknowledgement,
        val message: UiText = acknowledgement.message,
    ) : TaskStartDecision

    data class Blocked(
        val reason: TaskStartDecisionReason,
        /** 拦截的具体原因明细，附在拦截文案之后（如冲突的客户端、库存保持逐条计划为何被跳过） */
        val details: List<UiText> = emptyList(),
    ) : TaskStartDecision {
        val message: UiText get() = uiTextLines(reason.message, *details.toTypedArray())
    }
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

private fun List<String>.toClientTypesLine(): UiText? {
    if (isEmpty()) return null
    return uiTextJoin(
        *map(::wakeUpClientTypeLabel).toTypedArray(),
        separator = uiTextOf(R.string.common_enumeration_separator),
    )
}
