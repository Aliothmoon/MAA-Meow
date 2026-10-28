package com.aliothmoon.maameow.domain.enums

/**
 * 旧版刷襁褓动物的目标品种，只为读旧配置保留，迁移到 [RoguelikeBlackFlowStartAnimal]
 */
enum class RoguelikeBlackFlowCultivationTarget(val startAnimal: RoguelikeBlackFlowStartAnimal) {
    Cat(RoguelikeBlackFlowStartAnimal.Cat),
    FeatheredSerpent(RoguelikeBlackFlowStartAnimal.FeatheredSerpent),
    Dog(RoguelikeBlackFlowStartAnimal.Dog),
    Cerberus(RoguelikeBlackFlowStartAnimal.Cerberus);
}
