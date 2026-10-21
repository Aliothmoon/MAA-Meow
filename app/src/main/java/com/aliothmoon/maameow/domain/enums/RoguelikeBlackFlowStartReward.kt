package com.aliothmoon.maameow.domain.enums

/**
 * 黑流树海刷开局的期望奖励 - 对齐 WPF RoguelikeBlackFlowStartReward 枚举
 *
 * [bit] 为 core 的 blackflow_start_rewards 位掩码，[serpent] 的六项由襁褓羽蛇带来
 */
enum class RoguelikeBlackFlowStartReward(val bit: Int, val serpent: Boolean = false) {
    UnnumberedItem(1 shl 0),
    SurveyAdvance(1 shl 1),
    SpaceLease(1 shl 2),
    RegressionCompensation(1 shl 3),
    ForestRide(1 shl 4),
    NestParasitism(1 shl 5),
    Pegasus(1 shl 6, serpent = true),
    Hippogriff(1 shl 7, serpent = true),
    Dragon(1 shl 8, serpent = true),
    Hydra(1 shl 9, serpent = true),
    BaiZe(1 shl 10, serpent = true),
    GoldenCrow(1 shl 11, serpent = true);
}
