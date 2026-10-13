package com.aliothmoon.maameow.domain.models

import com.aliothmoon.maameow.data.model.DepotMaintainPlan
import com.aliothmoon.maameow.data.model.activity.ActivityStage

/**
 * 库存保持「当前活动」预设：一个进行中的活动，掉落为已知物品的关卡各占一条计划
 *
 * 对齐上游 RefreshActivityPresetList + AddActivityPresetPlan：开放窗口与名称取活动组级 Activity，
 * 关卡级覆盖只影响单关开放，不决定活动是否列出；尚未开放的后续关卡照样导入，运行时按关卡未开放跳过
 *
 * @param key 活动标识，即 sideStoryStage 的键
 */
data class DepotActivityPreset(
    val key: String,
    val name: String,
    val plans: List<DepotMaintainPlan>,
) {
    companion object {
        /** 统一目标数量，导入后按个人兑换计划调整，对齐上游 ActivityPresetDefaultCount */
        const val DEFAULT_COUNT = 200

        /**
         * 列出 [nowMillis] 时刻进行中的活动预设
         *
         * 活动起止不会触发数据刷新，调用方须按需传入当下时刻
         * 掉落非已知物品的是文案型 Drop，生成不了计划；一关都没有的活动不列出
         */
        fun listOpen(
            stages: List<ActivityStage>,
            knownItemIds: Set<String>,
            nowMillis: Long,
        ): List<DepotActivityPreset> = stages
            .groupBy { it.activityKey }
            .mapNotNull { (key, group) ->
                val activity = group.first().groupActivity ?: return@mapNotNull null
                if (!activity.isOpenAt(nowMillis)) return@mapNotNull null
                val plans = group.mapNotNull { stage ->
                    val drop = stage.drop?.takeIf { it in knownItemIds } ?: return@mapNotNull null
                    DepotMaintainPlan(stage = stage.value, dropId = drop, dropCount = DEFAULT_COUNT)
                }
                if (plans.isEmpty()) return@mapNotNull null
                DepotActivityPreset(key = key, name = activity.name.ifBlank { key }, plans = plans)
            }
    }
}
