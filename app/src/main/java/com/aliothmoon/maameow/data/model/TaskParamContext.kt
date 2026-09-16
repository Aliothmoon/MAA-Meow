package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.repository.OperBoxRepository
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.models.PlanSideTask
import com.aliothmoon.maameow.domain.models.ReportOptions
import com.aliothmoon.maameow.domain.models.TaskFallbackChain
import com.aliothmoon.maameow.domain.service.FightDropsRefresher
import com.aliothmoon.maameow.utils.i18n.UiText

/**
 * 展开环境：只读世界状态 + 本趟 [appendLog] / [FightDropsRefresher.stage]。
 * 非值对象；配置类不得反向抓依赖。
 */
class TaskParamContext(
    val node: TaskChainNode,
    val clientType: String,
    val chainAllowsCreditFight: Boolean,
    val activityManager: ActivityManager,
    val depotRepository: DepotRepository,
    val operBoxRepository: OperBoxRepository,
    val itemHelper: ItemHelper,
    val resourceDataManager: ResourceDataManager,
    val dropsRefresher: FightDropsRefresher,
    val logSink: PreflightLogSink,
    val report: ReportOptions = ReportOptions.DEFAULT,
    /** 干员识别改走一图流，不再下发 Core 任务 */
    val operBoxUseYituliuApi: Boolean = false,
    /** App 侧绝对路径映射到 core 读的路径（独立目录模式），见 MaaPathConfig.toCorePath */
    val relocatePath: (String) -> String = { it },
) {
    private val _fallbacks = mutableMapOf<Int, TaskFallbackChain>()

    /** key 为该 node 展开列表内的下标，Analyze 注入 slot 时转成 TaskSlot */
    val fallbacks: Map<Int, TaskFallbackChain> get() = _fallbacks

    /** 登记某个任务位的后备链；主任务 append 被 core 拒绝时按序尝试 */
    fun registerFallbacks(listIndex: Int, chain: TaskFallbackChain) {
        if (!chain.isEmpty) _fallbacks[listIndex] = chain
    }

    private val _sideTasks = mutableListOf<PlanSideTask>()

    /** Analyze 汇总到计划里并去重 */
    val sideTasks: List<PlanSideTask> get() = _sideTasks

    fun registerSideTask(task: PlanSideTask) {
        _sideTasks += task
    }

    fun appendLog(text: UiText, level: LogLevel = LogLevel.INFO) {
        logSink.append(text, level)
    }
}
