package com.aliothmoon.maameow.domain.models

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.data.model.MallConfig
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import timber.log.Timber


class MallCreditFightAvailability private constructor(
    val isAvailable: Boolean,
    val message: UiText? = null,
) {
    companion object {
        fun resolve(
            nodes: List<TaskChainNode>,
            manager: ActivityManager,
        ): MallCreditFightAvailability {
            val enabled = nodes.filter { it.enabled }.sortedBy { it.order }
            val result = enabled.firstNotNullOfOrNull {
                val config = it.config as? FightConfig ?: return@firstNotNullOfOrNull null
                val stageIndex =
                    findBlockingStageIndex(config, manager) ?: return@firstNotNullOfOrNull null
                val order = it.order + 1
                MallCreditFightAvailability(
                    isAvailable = false,
                    message = uiTextOf(
                        R.string.mall_credit_fight_blocked_by_stage,
                        it.name,
                        order,
                        stageIndex,
                    ),
                )
            } ?: MallCreditFightAvailability(isAvailable = true)

            if (!result.isAvailable && enabled.any { (it.config as? MallConfig)?.creditFight == true }) {
                val args = (result.message as? UiText.Resource)?.args.orEmpty()
                Timber.w(
                    "Credit fight disabled because a fight task has no resolvable active stage. task=%s order=%s stageIndex=%s",
                    args.getOrNull(0) ?: "unknown",
                    args.getOrNull(1) ?: -1,
                    args.getOrNull(2) ?: -1,
                )
            }
            return result
        }

        private fun findBlockingStageIndex(
            config: FightConfig,
            activityManager: ActivityManager,
        ): Int? {
            if (config.getActiveStage(activityManager).isNotBlank()) {
                return null
            }
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
    }
}
