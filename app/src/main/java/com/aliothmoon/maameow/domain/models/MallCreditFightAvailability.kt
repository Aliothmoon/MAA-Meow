package com.aliothmoon.maameow.domain.models

import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.resource.ActivityManager

data class MallCreditFightAvailability(
    val isAvailable: Boolean,
    val blockingTaskName: String? = null,
    val blockingTaskOrder: Int? = null,
    val blockingStageIndex: Int? = null,
) {
    val warningMessage: String?
        get() = if (isAvailable) {
            null
        } else {
            "存在 ｢理智作战｣ 任务关卡列表中有 ｢当前/上次｣, ｢信用收支｣ 任务不再借助战打一把 OF-1. " +
                "任务名: ${blockingTaskName.orEmpty()} (序号${blockingTaskOrder ?: -1}), 关卡序号 ${blockingStageIndex ?: 1}"
        }
}

fun resolveMallCreditFightAvailability(
    nodes: List<TaskChainNode>,
    activityManager: ActivityManager,
): MallCreditFightAvailability {
    val blockingAvailability = nodes
        .asSequence()
        .filter { it.enabled }
        .sortedBy { it.order }
        .firstNotNullOfOrNull { node ->
            val fightConfig = node.config as? FightConfig ?: return@firstNotNullOfOrNull null
            val blockingStageIndex = findBlockingStageIndex(fightConfig, activityManager)
                ?: return@firstNotNullOfOrNull null
            MallCreditFightAvailability(
                isAvailable = false,
                blockingTaskName = node.name,
                blockingTaskOrder = node.order + 1,
                blockingStageIndex = blockingStageIndex,
            )
        }

    return blockingAvailability ?: MallCreditFightAvailability(isAvailable = true)
}

private fun findBlockingStageIndex(
    config: FightConfig,
    activityManager: ActivityManager,
): Int? {
    if (config.getActiveStage(activityManager).isNotBlank()) {
        return null
    }

    // 与 getActiveStage 的候选集保持一致：不启用备选时计划仅含首选关卡（对齐 WPF UseAlternateStage 关闭时 StagePlan 仅保留首项）
    val stageValues = if (config.useAlternateStage) {
        listOf(config.stage1) + config.alternateStages
    } else {
        listOf(config.stage1)
    }
    val firstBlankStageIndex = stageValues.indexOfFirst { it.isBlank() }
    return if (firstBlankStageIndex >= 0) {
        firstBlankStageIndex + 1
    } else {
        1
    }
}
