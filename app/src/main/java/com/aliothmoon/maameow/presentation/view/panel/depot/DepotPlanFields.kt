package com.aliothmoon.maameow.presentation.view.panel.depot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.DepotMaintainPlan
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.StageGroup
import com.aliothmoon.maameow.domain.enums.UiUsageConstants
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.INumericField
import com.aliothmoon.maameow.presentation.view.panel.common.GroupedStageButtonGroup
import com.aliothmoon.maameow.presentation.view.panel.common.StageInputField
import com.aliothmoon.maameow.presentation.view.panel.common.StageRow
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility

/** 目标库存上限，对齐 WPF NumericUpDown 的 Maximum（上游面板另有同名私有常量，这里换个名字避免同包冲突） */
internal const val MAX_DEPOT_TARGET_INVENTORY = 1145141919

/**
 * 库存保持可选的关卡分组：排除「当期剿灭」与「当前/上次」。
 *
 * 剿灭没有指定掉落，当前/上次不知道打哪关，都算不出缺口，选了必被 toTaskParams 拒掉。
 * 对齐上游 RefreshStageList。
 */
@Composable
internal fun rememberDepotStageGroups(activityManager: ActivityManager): List<StageGroup> {
    val activityStages by activityManager.activityStages.collectAsStateWithLifecycle()
    return remember(activityStages) {
        activityManager.getMergedStageGroups()
            .map { group ->
                group.copy(stages = group.stages.filterNot {
                    it.code == "Annihilation" || it.code.isEmpty()
                })
            }
            .filter { it.stages.isNotEmpty() }
    }
}

internal fun List<StageGroup>.allStageCodes(): List<String> =
    flatMap { group -> group.stages.map { it.code } }

/**
 * 库存保持可选的物品列表 = 仓库识别认得出的那批材料 + 三样货币（[ItemHelper.depotTargetItems]），
 * 不是关卡掉落列表：掉落列表里的声望做库存保持没有意义，也识别不出来。
 *
 * 索引还没加载出来时退回内置的那份，别让面板空着。
 */
@Composable
internal fun rememberDepotItemIds(itemHelper: ItemHelper): List<String> {
    val depotTargets by itemHelper.depotTargetItems.collectAsStateWithLifecycle()
    return remember(depotTargets) {
        if (depotTargets.isNotEmpty()) depotTargets.map { it.id } else UiUsageConstants.dropItems
    }
}

/**
 * 一条库存保持计划的可编辑字段：关卡（列表 / 自定义代码）、目标数量，
 * 以及由调用方决定要不要露出来的理智药与源石。
 *
 * 物品**不在这里选**：计划总是从某个物品点开的（物品网格的一格、列表的一行），
 * 物品种类由入口决定。老计划里残留的其它 dropId 照样显示、照样能保存，只是不能换成另一种物品。
 *
 * 任务配置页的展开卡和库存数据页的底部面板共用这一份，免得两边的字段、上下限、
 * 关卡过滤各写一套。
 *
 * 库存数据页不传 [showMedicine] / [showStone]：那一页只改「保到多少、去哪刷」，
 * 药石留在任务配置页配，新建的计划按 [DepotMaintainPlan] 的默认值走。
 *
 * 所有输入都走 [INumericField] / [StageInputField] 的 `commitOnChange`：面板里有「保存」
 * 按钮，只靠失焦提交的话，用户敲完直接点保存，写下去的还是上一次的值。
 *
 * @param showStageListWithCustom true = 选了自定义关卡也保留关卡列表，方便再点回列表里的关卡
 * @param onStageSelected 从关卡列表里点了某一关；默认写回 [onPlanChange]，
 *   库存页用它顺手退出自定义态
 * @param onTargetBlankChange 目标框被清空时回调，调用方据此禁用「保存」
 */
