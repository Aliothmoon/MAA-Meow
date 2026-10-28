package com.aliothmoon.maameow.domain.enums

/**
 * 黑流树海刷开局要求的襁褓动物 - 对齐 WPF RoguelikeBlackFlowStartAnimal 枚举
 *
 * [value] 为 core 的 blackflow_start_animals 取值，[None] 不下发
 */
enum class RoguelikeBlackFlowStartAnimal(val value: String) {
    None(""),
    Cat("swaddled_cat"),
    Dog("swaddled_dog"),
    Cerberus("swaddled_cerberus"),
    FeatheredSerpent("swaddled_feathered_serpent");
}
