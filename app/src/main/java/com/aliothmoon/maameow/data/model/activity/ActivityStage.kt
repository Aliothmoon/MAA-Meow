package com.aliothmoon.maameow.data.model.activity

/**
 * 活动关卡（UI 使用）
 * 迁移自 WPF ActivityStage
 */
data class ActivityStage(
    val display: String,           // 显示名称 (如 "ME-8")
    val value: String,             // 关卡代码 (如 "ME-8")
    val drop: String? = null,      // 掉落物品 ID
    val activity: StageActivityInfo? = null,  // 所属活动信息
    val activityKey: String = ""   // 活动标识
) {
    /**
     * 关卡是否可用（活动进行中）
     */
    val isAvailable: Boolean
        get() = activity?.isOpen ?: true

    companion object {
        /**
         * 从原始数据创建
         */
        fun fromRaw(
            raw: ActivityStageRaw,
            activity: StageActivityInfo?,
            activityKey: String
        ): ActivityStage {
            return ActivityStage(
                display = raw.display,
                value = raw.value,
                drop = raw.drop,
                activity = activity,
                activityKey = activityKey
            )
        }
    }
}