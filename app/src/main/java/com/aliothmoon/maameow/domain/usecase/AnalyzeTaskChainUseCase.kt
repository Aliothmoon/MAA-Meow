package com.aliothmoon.maameow.domain.usecase

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.model.CollectingPreflightLogSink
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.TaskParamContext
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.data.model.WeeklyScheduled
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.repository.OperBoxRepository
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.data.resource.ServerTimezone
import com.aliothmoon.maameow.domain.models.MallCreditFightAvailability
import com.aliothmoon.maameow.domain.models.PlanSideTask
import com.aliothmoon.maameow.domain.models.ReportOptions
import com.aliothmoon.maameow.domain.service.FightDropsRefresher
import com.aliothmoon.maameow.domain.service.GameMetadataGate
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.domain.models.TaskFallbackChain
import com.aliothmoon.maameow.maa.task.TaskSlot
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.flow.first

class AnalyzeTaskChainUseCase(
    private val taskChainState: TaskChainState,
    private val resourceDataManager: ResourceDataManager,
    private val activityManager: ActivityManager,
    private val depotRepository: DepotRepository,
    private val operBoxRepository: OperBoxRepository,
    private val itemHelper: ItemHelper,
    private val dropsRefresher: FightDropsRefresher,
    private val appSettingsManager: AppSettingsManager,
    private val metadataGate: GameMetadataGate,
    private val relocatePath: (String) -> String = { it },
    private val awaitHotUpdate: suspend (clientType: String) -> Unit = {},
) {
    /**
     * 先等 depot/operBox 分片装载；config 的 toTaskParams 仍是非 suspend
     *
     * @param fromNodeId 从此节点起运行，之前的节点本轮跳过；节点未启用则顺延到其后首个启用的
     */
    suspend operator fun invoke(
        chain: List<TaskChainNode>,
        fromNodeId: String? = null,
    ): AnalyzeTaskChainResult {
        depotRepository.isLoaded.first { it }
        operBoxRepository.isLoaded.first { it }

        // 链级前提看 chainNodes，本轮实际下发的任务看 runNodes
        val chainNodes = chain.filter { it.enabled }.sortedBy { it.order }
        val runNodes = chainNodes.startingFrom(chain, fromNodeId)
        if (runNodes.isEmpty()) {
            return AnalyzeTaskChainResult.Blocked(
                reason = AnalyzeTaskChainFailureReason.NO_TASK_SELECTED,
            )
        }
        val list = getWakeUpClientTypeList(chainNodes)
        if (list.size > 1) {
            return AnalyzeTaskChainResult.Blocked(
                reason = AnalyzeTaskChainFailureReason.CONFLICTING_CLIENT_TYPES,
                clientTypes = list,
            )
        }


        val clientType = taskChainState.clientType
        // 关卡开放、干员名都要等元数据，超时按缺数据降级
        awaitHotUpdate(clientType)
        metadataGate.awaitCurrent()

        // 被跳过的理智作战下次仍要用「上次」关卡，借助战按整条链判断
        val info = MallCreditFightAvailability.resolve(chainNodes, activityManager)

        dropsRefresher.clear()

        val report = ReportOptions.of(
            clientType = clientType,
            reportToPenguin = appSettingsManager.reportToPenguin.value,
            reportToYituliu = appSettingsManager.reportToYituliu.value,
            penguinId = appSettingsManager.penguinId.value,
        )
        val log = CollectingPreflightLogSink()
        val fallbacks = mutableMapOf<TaskSlot, TaskFallbackChain>()
        val sideTasks = mutableListOf<PlanSideTask>()

        val serverDayOfWeek = ServerTimezone.getYjDayOfWeek(clientType)
        val expanded = runNodes.flatMap { node ->
            if ((node.config as? WeeklyScheduled)?.isSkippedOn(serverDayOfWeek) == true) {
                log.append(uiTextOf(R.string.runlog_weekly_schedule_skipped, node.name), LogLevel.INFO)
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
                report = report,
                operBoxUseYituliuApi = appSettingsManager.operBoxUseYituliuApi.value,
                relocatePath = relocatePath,
            )
            val expandedNode = node.config.toTaskParams(ctx).mapIndexed { index, task ->
                task.copy(slot = TaskSlot(node.id, index))
            }
            ctx.fallbacks.forEach { (index, candidates) ->
                fallbacks[TaskSlot(node.id, index)] = candidates
            }
            sideTasks += ctx.sideTasks
            expandedNode
        }
        val params = dropAdjacentDuplicateDepot(expanded)
        val logs = log.entries

        // 只剩旁路任务也算可执行，由启动侧走不起 Core 的轻量路径
        if (params.isEmpty() && sideTasks.isEmpty()) {
            return AnalyzeTaskChainResult.Blocked(
                reason = AnalyzeTaskChainFailureReason.NO_EXECUTABLE_TASKS,
                logs = logs,
            )
        }

        return AnalyzeTaskChainResult.Ready(
            TaskChainPlan(
                nodes = runNodes,
                params = params,
                clientType = clientType,
                gamePackageName = Packages[clientType],
                launchesGame = runNodes
                    .mapNotNull { it.config as? WakeUpConfig }
                    .any { it.startGameEnabled },
                logs = logs,
                fallbacks = fallbacks,
                // 开了两个同类节点会攒出两份，并发进去只有一份能拿到锁
                sideTasks = sideTasks.distinct(),
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

    /** 起点节点已不在链上时返回空，按「未选择任务」拦截 */
    private fun List<TaskChainNode>.startingFrom(
        chain: List<TaskChainNode>,
        fromNodeId: String?,
    ): List<TaskChainNode> {
        fromNodeId ?: return this
        val from = chain.firstOrNull { it.id == fromNodeId } ?: return emptyList()
        return filter { it.order >= from.order }
    }

    private fun getWakeUpClientTypeList(nodes: List<TaskChainNode>): List<String> {
        return nodes.mapNotNull { (it.config as? WakeUpConfig)?.clientType }
            .distinct()
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
    /** 任务位 → 后备候选（按序）；主任务 append 失败时才用到，目前只有库存保持「仅第一个」会产生 */
    val fallbacks: Map<TaskSlot, TaskFallbackChain> = emptyMap(),
    val sideTasks: List<PlanSideTask> = emptyList(),
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
