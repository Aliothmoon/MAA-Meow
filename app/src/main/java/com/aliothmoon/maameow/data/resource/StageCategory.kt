package com.aliothmoon.maameow.data.resource

/**
 * 关卡分类
 */
enum class StageCategory(val displayName: String) {
    MAIN("主线"),              // 主线关卡
    RESOURCE_CE("龙门币"),     // CE 系列
    RESOURCE_LS("作战记录"),   // LS 系列
    RESOURCE_CA("技巧概要"),   // CA 系列
    RESOURCE_AP("采购凭证"),   // AP 系列（芯片材料）
    RESOURCE_SK("碳素"),       // SK 系列
    CHIP_PR("芯片"),           // PR 系列（芯片本）
    ANNIHILATION("剿灭"),      // 剿灭模式
    EVENT("活动"),             // 活动关卡
    OTHER("其他");             // 其他

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
