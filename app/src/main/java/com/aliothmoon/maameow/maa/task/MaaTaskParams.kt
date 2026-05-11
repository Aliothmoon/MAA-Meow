package com.aliothmoon.maameow.maa.task

/**
 * MaaCore#AsstAppendTask
 *
 * - type: 任务类型字符串
 * - params: 任务参数 JSON 字符串
 * - nodeId: 关联的 TaskChainNode.id;由 AnalyzeTaskChainUseCase 注入,
 *           供回调阶段(如 Infrast 计划自动切换)反查源节点。Copilot 等链外路径保持 null
 */
data class MaaTaskParams(
    val type: MaaTaskType,
    val params: String,
    val nodeId: String? = null,
)
