package com.aliothmoon.maameow.maa.task

/**
 * MaaCore 任务类型枚举
 *
 * 与 MaaCore C++ 接口对应，value 为传递给 AsstAppendTask 的 type 参数
 * 参考: MaaWpfGui/Services/MaaService.cs - AsstTaskType
 */
enum class MaaTaskType(val value: String) {
    // 基础任务
    START_UP("StartUp"),
    CLOSE_DOWN("CloseDown"),

    // 日常任务
    FIGHT("Fight"),
    RECRUIT("Recruit"),
    INFRAST("Infrast"),
    MALL("Mall"),
    AWARD("Award"),

    // 特殊任务
    ROGUELIKE("Roguelike"),
    RECLAMATION("Reclamation"),

    // 辅助任务
    COPILOT("Copilot"),
    SSS_COPILOT("SSSCopilot"),
    PARADOX_COPILOT("ParadoxCopilot"),

    // 识别任务
    DEPOT("Depot"),
    OPER_BOX("OperBox");

    companion object {
        fun fromValue(value: String): MaaTaskType? =
            entries.find { it.value == value }
    }
}
