package com.aliothmoon.maameow.domain.usecase

import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.data.model.MallConfig
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.data.resource.ServerTimezone
import com.aliothmoon.maameow.domain.models.MallCreditFightAvailability
import com.aliothmoon.maameow.domain.models.resolveMallCreditFightAvailability
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import timber.log.Timber
import java.time.DayOfWeek

class AnalyzeTaskChainUseCase {
    companion object {
        const val EMPTY_PARAMS_MESSAGE = "当前没有可执行的任务（可能被周计划过滤）"
    }

    operator fun invoke(chain: List<TaskChainNode>): AnalyzeTaskChainResult {
        val enabledNodes = chain.filter { it.enabled }.sortedBy { it.order }
        if (enabledNodes.isEmpty()) {
            return AnalyzeTaskChainResult.Blocked(
                reason = AnalyzeTaskChainFailureReason.INVALID_CHAIN,
                message = "请先选择要执行的任务",
            )
        }

        validateClientTypeConsistency(enabledNodes)?.let { message ->
            return AnalyzeTaskChainResult.Blocked(
                reason = AnalyzeTaskChainFailureReason.INVALID_CHAIN,
                message = message,
            )
        }

        val clientType = resolveClientType(enabledNodes)
        val creditFightAvailability = resolveMallCreditFightAvailability(enabledNodes)
        val serverDayOfWeek = ServerTimezone.getYjDayOfWeek(clientType)

        logCreditFightWarning(enabledNodes, creditFightAvailability)

        val params = enabledNodes.mapNotNull { node ->
            if (isSkippedByWeeklySchedule(node, serverDayOfWeek)) {
                return@mapNotNull null
            }
            buildNodeParams(node, creditFightAvailability, clientType)
        }

        if (params.isEmpty()) {
            return AnalyzeTaskChainResult.Blocked(
                reason = AnalyzeTaskChainFailureReason.NO_EXECUTABLE_TASKS,
                message = EMPTY_PARAMS_MESSAGE,
            )
        }

        return AnalyzeTaskChainResult.Ready(
            TaskChainPlan(
                enabledNodes = enabledNodes,
                params = params,
                clientType = clientType,
                gamePackageName = Packages[clientType],
                launchesGame = enabledNodes
                    .mapNotNull { it.config as? WakeUpConfig }
                    .any { it.startGameEnabled },
            )
        )
    }

    private fun validateClientTypeConsistency(nodes: List<TaskChainNode>): String? {
        val clientTypes = nodes
            .mapNotNull { (it.config as? WakeUpConfig)?.clientType }
            .distinct()
        if (clientTypes.size > 1) {
            return "任务链中存在多个不同的客户端类型（${clientTypes.joinToString("、")}），请保持一致"
        }
        return null
    }

    private fun resolveClientType(nodes: List<TaskChainNode>): String {
        return nodes.firstNotNullOfOrNull { (it.config as? WakeUpConfig)?.clientType } ?: "Official"
    }

    private fun isSkippedByWeeklySchedule(node: TaskChainNode, serverDayOfWeek: DayOfWeek): Boolean {
        val config = node.config
        if (config is FightConfig && config.useWeeklySchedule) {
            if (config.weeklySchedule[serverDayOfWeek.name] == false) {
                Timber.d("WeeklySchedule: skip node '%s' on %s", node.name, serverDayOfWeek)
                return true
            }
        }
        return false
    }

    private fun buildNodeParams(
        node: TaskChainNode,
        creditFightAvailability: MallCreditFightAvailability,
        clientType: String,
    ): MaaTaskParams {
        return when (val config = node.config) {
            is MallConfig -> {
                config.toTaskParams(
                    creditFightEnabled = config.creditFight && creditFightAvailability.isAvailable,
                    clientType = clientType,
                )
            }

            else -> config.toTaskParams()
        }
    }

    private fun logCreditFightWarning(
        nodes: List<TaskChainNode>,
        availability: MallCreditFightAvailability,
    ) {
        if (!availability.isAvailable && nodes.any { (it.config as? MallConfig)?.creditFight == true }) {
            Timber.w(
                "Credit fight disabled because a fight task has no resolvable active stage. task=%s order=%d",
                availability.blockingTaskName ?: "unknown",
                availability.blockingTaskOrder ?: -1,
            )
        }
    }
}

data class TaskChainPlan(
    val enabledNodes: List<TaskChainNode>,
    val params: List<MaaTaskParams>,
    val clientType: String,
    val gamePackageName: String?,
    val launchesGame: Boolean,
)

enum class AnalyzeTaskChainFailureReason {
    INVALID_CHAIN,
    NO_EXECUTABLE_TASKS,
}

sealed interface AnalyzeTaskChainResult {
    data class Ready(val plan: TaskChainPlan) : AnalyzeTaskChainResult

    data class Blocked(
        val reason: AnalyzeTaskChainFailureReason,
        val message: String,
    ) : AnalyzeTaskChainResult
}
