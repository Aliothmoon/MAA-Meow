package com.aliothmoon.maameow.domain.usecase

import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.model.CollectingPreflightLogSink
import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.TaskParamContext
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.repository.OperBoxRepository
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.data.resource.ServerTimezone
import com.aliothmoon.maameow.domain.models.MallCreditFightAvailability
import com.aliothmoon.maameow.domain.service.FightDropsRefresher
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.maa.task.TaskSlot
import com.aliothmoon.maameow.utils.i18n.UiText
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.DayOfWeek

class AnalyzeTaskChainUseCase(
    private val taskChainState: TaskChainState,
    private val resourceDataManager: ResourceDataManager,
    private val activityManager: ActivityManager,
    private val depotRepository: DepotRepository,
    private val operBoxRepository: OperBoxRepository,
    private val itemHelper: ItemHelper,
    private val dropsRefresher: FightDropsRefresher,
) {
    /** 先等 depot/operBox 分片装载；config 的 toTaskParams 仍是非 suspend。 */
    suspend operator fun invoke(chain: List<TaskChainNode>): AnalyzeTaskChainResult {
        depotRepository.isLoaded.first { it }
        operBoxRepository.isLoaded.first { it }

        val nodes = chain.filter { it.enabled }.sortedBy { it.order }
        if (nodes.isEmpty()) {
            return AnalyzeTaskChainResult.Blocked(
                reason = AnalyzeTaskChainFailureReason.NO_TASK_SELECTED,
            )
        }
        val list = getWakeUpClientTypeList(nodes)
        if (list.size > 1) {
            return AnalyzeTaskChainResult.Blocked(
                reason = AnalyzeTaskChainFailureReason.CONFLICTING_CLIENT_TYPES,
                clientTypes = list,
            )
        }


        val info = MallCreditFightAvailability.resolve(nodes, activityManager)

        dropsRefresher.clear()

        val clientType = taskChainState.clientType
        val log = CollectingPreflightLogSink()

        val serverDayOfWeek = ServerTimezone.getYjDayOfWeek(clientType)
        val expanded = nodes.flatMap { node ->
            if (isSkippedByWeeklySchedule(node, serverDayOfWeek)) {
                return@flatMap emptyList()
            }
            val ctx = TaskParamContext(
                node = node,
                clientType = clientType,
                chainAllowsCreditFight = info.isAvailable,
                itemHelper = itemHelper,
                activityManager = activityManager,
                depotRepository = depotRepository,
                operBoxRepository = operBoxRepository,
                resourceDataManager = resourceDataManager,
                dropsRefresher = dropsRefresher,
                logSink = log,
            )
            node.config.toTaskParams(ctx).mapIndexed { index, task ->
                task.copy(slot = TaskSlot(node.id, index))
            }
        }
        val params = dropAdjacentDuplicateDepot(expanded)
        val logs = log.entries

        if (params.isEmpty()) {
            return AnalyzeTaskChainResult.Blocked(
                reason = AnalyzeTaskChainFailureReason.NO_EXECUTABLE_TASKS,
                logs = logs,
            )
        }

        return AnalyzeTaskChainResult.Ready(
            TaskChainPlan(
                nodes = nodes,
                params = params,
                clientType = clientType,
                gamePackageName = Packages[clientType],
                launchesGame = nodes
                    .mapNotNull { it.config as? WakeUpConfig }
                    .any { it.startGameEnabled },
                logs = logs,
            )
        )
    }

    /** 去掉相邻重复 DEPOT；中间有其它任务则保留（库存可能已变）。 */
    private fun dropAdjacentDuplicateDepot(params: List<MaaTaskParams>): List<MaaTaskParams> =
        params.filterIndexed { index, task ->
            index == 0 ||
                    task.type != MaaTaskType.DEPOT ||
                    params[index - 1].type != MaaTaskType.DEPOT
        }

    private fun getWakeUpClientTypeList(nodes: List<TaskChainNode>): List<String> {
        return nodes.mapNotNull { (it.config as? WakeUpConfig)?.clientType }
            .distinct()
    }

    private fun isSkippedByWeeklySchedule(
        node: TaskChainNode,
        serverDayOfWeek: DayOfWeek
    ): Boolean {
        val config = node.config
        if (config is FightConfig && config.useWeeklySchedule) {
            if (config.weeklySchedule[serverDayOfWeek.name] == false) {
                Timber.d("WeeklySchedule: skip node '%s' on %s", node.name, serverDayOfWeek)
                return true
            }
        }
        return false
    }


}

data class TaskChainPlan(
    val nodes: List<TaskChainNode>,
    val params: List<MaaTaskParams>,
    val clientType: String,
    val gamePackageName: String?,
    val launchesGame: Boolean,
    val gameAliveBeforeStart: Boolean? = null,
    /** 预检日志，会话开始后由 Composition 回放。 */
    val logs: List<Pair<UiText, LogLevel>> = emptyList(),
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
        val logs: List<Pair<UiText, LogLevel>> = emptyList(),
    ) : AnalyzeTaskChainResult
}
