package com.aliothmoon.maameow.data.resource

import java.time.DayOfWeek

/**
 * 资源本开放日期配置
 * see StageManager.AddPermanentStages
 */
object StageOpenDays {
    private val MONDAY = DayOfWeek.MONDAY
    private val TUESDAY = DayOfWeek.TUESDAY
    private val WEDNESDAY = DayOfWeek.WEDNESDAY
    private val THURSDAY = DayOfWeek.THURSDAY
    private val FRIDAY = DayOfWeek.FRIDAY
    private val SATURDAY = DayOfWeek.SATURDAY
    private val SUNDAY = DayOfWeek.SUNDAY

    /**
     * 资源本开放日期映射
     */
    val RESOURCE_OPEN_DAYS: Map<String, List<DayOfWeek>> = mapOf(
        // 龙门币 - CE: 周二、四、六、日
        "CE" to listOf(TUESDAY, THURSDAY, SATURDAY, SUNDAY),
        // 作战记录 - LS: 每天
        "LS" to emptyList(),
        // 技巧概要 - CA: 周二、三、五、日
        "CA" to listOf(TUESDAY, WEDNESDAY, FRIDAY, SUNDAY),
        // 采购凭证/芯片材料 - AP: 周一、四、六、日
        "AP" to listOf(MONDAY, THURSDAY, SATURDAY, SUNDAY),
        // 碳素 - SK: 周一、三、五、六
        "SK" to listOf(MONDAY, WEDNESDAY, FRIDAY, SATURDAY)
    )

    /**
     * 芯片本开放日期映射
     */
    val CHIP_OPEN_DAYS: Map<String, List<DayOfWeek>> = mapOf(
        // PR-A: 重装/医疗 - 周一、四、五、日
        "PR-A" to listOf(MONDAY, THURSDAY, FRIDAY, SUNDAY),
        // PR-B: 狙击/术师 - 周一、二、五、六
        "PR-B" to listOf(MONDAY, TUESDAY, FRIDAY, SATURDAY),
        // PR-C: 先锋/辅助 - 周三、四、六、日
        "PR-C" to listOf(WEDNESDAY, THURSDAY, SATURDAY, SUNDAY),
        // PR-D: 近卫/特种 - 周二、三、六、日
        "PR-D" to listOf(TUESDAY, WEDNESDAY, SATURDAY, SUNDAY)
    )

    /**
     * 根据关卡代码获取开放日期
     */
    fun getOpenDays(code: String): List<DayOfWeek> {
        // 检查资源本
        for ((prefix, days) in RESOURCE_OPEN_DAYS) {
            if (code.startsWith("$prefix-")) {
                return days
            }
        }
        // 检查芯片本
        for ((prefix, days) in CHIP_OPEN_DAYS) {
            if (code.startsWith("$prefix-")) {
                return days
            }
        }
        // 默认每天开放
        return emptyList()
    }
}