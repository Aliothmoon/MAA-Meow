package com.aliothmoon.maameow.domain.usecase

import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.AppAliveChecker
import com.aliothmoon.maameow.remote.AppAliveStatus
import timber.log.Timber

class PrepareTaskStartUseCase(
    private val analyzeTaskChainUseCase: AnalyzeTaskChainUseCase,
    private val appAliveChecker: AppAliveChecker,
    private val appSettings: AppSettingsManager,
    private val isPackageInstalled: (String) -> Boolean = { true },
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
                )
            }
        }

        // 检查游戏安装包是否存在
        val packageName = plan.gamePackageName
        if (packageName != null
            && !isPackageInstalled(packageName)
            && !context.acknowledgements.contains(TaskStartAcknowledgement.GAME_NOT_INSTALLED)
        ) {
            return when (context.mode) {
                TaskStartMode.MANUAL -> TaskStartDecision.RequiresConfirmation(
                    reason = TaskStartDecisionReason.GAME_NOT_INSTALLED,
                    acknowledgement = TaskStartAcknowledgement.GAME_NOT_INSTALLED,
                )
                TaskStartMode.SCHEDULED -> TaskStartDecision.Blocked(
                    reason = TaskStartDecisionReason.GAME_NOT_INSTALLED,
                )
            }
        }

        val runMode = appSettings.runMode.value
        if (plan.launchesGame ||
            runMode == RunMode.FOREGROUND ||
            context.acknowledgements.contains(TaskStartAcknowledgement.GAME_NOT_RUNNING_WITHOUT_WAKE_UP)
        ) {
            return TaskStartDecision.Ready(plan)
        }
        if (packageName == null) {
            Timber.w(
                "PrepareTaskStart: cannot resolve package name for clientType=%s",
                plan.clientType
            )
            return TaskStartDecision.Ready(plan)
        }

        return when (appAliveChecker.isAppAlive(packageName)) {
            AppAliveStatus.DEAD -> {
                when (context.mode) {
                    TaskStartMode.MANUAL -> {
                        TaskStartDecision.RequiresConfirmation(
                            reason = TaskStartDecisionReason.GAME_NOT_RUNNING_WITHOUT_WAKE_UP,
                            acknowledgement = TaskStartAcknowledgement.GAME_NOT_RUNNING_WITHOUT_WAKE_UP,
                        )
                    }

                    TaskStartMode.SCHEDULED -> {
                        TaskStartDecision.Blocked(
                            reason = TaskStartDecisionReason.GAME_NOT_RUNNING_WITHOUT_WAKE_UP,
                        )
                    }
                }
            }

            AppAliveStatus.UNKNOWN -> {
                Timber.w("PrepareTaskStart: unable to determine whether %s is alive", packageName)
                TaskStartDecision.Ready(plan)
            }

            else -> TaskStartDecision.Ready(plan)
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
}

sealed interface TaskStartDecision {
    data class Ready(val plan: TaskChainPlan) : TaskStartDecision

    data class RequiresConfirmation(
        val reason: TaskStartDecisionReason,
        val acknowledgement: TaskStartAcknowledgement,
        val clientTypes: List<String> = emptyList(),
    ) : TaskStartDecision

    data class Blocked(
        val reason: TaskStartDecisionReason,
        val clientTypes: List<String> = emptyList(),
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
