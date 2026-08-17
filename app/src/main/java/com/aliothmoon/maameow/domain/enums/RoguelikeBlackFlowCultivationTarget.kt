package com.aliothmoon.maameow.domain.enums

/**
 * 黑流树海刷襁褓动物的目标品种 - 对齐 WPF RoguelikeBlackFlowCultivationTarget 枚举
 *
 * [value] 为 core 的 blackflow_cultivation_target 取值
 */
enum class RoguelikeBlackFlowCultivationTarget(val value: String) {
    /** 襁褓中的猫 */
    Cat("swaddled_cat"),

    /** 襁褓羽蛇 */
    FeatheredSerpent("swaddled_feathered_serpent"),

    /** 襁褓中的狗 */
    Dog("swaddled_dog"),

    /** 襁褓三头犬 */
    Cerberus("swaddled_cerberus");
}
