package com.aliothmoon.maameow.presentation.view.panel.depot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.DepotMaintainConfig
import com.aliothmoon.maameow.data.model.DepotMaintainPlan
import com.aliothmoon.maameow.data.model.DepotPlanOutcome
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.StageGroup
import com.aliothmoon.maameow.domain.models.DepotActivityPreset
import com.aliothmoon.maameow.domain.models.DualChipConversion
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.INumericField
import com.aliothmoon.maameow.presentation.components.InlineConfirmPanel
import com.aliothmoon.maameow.presentation.components.ItemIcon
import com.aliothmoon.maameow.presentation.view.panel.common.GroupedStageButtonGroup
import com.aliothmoon.maameow.presentation.view.panel.common.ItemButtonGroup
import com.aliothmoon.maameow.presentation.view.panel.common.StageInputField
import com.aliothmoon.maameow.presentation.view.panel.common.StageRow
import com.aliothmoon.maameow.presentation.view.panel.common.stageDisplayName
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility

/** 悬浮窗库存保持常规页：沿用旧版内联 PlanSummary + 展开式 PlanCard，不弹出系统弹窗 */
internal fun LazyListScope.FloatingGeneralTab(
    config: DepotMaintainConfig,
    onConfigChange: (DepotMaintainConfig) -> Unit,
    planOutcomes: List<DepotPlanOutcome>,
    stageGroups: List<StageGroup>,
    stageCodes: List<String>,
    itemIds: List<String>,
    itemNameMap: Map<String, String>,
    inventory: Map<String, Int>,
    inventorySynced: Boolean,
    expandedIndices: MutableList<Int>,
    presetPanelExpanded: Boolean,
    onPresetPanelExpandedChange: (Boolean) -> Unit,
    activityPresets: List<DepotActivityPreset>,
    selectedPreset: DepotPresetSelection?,
    onSelectedPresetChange: (DepotPresetSelection?) -> Unit,
    onApplyPreset: (DepotPresetSelection) -> Boolean,
    showClearConfirm: Boolean,
    onShowClearConfirmChange: (Boolean) -> Unit,
) {
    if (config.plans.isNotEmpty()) {
        item(key = "floating-summary", contentType = "summary") {
            FloatingPlanSummary(
                plans = config.plans,
                outcomes = planOutcomes,
                stageGroups = stageGroups,
                itemNameMap = itemNameMap,
                inventory = inventory,
                inventorySynced = inventorySynced,
                countDualChips = config.countDualChipAsChipPack,
            )
        }
    }
    item(key = "floating-controls", contentType = "controls") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        onConfigChange(config.copy(plans = config.plans + DepotMaintainPlan()))
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.panel_depot_add_plan))
                }
                OutlinedButton(
                    onClick = { expandedIndices.clear() },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(
                        Icons.Default.UnfoldLess,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.panel_depot_collapse_all))
                }
                OutlinedButton(
                    onClick = {
                        onShowClearConfirmChange(true)
                        onPresetPanelExpandedChange(false)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = config.plans.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.panel_depot_clear_plans))
                }
            }
            PresetPicker(
                expanded = presetPanelExpanded,
                onExpandedChange = {
                    onPresetPanelExpandedChange(it)
                    if (it) onShowClearConfirmChange(false)
                },
                activityPresets = activityPresets,
                skipDuringActivity = config.skipDuringActivity,
                selected = selectedPreset,
                onSelectedChange = onSelectedPresetChange,
                onApply = onApplyPreset,
            )
            InlineConfirmPanel(
                visible = showClearConfirm && config.plans.isNotEmpty(),
                message = stringResource(
                    R.string.panel_depot_clear_plans_confirm,
                    config.plans.size,
                ),
                onConfirm = {
                    expandedIndices.clear()
                    onConfigChange(config.copy(plans = emptyList()))
                    onShowClearConfirmChange(false)
                },
                onDismiss = { onShowClearConfirmChange(false) },
            )
        }
    }
    itemsIndexed(
        items = config.plans,
        key = { index, _ -> "floating-plan-$index" },
        contentType = { _, _ -> "plan" },
    ) { index, plan ->
        FloatingPlanCard(
            plan = plan,
            expanded = index in expandedIndices,
            onToggleExpand = {
                if (index in expandedIndices) expandedIndices.remove(index)
                else expandedIndices.add(index)
            },
            customStageCode = config.customStageCode,
            showMedicine = config.useMedicine,
            showStone = config.useStone,
            stageGroups = stageGroups,
            stageCodes = stageCodes,
            itemIds = itemIds,
            itemNameMap = itemNameMap,
            onPlanChange = { updated ->
                onConfigChange(
                    config.copy(
                        plans = config.plans.toMutableList().also { it[index] = updated }
                    )
                )
            },
            onRemove = {
                val remapped = expandedIndices.mapNotNull { i ->
                    when {
                        i < index -> i
                        i == index -> null
                        else -> i - 1
                    }
                }.distinct()
                expandedIndices.clear()
                expandedIndices.addAll(remapped)
                onConfigChange(
                    config.copy(
                        plans = config.plans.toMutableList().also { it.removeAt(index) }
                    )
                )
            },
        )
    }
}

