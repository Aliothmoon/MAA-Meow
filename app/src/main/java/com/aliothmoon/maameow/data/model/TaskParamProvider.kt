package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.utils.i18n.UiText
import kotlinx.serialization.Serializable

@Serializable
sealed interface TaskParamProvider {
    /**
     * 展开为 MaaCore 任务参数。
     *
     * 返回 List 而非单个：绝大多数任务一对一，但容器型任务（如库存保持）
     * 会展开成多个 MaaCore 任务，且可能因逐条子计划无效而产出诊断日志。
     */
    fun toTaskParams(ctx: TaskParamContext): TaskParamResult
}

/**
 * 单个任务节点的展开结果。
 *
 * [logs] 让「某条子计划为何没产出任务」能被如实告知用户 ——
 * 容器型任务会逐条校验子计划，只返回 params 的话这些原因就永久丢失了。
 */
data class TaskParamResult(
    val params: List<MaaTaskParams>,
    val logs: List<Pair<UiText, LogLevel>> = emptyList(),
    /**
     * 本节点展开时同时到期干员箱+仓库（更新数据任务）。
     * 仅作规划标记：启动成功后 arm Collector，两侧识别成功再报 DepotOperBox。
     */
    val unlockDoubleSync: Boolean = false,
)
