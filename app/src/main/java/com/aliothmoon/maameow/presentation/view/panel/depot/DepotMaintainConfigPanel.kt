package com.aliothmoon.maameow.presentation.view.panel.depot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.DepotMaintainConfig
import com.aliothmoon.maameow.data.model.DepotMaintainPlan
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.StageGroup
import com.aliothmoon.maameow.domain.enums.UiUsageConstants
import com.aliothmoon.maameow.presentation.components.CheckBoxWithExpandableTip
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.INumericField
import com.aliothmoon.maameow.presentation.view.panel.common.GroupedStageButtonGroup
import com.aliothmoon.maameow.presentation.view.panel.common.ItemButtonGroup
import com.aliothmoon.maameow.presentation.view.panel.common.StageInputField
import com.aliothmoon.maameow.presentation.view.panel.common.StageRow
import org.koin.compose.koinInject

/** 目标库存上限，对齐 WPF NumericUpDown 的 Maximum */
private const val MAX_TARGET_INVENTORY = 1145141919

@Composable
fun DepotMaintainConfigPanel(
    config: DepotMaintainConfig,
    onConfigChange: (DepotMaintainConfig) -> Unit,
    modifier: Modifier = Modifier,
    depotRepository: DepotRepository = koinInject(),
    itemHelper: ItemHelper = koinInject(),
    activityManager: ActivityManager = koinInject(),
) {
    val snapshot by depotRepository.snapshot.collectAsStateWithLifecycle()
    val dropItems by itemHelper.dropItems.collectAsStateWithLifecycle()
    val activityStages by activityManager.activityStages.collectAsStateWithLifecycle()

    // 排除「当期剿灭」：库存保持按材料刷取，剿灭无指定掉落。对齐上游 RefreshStageList
    val stageGroups = remember(activityStages) {
        activityManager.getMergedStageGroups()
            .map { group -> group.copy(stages = group.stages.filterNot { it.code == "Annihilation" }) }
            .filter { it.stages.isNotEmpty() }
    }
    val stageCodes = remember(stageGroups) {
        stageGroups.flatMap { group -> group.stages.map { it.code } }
    }
    val itemNameMap = remember(dropItems) { dropItems.associate { it.id to it.name } }
    val itemIds = if (dropItems.isNotEmpty()) dropItems.map { it.id } else UiUsageConstants.dropItems

    // 展开态是纯 UI 局部状态，不持久化。
    // 注意：以下标为 key，删除中间某条计划后展开态会落到相邻计划上。
    // v1 接受此偏差（用户删除后通常会重新展开），若反馈明显再给 plan 加稳定 UI key。
    val expandedIndices = remember { mutableStateListOf<Int>() }

    val notSelectedLabel = stringResource(R.string.panel_depot_not_selected)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 12.dp, vertical = 4.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CheckBoxWithExpandableTip(
            checked = config.updateDepot,
            onCheckedChange = { onConfigChange(config.copy(updateDepot = it)) },
            label = stringResource(R.string.panel_depot_update_before_start),
            tipText = stringResource(R.string.panel_depot_update_before_start_tip),
        )

        CheckBoxWithLabel(
            checked = config.skipDuringActivity,
            onCheckedChange = { onConfigChange(config.copy(skipDuringActivity = it)) },
            label = stringResource(R.string.panel_depot_skip_during_activity),
        )

        CheckBoxWithLabel(
            checked = config.skipDuringResourceCollection,
            onCheckedChange = { onConfigChange(config.copy(skipDuringResourceCollection = it)) },
            label = stringResource(R.string.panel_depot_skip_during_resource),
        )

        CheckBoxWithExpandableTip(
            checked = config.customStageCode,
            onCheckedChange = { onConfigChange(config.copy(customStageCode = it)) },
            label = stringResource(R.string.panel_fight_custom_stage_code),
            tipText = stringResource(R.string.panel_fight_custom_stage_code_tip),
        )

        // 汇总区：i: 关卡 - 材料 当前/目标
        if (config.plans.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    config.plans.forEachIndexed { index, plan ->
                        // 未跑过仓库识别时用「--」表达「无数据」，而非误导性的 0
                        val current = if (snapshot.syncTimeMillis == 0L) {
                            "--"
                        } else {
                            (snapshot.items[plan.dropId] ?: 0).toString()
                        }
                        Text(
                            text = "${index + 1}: ${stageDisplayOf(stageGroups, plan.stage)}" +
                                    " - ${itemNameMap[plan.dropId] ?: notSelectedLabel}" +
                                    " $current/${plan.dropCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onConfigChange(config.copy(plans = config.plans + DepotMaintainPlan()))
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.panel_depot_add_plan))
            }
            OutlinedButton(
                onClick = { expandedIndices.clear() },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.panel_depot_collapse_all))
            }
        }

        config.plans.forEachIndexed { index, plan ->
            PlanCard(
                index = index,
                plan = plan,
                expanded = index in expandedIndices,
                onToggleExpand = {
                    if (index in expandedIndices) expandedIndices.remove(index)
                    else expandedIndices.add(index)
                },
                customStageCode = config.customStageCode,
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
                    expandedIndices.remove(index)
                    onConfigChange(
                        config.copy(
                            plans = config.plans.toMutableList().also { it.removeAt(index) }
                        )
                    )
                },
            )
        }
    }
}

