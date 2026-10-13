package com.aliothmoon.maameow.presentation.view.panel.depot

import androidx.annotation.StringRes
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.DepotMaintainPlan

/**
 * 库存保持的 UI 内置预设
 *
 * 数据与 MAA WPF DepotMaintainTaskUserControlModel.PresetData 保持一致
 */
internal enum class DepotMaintainPreset(
    @get:StringRes val labelRes: Int,
    val plans: List<DepotMaintainPlan>,
) {
    CHIP_1(
        labelRes = R.string.panel_depot_preset_chip_1,
        plans = buildPresetPlans(
            defaultCount = 20,
            "PR-A-1" to listOf("3261", "3231"),
            "PR-B-1" to listOf("3251", "3241"),
            "PR-C-1" to listOf("3211", "3271"),
            "PR-D-1" to listOf("3221", "3281"),
        ),
    ),
    CHIP_2(
        labelRes = R.string.panel_depot_preset_chip_2,
        plans = buildPresetPlans(
            defaultCount = 32,
            "PR-A-2" to listOf("3262", "3232"),
            "PR-B-2" to listOf("3252", "3242"),
            "PR-C-2" to listOf("3212", "3272"),
            "PR-D-2" to listOf("3222", "3282"),
        ),
    ),
    LMD(
        labelRes = R.string.panel_depot_preset_lmd,
        plans = buildPresetPlans(
            defaultCount = 5_000_000,
            "CE-6" to listOf("4001"),
        ),
    ),
    PURCHASE_CERTIFICATE(
        labelRes = R.string.panel_depot_preset_certificate,
        plans = buildPresetPlans(
            defaultCount = 8_000,
            "AP-5" to listOf("4006"),
        ),
    ),
    SKILL_SUMMARY_3(
        labelRes = R.string.panel_depot_preset_skill_summary,
        plans = buildPresetPlans(
            defaultCount = 400,
            "CA-5" to listOf("3303"),
        ),
    ),
}

/** 预设菜单的选中项；活动只记 key，确认时按当下重算，避免导入展开后已失效的活动 */
internal sealed interface DepotPresetSelection {
    data class Builtin(val preset: DepotMaintainPreset) : DepotPresetSelection
    data class Activity(val key: String) : DepotPresetSelection
}

internal fun appendDepotMaintainPreset(
    existingPlans: List<DepotMaintainPlan>,
    preset: DepotMaintainPreset,
): List<DepotMaintainPlan> = existingPlans + preset.plans

private fun buildPresetPlans(
    defaultCount: Int,
    vararg stages: Pair<String, List<String>>,
): List<DepotMaintainPlan> = stages.flatMap { (stage, dropIds) ->
    dropIds.map { dropId ->
        DepotMaintainPlan(
            stage = stage,
            dropId = dropId,
            dropCount = defaultCount,
        )
    }
}
