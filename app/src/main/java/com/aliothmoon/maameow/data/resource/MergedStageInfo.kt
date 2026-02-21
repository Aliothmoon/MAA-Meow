package com.aliothmoon.maameow.data.resource

import com.aliothmoon.maameow.data.model.activity.StageActivityInfo
import java.time.DayOfWeek

/**
 * 合并关卡信息（用于统一查找）
 */
data class MergedStageInfo(
    val code: String,                               // 关卡代码 (value)
    val displayName: String,                        // 显示名称
    val openDays: List<DayOfWeek> = emptyList(),    // 空 = 每天开放
    val activity: StageActivityInfo? = null,        // 活动信息
    val drop: String? = null,                       // 掉落物品 ID
    val tip: String = ""                            // 关卡提示信息
) {
    /**
     * 检查关卡在指定日期是否开放
     * 迁移自 WPF StageManager.IsStageOpen
     */
    fun isStageOpen(dayOfWeek: DayOfWeek): Boolean {
        activity?.let {
            if (it.isOpen) return true                    // 活动进行中 → 开放
            if (!it.isResourceCollection) return false    // 非资源收集的过期活动 → 关闭
        }
        // 永久关卡按 openDays 判断
        return openDays.isEmpty() || dayOfWeek in openDays
    }
}