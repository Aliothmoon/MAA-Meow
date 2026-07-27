package com.aliothmoon.maameow.domain.usecase

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.model.MallConfig
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.TaskParamContext
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.data.resource.ServerTimezone
import com.aliothmoon.maameow.domain.models.MallCreditFightAvailability
import com.aliothmoon.maameow.domain.models.SeriesLock
import com.aliothmoon.maameow.domain.models.resolveMallCreditFightAvailability
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import timber.log.Timber
import java.time.DayOfWeek

class AnalyzeTaskChainUseCase(
    private val taskChainState: TaskChainState,
    private val resourceDataManager: ResourceDataManager,
) {
    operator fun invoke(chain: List<TaskChainNode>): AnalyzeTaskChainResult {
        val enabledNodes = chain.filter { it.enabled }.sortedBy { it.order }
        if (enabledNodes.isEmpty()) {
            return AnalyzeTaskChainResult.Blocked(
                reason = AnalyzeTaskChainFailureReason.NO_TASK_SELECTED,
            )
        }

        validateClientTypeConsistency(enabledNodes)?.let { clientTypes ->
            return AnalyzeTaskChainResult.Blocked(
                reason = AnalyzeTaskChainFailureReason.CONFLICTING_CLIENT_TYPES,
                clientTypes = clientTypes,
            )
        }

        val clientType = taskChainState.getClientType()
        val creditFightAvailability = resolveMallCreditFightAvailability(enabledNodes)
        val serverDayOfWeek = ServerTimezone.getYjDayOfWeek(clientType)

        logCreditFightWarning(enabledNodes, creditFightAvailability)

        val ctx = TaskParamContext(
            clientType = clientType,
            chainAllowsCreditFight = creditFightAvailability.isAvailable,
            normalizeCoreChar = { coreChar ->
                resourceDataManager.getCharacterByNameOrAlias(coreChar)?.name ?: coreChar
            },
        )

        val params = enabledNodes.flatMap { node ->
            if (isSkippedByWeeklySchedule(node, serverDayOfWeek)) {
                return@flatMap emptyList()
            }
            node.config.toTaskParams(ctx).map { it.copy(nodeId = node.id) }
        }

        if (params.isEmpty()) {
            return AnalyzeTaskChainResult.Blocked(
                reason = AnalyzeTaskChainFailureReason.NO_EXECUTABLE_TASKS,
            )
        }

        val preflightLogs = mutableListOf<Pair<UiText, LogLevel>>()
        // TODO: MaaCore 适配代理倍率 7~10 后删除
        if (SeriesLock.isLocked(clientType) && enabledNodes.any { it.config is FightConfig }) {
            preflightLogs += uiTextOf(R.string.runlog_series_locked) to LogLevel.WARNING
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
                preflightLogs = preflightLogs,
            )
        )
    }

    private fun validateClientTypeConsistency(nodes: List<TaskChainNode>): List<String>? {
        val clientTypes = nodes
            .mapNotNull { (it.config as? WakeUpConfig)?.clientType }
            .distinct()
        if (clientTypes.size > 1) {
            return clientTypes
        }
        return null
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
    val gameAliveBeforeStart: Boolean? = null,
    /**
     * 任务链分析阶段产生的、需要在会话开始后回放给用户的日志。
     * UseCase 保持无副作用，由 MaaCompositionService 在 startSession 之后统一 append。
     */
    val preflightLogs: List<Pair<UiText, LogLevel>> = emptyList(),
)

enum class AnalyzeTaskChainFailureReason {
    NO_TASK_SELECTED,
    CONFLICTING_CLIENT_TYPES,
    NO_EXECUTABLE_TASKS,
}

sealed interface AnalyzeTaskChainResult {
    data class Ready(val plan: TaskChainPlan) : AnalyzeTaskChainResult

    data class Blocked(
        val reason: AnalyzeTaskChainFailureReason,
        val clientTypes: List<String> = emptyList(),
    ) : AnalyzeTaskChainResult
}