@Composable
private fun FloatingPlanSummary(
    plans: List<DepotMaintainPlan>,
    outcomes: List<DepotPlanOutcome>,
    stageGroups: List<StageGroup>,
    itemNameMap: Map<String, String>,
    inventory: Map<String, Int>,
    inventorySynced: Boolean,
    countDualChips: Boolean,
) {
    val notSelectedLabel = stringResource(R.string.panel_depot_not_selected)
    val runnableCount = outcomes.count { it == DepotPlanOutcome.Runnable }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.panel_depot_summary_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.panel_depot_summary_runnable,
                        runnableCount,
                        plans.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp,
            )

            plans.forEachIndexed { index, plan ->
                val outcome = outcomes[index]
                val muted = outcome != DepotPlanOutcome.Runnable && outcome != DepotPlanOutcome.Enough
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(18.dp),
                    )
                    if (plan.dropId.isNotEmpty()) {
                        ItemIcon(
                            itemId = plan.dropId,
                            modifier = Modifier.size(18.dp),
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = stageDisplayName(plan.stage, stageGroups, notSelectedLabel) +
                            " · " + (itemNameMap[plan.dropId] ?: notSelectedLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    when (outcome) {
                        DepotPlanOutcome.NoItem -> FloatingSummaryTag(
                            text = stringResource(R.string.depot_inventory_maintain_no_item),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        )

                        DepotPlanOutcome.ZeroTarget -> FloatingSummaryTag(
                            text = stringResource(R.string.depot_inventory_maintain_zero_target),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        )

                        DepotPlanOutcome.StageRequired -> FloatingSummaryTag(
                            text = stringResource(R.string.depot_inventory_maintain_stage_required),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        )

                        DepotPlanOutcome.StageClosed -> {
                            val (warningBg, warningFg) = depotWarningColors()
                            FloatingSummaryTag(
                                text = stringResource(R.string.depot_inventory_order_stage_closed),
                                color = warningFg,
                                containerColor = warningBg,
                            )
                        }

                        DepotPlanOutcome.Enough -> FloatingSummaryTag(
                            text = stringResource(R.string.depot_inventory_maintain_enough),
                            color = MaterialTheme.colorScheme.primary,
                        )

                        DepotPlanOutcome.Runnable -> FloatingSummaryTag(
                            text = floatingInventoryText(
                                plan.dropId,
                                inventory,
                                inventorySynced || plan.dropId in ItemHelper.CURRENCY_ITEM_IDS,
                                countDualChips,
                            ) + " / ${plan.dropCount}",
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

private fun floatingInventoryText(
    dropId: String,
    inventory: Map<String, Int>,
    hasBaseline: Boolean,
    countDualChips: Boolean,
): String {
    if (!hasBaseline) return "--"
    val raw = inventory[dropId] ?: 0
    val extra = if (countDualChips) DualChipConversion.extra(dropId) { inventory[it] ?: 0 } else 0
    return if (extra > 0) "$raw (+$extra)" else "$raw"
}

@Composable
private fun FloatingSummaryTag(
    text: String,
    color: Color,
    containerColor: Color = Color.Unspecified,
) {
    if (containerColor != Color.Unspecified) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = containerColor,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun FloatingPlanCard(
    plan: DepotMaintainPlan,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    customStageCode: Boolean,
    showMedicine: Boolean,
    showStone: Boolean,
    stageGroups: List<StageGroup>,
    stageCodes: List<String>,
    itemIds: List<String>,
    itemNameMap: Map<String, String>,
    onPlanChange: (DepotMaintainPlan) -> Unit,
    onRemove: () -> Unit,
) {
    val notSelectedLabel = stringResource(R.string.panel_depot_not_selected)
    val headerText = stageDisplayName(plan.stage, stageGroups, notSelectedLabel) +
        " · ${itemNameMap[plan.dropId] ?: notSelectedLabel} x${plan.dropCount}"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clearAndSetSemantics {
                        contentDescription = headerText
                        role = Role.Button
                        onClick {
                            onToggleExpand()
                            true
                        }
                    }
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (plan.dropId.isNotEmpty()) {
                    ItemIcon(
                        itemId = plan.dropId,
                        modifier = Modifier.size(24.dp),
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            MaaAnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (customStageCode) {
                        StageRow(onRemove = null) {
                            StageInputField(
                                value = plan.stage,
                                onValueChange = { onPlanChange(plan.copy(stage = it)) },
                                label = stringResource(R.string.panel_fight_primary_stage_label),
                                placeholder = stringResource(R.string.panel_fight_primary_stage_placeholder),
                                stageCodes = stageCodes,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        GroupedStageButtonGroup(
                            label = stringResource(R.string.panel_fight_primary_stage_label),
                            selectedValue = plan.stage,
                            stageGroups = stageGroups,
                            emptyLabel = notSelectedLabel,
                            onItemSelected = { onPlanChange(plan.copy(stage = it)) }
                        )
                    }

                    ItemButtonGroup(
                        label = stringResource(R.string.panel_fight_material),
                        selectedValue = plan.dropId,
                        items = itemIds,
                        onItemSelected = { onPlanChange(plan.copy(dropId = it)) },
                        displayMapper = { id -> itemNameMap[id] ?: id }
                    )

                    if (plan.dropId in ItemHelper.CURRENCY_ITEM_IDS) {
                        Text(
                            text = stringResource(R.string.depot_plan_currency_inventory_tip),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    INumericField(
                        value = plan.dropCount,
                        onValueChange = { onPlanChange(plan.copy(dropCount = it)) },
                        label = stringResource(R.string.panel_depot_target_inventory),
                        minimum = 1,
                        maximum = MAX_DEPOT_TARGET_INVENTORY,
                        modifier = Modifier.fillMaxWidth()
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
                                maximum = 999,
                                modifier = Modifier.fillMaxWidth()
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
                                maximum = 999,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    HorizontalDivider()

                    OutlinedButton(
                        onClick = onRemove,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(
                            1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.depot_inventory_plan_remove))
                    }
                }
            }
        }
    }
}