@Composable
private fun PlanCard(
    index: Int,
    plan: DepotMaintainPlan,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    customStageCode: Boolean,
    stageGroups: List<StageGroup>,
    stageCodes: List<String>,
    itemIds: List<String>,
    itemNameMap: Map<String, String>,
    onPlanChange: (DepotMaintainPlan) -> Unit,
    onRemove: () -> Unit,
) {
    val notSelectedLabel = stringResource(R.string.panel_depot_not_selected)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}: ${stageDisplayOf(stageGroups, plan.stage)}" +
                            " - ${itemNameMap[plan.dropId] ?: notSelectedLabel}" +
                            " x${plan.dropCount}",
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

            AnimatedVisibility(visible = expanded) {
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

                    INumericField(
                        value = plan.dropCount,
                        onValueChange = { onPlanChange(plan.copy(dropCount = it)) },
                        label = stringResource(R.string.panel_depot_target_inventory),
                        minimum = 0,
                        maximum = MAX_TARGET_INVENTORY,
                        modifier = Modifier.fillMaxWidth()
                    )

                    CheckBoxWithLabel(
                        checked = plan.useMedicine,
                        onCheckedChange = { onPlanChange(plan.copy(useMedicine = it)) },
                        label = stringResource(R.string.panel_fight_use_medicine),
                    )
                    AnimatedVisibility(visible = plan.useMedicine) {
                        INumericField(
                            value = plan.medicineCount,
                            onValueChange = { onPlanChange(plan.copy(medicineCount = it)) },
                            label = stringResource(R.string.panel_fight_use_medicine_count),
                            minimum = 0,
                            maximum = 999,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    CheckBoxWithLabel(
                        checked = plan.useStone,
                        onCheckedChange = { onPlanChange(plan.copy(useStone = it)) },
                        label = stringResource(R.string.panel_stone_use),
                    )
                    AnimatedVisibility(visible = plan.useStone) {
                        INumericField(
                            value = plan.stoneCount,
                            onValueChange = { onPlanChange(plan.copy(stoneCount = it)) },
                            label = stringResource(R.string.panel_depot_stone_count),
                            minimum = 0,
                            maximum = 999,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider()

                    // 移动端无 hover，删除按钮在展开态底部常驻（上游为 hover 显示）
                    OutlinedButton(
                        onClick = onRemove,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.panel_depot_remove_plan))
                    }
                }
            }
        }
    }
}

/** 关卡代码 → 显示名；查不到回退代码本身，空串回退「未选择」 */
@Composable
private fun stageDisplayOf(stageGroups: List<StageGroup>, code: String): String {
    if (code.isEmpty()) return stringResource(R.string.panel_depot_not_selected)
    return stageGroups.firstNotNullOfOrNull { group ->
        group.stages.firstOrNull { it.code == code }?.displayName
    } ?: code
}
