package com.aliothmoon.maameow.domain.enums

/**
 * 黑流树海刷开局的期望奖励 - 对齐 WPF RoguelikeBlackFlowStartReward 枚举
 *
 * [bit] 为 core 的 blackflow_start_rewards 位掩码，[id] 为 blackflow_start_reward_priority 的取值，
 * [serpent] 的六项由襁褓羽蛇带来
 */
enum class RoguelikeBlackFlowStartReward(val bit: Int, val id: String, val serpent: Boolean = false) {
    UnnumberedItem(1 shl 0, "unnumbered_item"),
    SurveyAdvance(1 shl 1, "survey_advance"),
    SpaceLease(1 shl 2, "space_lease"),
    RegressionCompensation(1 shl 3, "regression_compensation"),
    ForestRide(1 shl 4, "forest_ride"),
    NestParasitism(1 shl 5, "nest_parasitism"),
    Pegasus(1 shl 6, "pegasus", serpent = true),
    Hippogriff(1 shl 7, "hippogriff", serpent = true),
    Dragon(1 shl 8, "dragon", serpent = true),
    Hydra(1 shl 9, "hydra", serpent = true),
    BaiZe(1 shl 10, "bai_ze", serpent = true),
    GoldenCrow(1 shl 11, "golden_crow", serpent = true);

    companion object {
        /** 凹实托邦时支援的默认选取顺序：襁褓天马最前，其余普通奖励按日常顺序，对齐 WPF RoguelikeTask */
        val DEFAULT_PRIORITY: List<RoguelikeBlackFlowStartReward> = listOf(
            Pegasus, SpaceLease, ForestRide, UnnumberedItem, RegressionCompensation, SurveyAdvance,
            NestParasitism, Hippogriff, Dragon, Hydra, BaiZe, GoldenCrow,
        )

        /** 整理成十二种各一次，缺的按默认顺序补在后面 */
        fun normalizePriority(priority: List<RoguelikeBlackFlowStartReward>): List<RoguelikeBlackFlowStartReward> {
            val distinct = priority.distinct()
            return distinct + DEFAULT_PRIORITY.filterNot { it in distinct }
        }
    }
}
