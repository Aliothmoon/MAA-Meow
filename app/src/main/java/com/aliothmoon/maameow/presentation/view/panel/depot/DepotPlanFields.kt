package com.aliothmoon.maameow.presentation.view.panel.depot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentDataType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDataType
import androidx.compose.ui.semantics.semantics
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

/** 目标库存上限，对齐 WPF NumericUpDown 的 Maximum */
internal const val MAX_DEPOT_TARGET_INVENTORY = 1145141919

/** 库存保持可选关卡分组：排除当期剿灭与空串（当前/上次） */
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

/** 库存保持排除项：赤金、家具零件、合成玉、声望 */
private val DEPOT_EXCLUDED_ITEM_IDS = setOf("3003", "3401", "4003", "5001")

/** 库存保持可选物品列表（沿用 dropItems 可掉落口径，排除非刷取杂项），索引未就绪时回退内置表 */
@Composable
internal fun rememberDepotItemIds(itemHelper: ItemHelper): List<String> {
    val dropItems by itemHelper.dropItems.collectAsStateWithLifecycle()
    return remember(dropItems) {
        if (dropItems.isNotEmpty()) {
            dropItems.filterNot { it.id in DEPOT_EXCLUDED_ITEM_IDS }.map { it.id }
        } else {
            UiUsageConstants.dropItems
        }
    }
}

/** 单条库存保持计划的可编辑字段（关卡、目标数量、理智药与源石） */
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
    onMedicineBlankChange: ((Boolean) -> Unit)? = null,
    onStoneBlankChange: ((Boolean) -> Unit)? = null,
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
                emptyLabel = stringResource(R.string.panel_depot_not_selected),
            )
        }

        if (customStageCode || stageGroups.isEmpty()) {
            StageRow(onRemove = null) {
                StageInputField(
                    value = plan.stage,
                    onValueChange = { onPlanChange(plan.copy(stage = it)) },
                    label = stringResource(R.string.panel_fight_primary_stage_label),
                    placeholder = stringResource(R.string.panel_fight_primary_stage_placeholder),
                    stageCodes = stageCodes,
                    commitOnChange = true,
                    modifier = Modifier.weight(1f).depotInputField(),
                )
            }
        }

        INumericField(
            value = plan.dropCount,
            onValueChange = { onPlanChange(plan.copy(dropCount = it)) },
            label = stringResource(R.string.panel_depot_target_inventory),
            minimum = 1,
            maximum = MAX_DEPOT_TARGET_INVENTORY,
            commitOnChange = true,
            onBlankChange = onTargetBlankChange,
            modifier = Modifier.fillMaxWidth().depotInputField(),
        )

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
                    onBlankChange = onMedicineBlankChange,
                    modifier = Modifier.fillMaxWidth().depotInputField(),
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
                    onBlankChange = onStoneBlankChange,
                    modifier = Modifier.fillMaxWidth().depotInputField(),
                )
            }
        }
    }
}

private const val MAX_MEDICINE_OR_STONE = 999

/**
 * 禁用关卡与数字框的系统密码自动填充，并在聚焦时上报位置给抽屉，
 * 让抽屉仅在输入框会被键盘遮挡时按需上移到键盘紧上方
 */
@Composable
private fun Modifier.depotInputField(): Modifier {
    val imeController = LocalDepotSheetImeController.current
    val fieldToken = remember { Any() }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(imeController, fieldToken) {
        onDispose {
            imeController?.clearField(fieldToken)
        }
    }
    return this
        .semantics { contentDataType = ContentDataType.None }
        .onGloballyPositioned {
            coords = it
            if (focused) imeController?.updateFocusedCoords(fieldToken, it)
        }
        .onFocusChanged { state ->
            focused = state.hasFocus
            if (state.hasFocus) {
                imeController?.focusField(fieldToken, coords)
            } else {
                imeController?.clearField(fieldToken)
            }
        }
}
