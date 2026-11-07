package com.aliothmoon.maameow.data.resource

/**
 * 关卡分类
 *
 * displayName 已删：原硬编码中文名（"主线"/"龙门币"…）全仓无任何显示消费点，
 * 属死代码。将来若要在 UI 展示分类名，按 stage_name_* 的先例建 string 资源。
 */
enum class StageCategory {
    MAIN,           // 主线关卡
    RESOURCE_CE,    // CE 系列（龙门币）
    RESOURCE_LS,    // LS 系列（作战记录）
    RESOURCE_CA,    // CA 系列（技巧概要）
    RESOURCE_AP,    // AP 系列（采购凭证）
    RESOURCE_SK,    // SK 系列（碳素）
    CHIP_PR,        // PR 系列（芯片本）
    ANNIHILATION,   // 剿灭模式
    EVENT,          // 活动关卡
    OTHER;          // 其他

    companion object {
        val MAIN_REG = Regex("^\\d+-\\d+$")
        val EVENT_REG = Regex("^[A-Z]{2}-\\d+$")
        fun fromCode(code: String): StageCategory {
            return when {
                code.startsWith("CE-") -> RESOURCE_CE
                code.startsWith("LS-") -> RESOURCE_LS
                code.startsWith("CA-") -> RESOURCE_CA
                code.startsWith("AP-") -> RESOURCE_AP
                code.startsWith("SK-") -> RESOURCE_SK
                code.startsWith("PR-") -> CHIP_PR
                code == "Annihilation" || code.contains("@Annihilation") -> ANNIHILATION
                code.matches(MAIN_REG) -> MAIN  // 如 1-7, 10-17
                code.matches(EVENT_REG) -> EVENT  // 如 SN-10
                else -> OTHER
            }
        }
    }
}