@Composable
internal fun DepotPlanFields(
    plan: DepotMaintainPlan,
    onPlanChange: (DepotMaintainPlan) -> Unit,
    stageGroups: List<StageGroup>,
    stageCodes: List<String>,
    customStageCode: Boolean,
    onCustomStageSelected: () -> Unit,
    modifier: Modifier = Modifier,
    showMedicine: Boolean = false,
    showStone: Boolean = false,
    showStageListWithCustom: Boolean = false,
    onStageSelected: ((String) -> Unit)? = null,
    onTargetBlankChange: ((Boolean) -> Unit)? = null,
) {
    val pickStage: (String) -> Unit = onStageSelected ?: { onPlanChange(plan.copy(stage = it)) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (stageGroups.isNotEmpty() && (showStageListWithCustom || !customStageCode)) {
            GroupedStageButtonGroup(
                label = stringResource(R.string.panel_fight_primary_stage_label),
                selectedValue = plan.stage,
                stageGroups = stageGroups,
                onItemSelected = pickStage,
                customLabel = if (showStageListWithCustom) {
                    stringResource(R.string.depot_inventory_plan_stage_custom)
                } else {
                    null
                },
                customSelected = customStageCode,
                onCustomSelected = onCustomStageSelected,
            )
        }

        // 关卡资源还没加载、或用户选了自定义时，只能手输
        if (customStageCode || stageGroups.isEmpty()) {
            StageRow(onRemove = null) {
                StageInputField(
                    value = plan.stage,
                    onValueChange = { onPlanChange(plan.copy(stage = it)) },
                    label = stringResource(R.string.panel_fight_primary_stage_label),
                    placeholder = stringResource(R.string.panel_fight_primary_stage_placeholder),
                    stageCodes = stageCodes,
                    commitOnChange = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        INumericField(
            value = plan.dropCount,
            onValueChange = { onPlanChange(plan.copy(dropCount = it)) },
            label = stringResource(R.string.panel_depot_target_inventory),
            // dropCount <= 0 会被执行侧按 ERROR 拒掉，UI 不该放行 0
            minimum = 1,
            maximum = MAX_DEPOT_TARGET_INVENTORY,
            commitOnChange = true,
            onBlankChange = onTargetBlankChange,
            modifier = Modifier.fillMaxWidth(),
        )

        // 「按最大倍率刷取」是任务级开关（高级设置），挪到计划里只会让同一次运行
        // 出现 1 倍/最大倍率混刷，没有收益，所以这里不再放开关。

        // 任务级总开关关掉时整行隐藏，勾选值原样留着，重新打开即恢复
        if (showMedicine) {
            CheckBoxWithLabel(
                checked = plan.useMedicine,
                onCheckedChange = { onPlanChange(plan.copy(useMedicine = it)) },
                label = stringResource(R.string.panel_fight_use_medicine),
            )
            MaaAnimatedVisibility(visible = plan.useMedicine) {
                INumericField(
                    value = plan.medicineCount,
                    onValueChange = { onPlanChange(plan.copy(medicineCount = it)) },
                    label = stringResource(R.string.panel_fight_use_medicine_count),
                    minimum = 0,
                    maximum = MAX_MEDICINE_OR_STONE,
                    commitOnChange = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (showStone) {
            CheckBoxWithLabel(
                checked = plan.useStone,
                onCheckedChange = { onPlanChange(plan.copy(useStone = it)) },
                label = stringResource(R.string.panel_stone_use),
            )
            MaaAnimatedVisibility(visible = plan.useStone) {
                INumericField(
                    value = plan.stoneCount,
                    onValueChange = { onPlanChange(plan.copy(stoneCount = it)) },
                    label = stringResource(R.string.panel_depot_stone_count),
                    minimum = 0,
                    maximum = MAX_MEDICINE_OR_STONE,
                    commitOnChange = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 理智药 / 源石单次上限，与任务配置页同一个数 */
private const val MAX_MEDICINE_OR_STONE = 999
