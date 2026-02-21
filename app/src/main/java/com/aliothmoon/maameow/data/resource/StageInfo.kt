package com.aliothmoon.maameow.data.resource

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 关卡信息（UI 使用）
 */
data class StageInfo(
    val stageId: String,       // 内部 ID（如 "a001_01_perm"）
    val code: String,          // 显示代码（如 "GT-1"）
    val apCost: Int = 0,       // 理智消耗
    val openDays: List<DayOfWeek> = emptyList(),  // 开放日期（空表示每天开放）
    val category: StageCategory = StageCategory.OTHER,  // 关卡分类
    val tip: String = "",      // 关卡提示信息（迁移自 WPF TipKey）
    val dropGroups: List<List<String>> = emptyList()  // 芯片本掉落组 (迁移自 WPF DropGroups)
) {
    /**
     * 获取用于 UI 显示的名称
     * 迁移自 WPF zh-cn.xaml 第740-770行的本地化映射
     */
    val displayName: String
        get() = STAGE_DISPLAY_NAMES[code] ?: code

    /**
     * 检查关卡在指定日期是否开放
     */
    fun isOpenOn(dayOfWeek: DayOfWeek): Boolean {
        return openDays.isEmpty() || dayOfWeek in openDays
    }

    /**
     * 检查关卡今天是否开放
     */
    fun isOpenToday(): Boolean {
        return isOpenOn(DayOfWeek.of(LocalDate.now().dayOfWeek.value))
    }

    companion object {
        /**
         * 关卡代码到显示名称的映射
         */
        private val STAGE_DISPLAY_NAMES = mapOf(
            // 主线关卡
            "1-7" to "1-7",
            "R8-11" to "R8-11",
            "12-17-HARD" to "12-17-HARD",

            // 资源本
            "CE-6" to "龙门币-6/5",
            "AP-5" to "红票-5",
            "CA-5" to "技能-5",
            "LS-6" to "经验-6/5",
            "SK-5" to "碳-5",

            // 芯片本
            "PR-A-1" to "奶/盾芯片",
            "PR-A-2" to "奶/盾芯片组",
            "PR-B-1" to "术/狙芯片",
            "PR-B-2" to "术/狙芯片组",
            "PR-C-1" to "先/辅芯片",
            "PR-C-2" to "先/辅芯片组",
            "PR-D-1" to "近/特芯片",
            "PR-D-2" to "近/特芯片组",

            // 剿灭模式
            "Annihilation" to "当期剿灭",
            "Chernobog@Annihilation" to "切尔诺伯格",
            "Lungmen@Annihilation" to "龙门外环",
            "LungmenOutskirts@Annihilation" to "龙门外环",
            "LungmenDowntown@Annihilation" to "龙门市区"
        )

        /**
         * 关卡提示信息映射
         * 迁移自 WPF Localizations/zh-cn.xaml 第762-770行
         */
        val STAGE_TIPS = mapOf(
            "CE-6" to "CE-6: 龙门币",
            "AP-5" to "AP-5: 红票",
            "CA-5" to "CA-5: 技能",
            "LS-6" to "LS-6: 经验",
            "SK-5" to "SK-5: 碳",
            "PR-A-1" to "PR-A-1/2: 奶&盾芯片",
            "PR-A-2" to "PR-A-1/2: 奶&盾芯片",
            "PR-B-1" to "PR-B-1/2: 术&狙芯片",
            "PR-B-2" to "PR-B-1/2: 术&狙芯片",
            "PR-C-1" to "PR-C-1/2: 先&辅芯片",
            "PR-C-2" to "PR-C-1/2: 先&辅芯片",
            "PR-D-1" to "PR-D-1/2: 近&特芯片",
            "PR-D-2" to "PR-D-1/2: 近&特芯片"
        )
    }
}