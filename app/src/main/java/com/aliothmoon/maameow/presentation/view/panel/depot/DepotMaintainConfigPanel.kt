package com.aliothmoon.maameow.presentation.view.panel.depot

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.DepotMaintainConfig
import com.aliothmoon.maameow.data.model.DepotMaintainPlan
import com.aliothmoon.maameow.data.model.DepotPlanOutcome
import com.aliothmoon.maameow.data.model.depotPlanOutcome
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.StageGroup
import com.aliothmoon.maameow.domain.enums.UiUsageConstants
import com.aliothmoon.maameow.domain.models.DepotActivityPreset
import com.aliothmoon.maameow.domain.models.DualChipConversion
import com.aliothmoon.maameow.presentation.LocalInputFocusManager
import com.aliothmoon.maameow.presentation.components.CheckBoxWithExpandableTip
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.INumericField
import com.aliothmoon.maameow.presentation.components.InlineActionRow
import com.aliothmoon.maameow.presentation.components.InlineConfirmPanel
import com.aliothmoon.maameow.presentation.components.ItemIcon
import com.aliothmoon.maameow.presentation.components.SectionHeader
import com.aliothmoon.maameow.presentation.components.rememberStagedBeyondViewportCount
import com.aliothmoon.maameow.presentation.view.panel.common.GroupedStageButtonGroup
import com.aliothmoon.maameow.presentation.view.panel.common.ItemButtonGroup
import com.aliothmoon.maameow.presentation.view.panel.common.StageInputField
import com.aliothmoon.maameow.presentation.view.panel.common.StageRow
import com.aliothmoon.maameow.presentation.view.panel.common.stageDisplayName
import com.aliothmoon.maameow.theme.LocalReduceMotion
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility
import com.aliothmoon.maameow.theme.MaaMotion
import kotlinx.coroutines.launch
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
    // 另排除「当前/上次」不知道打哪关就算不出缺口，选了必被 toTaskParams 拒掉
    val stageGroups = remember(activityStages) {
        activityManager.getMergedStageGroups()
            .map { group ->
                group.copy(stages = group.stages.filterNot {
                    it.code == "Annihilation" || it.code.isEmpty()
                })
            }
            .filter { it.stages.isNotEmpty() }
    }
    val stageCodes = remember(stageGroups) {
        stageGroups.flatMap { group -> group.stages.map { it.code } }
    }
    val itemNameMap = remember(dropItems) { dropItems.associate { it.id to it.name } }
    val planOutcomes = remember(config.plans, config.countDualChipAsChipPack, snapshot, activityStages) {
        config.plans.map { plan ->
            val current = DualChipConversion.count(plan.dropId, config.countDualChipAsChipPack, snapshot::countOf)
            depotPlanOutcome(plan, current) { activityManager.isStageOpen(it) }
        }
    }
    val itemIds =
        if (dropItems.isNotEmpty()) dropItems.map { it.id } else UiUsageConstants.dropItems

    // 删除计划时同步展开下标，防止串到相邻项
    val expandedIndices = remember { mutableStateListOf<Int>() }
    var presetPanelExpanded by remember { mutableStateOf(false) }
    // 只在展开期间有效，应用或取消后清掉
    var selectedPreset by remember { mutableStateOf<DepotPresetSelection?>(null) }
    val knownItemIds = itemHelper.items.collectAsStateWithLifecycle().value.keys
    // 展开或导入落空时以当下时刻重算，对齐上游 PresetDropDownVisibilityChanged
    var presetClock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val activityPresets = remember(activityStages, knownItemIds, presetClock) {
        DepotActivityPreset.listOpen(activityStages, knownItemIds, presetClock)
    }
    // 选中的活动已不在最新列表就视为未选，确认键随之置灰
    val validPresetSelection = selectedPreset?.takeIf { selection ->
        selection !is DepotPresetSelection.Activity || activityPresets.any { it.key == selection.key }
    }
    // 活动按 key 现查，面板开着期间到期或被移除就不导入，转而刷新列表
    val applyPreset: (DepotPresetSelection) -> Boolean = { selection ->
        val now = System.currentTimeMillis()
        val plans = when (selection) {
            is DepotPresetSelection.Builtin -> appendDepotMaintainPreset(config.plans, selection.preset)
            is DepotPresetSelection.Activity ->
                DepotActivityPreset.listOpen(activityStages, knownItemIds, now)
                    .firstOrNull { it.key == selection.key }
                    ?.let { config.plans + it.plans }
        }
        if (plans != null) onConfigChange(config.copy(plans = plans)) else presetClock = now
        plans != null
    }
    var showClearConfirm by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    // 切页时收起就地展开的面板，免得回来时还挂着一个半途的确认
    LaunchedEffect(pagerState.currentPage) {
        presetPanelExpanded = false
        selectedPreset = null
        showClearConfirm = false
    }
    val coroutineScope = rememberCoroutineScope()
    val inputFocus = LocalInputFocusManager.current
    val generalScrollState = rememberLazyListState()
    val advancedScrollState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 4.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                R.string.common_tab_general,
                R.string.common_tab_advanced,
            ).forEachIndexed { page, labelRes ->
                val selected = pagerState.currentPage == page
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    // 用 selectable 而非 clickable，读屏才会播报选中态
                    // 同 PanelHeader 页签，不要涟漪
                    modifier = Modifier.selectable(
                        selected = selected,
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                    ) {
                        inputFocus.clear()
                        coroutineScope.launch { pagerState.animateScrollToPage(page) }
                    }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))

        HorizontalPager(
            pageSize = PageSize.Fill,
            state = pagerState,
            beyondViewportPageCount = rememberStagedBeyondViewportCount(pagerState, 1),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            LazyColumn(
                state = if (page == 0) generalScrollState else advancedScrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (page) {
                    0 -> GeneralTab(
                        config = config,
                        onConfigChange = onConfigChange,
                        planOutcomes = planOutcomes,
                        stageGroups = stageGroups,
                        stageCodes = stageCodes,
                        itemIds = itemIds,
                        itemNameMap = itemNameMap,
                        inventory = snapshot.items,
                        inventorySynced = snapshot.hasSynced,
                        expandedIndices = expandedIndices,
                        presetPanelExpanded = presetPanelExpanded,
                        onPresetPanelExpandedChange = {
                            presetPanelExpanded = it
                            if (it) presetClock = System.currentTimeMillis()
                        },
                        activityPresets = activityPresets,
                        selectedPreset = validPresetSelection,
                        onSelectedPresetChange = { selectedPreset = it },
                        onApplyPreset = applyPreset,
                        showClearConfirm = showClearConfirm,
                        onShowClearConfirmChange = { showClearConfirm = it },
                    )

                    else -> item(key = "advanced") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdvancedTab(config, onConfigChange)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 计划概览
 * 序号与计划卡、运行日志的编号同源（1 起、按原始位置），右侧给出本次会不会跑
 *
 * @param outcomes 与 [plans] 同序，由调用方算好，避免每次重组重跑判定
 */
@Composable
private fun PlanSummary(
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
                    color = if (runnableCount == 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp,
            )

            plans.forEachIndexed { index, plan ->
                val outcome = outcomes[index]
                val muted =
                    outcome != DepotPlanOutcome.Runnable && outcome != DepotPlanOutcome.Enough
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
                        ItemIcon(itemId = plan.dropId, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = stageDisplayName(plan.stage, stageGroups) +
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
                        DepotPlanOutcome.NoItem -> SummaryTag(
                            text = stringResource(R.string.panel_depot_summary_no_item),
                            color = MaterialTheme.colorScheme.error,
                        )

                        DepotPlanOutcome.ZeroTarget -> SummaryTag(
                            text = stringResource(R.string.panel_depot_summary_zero_target),
                            color = MaterialTheme.colorScheme.error,
                        )

                        DepotPlanOutcome.StageRequired -> SummaryTag(
                            text = stringResource(R.string.panel_depot_summary_stage_required),
                            color = MaterialTheme.colorScheme.error,
                        )

                        DepotPlanOutcome.StageClosed -> SummaryTag(
                            text = stringResource(R.string.panel_depot_summary_stage_closed),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        DepotPlanOutcome.Enough -> SummaryTag(
                            text = stringResource(R.string.panel_depot_summary_enough),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        // 未识别过库存时用「--」表达「无数据」，而非误导性的 0；折算部分以「原始 (+折算)」显示
                        DepotPlanOutcome.Runnable -> SummaryTag(
                            text = inventoryText(plan.dropId, inventory, inventorySynced, countDualChips) +
                                    " / ${plan.dropCount}",
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

private fun inventoryText(
    dropId: String,
    inventory: Map<String, Int>,
    synced: Boolean,
    countDualChips: Boolean,
): String {
    if (!synced) return "--"
    val raw = inventory[dropId] ?: 0
    val extra = if (countDualChips) DualChipConversion.extra(dropId) { inventory[it] ?: 0 } else 0
    return if (extra > 0) "$raw (+$extra)" else "$raw"
}

@Composable
private fun SummaryTag(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
    )
}

private fun LazyListScope.GeneralTab(
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
        item(key = "summary", contentType = "summary") {
            PlanSummary(
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
    item(key = "controls", contentType = "controls") {
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
    itemsIndexed(config.plans, key = { index, _ -> "plan-$index" }, contentType = { _, _ -> "plan" }) { index, plan ->
        PlanCard(
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
                        plans = config.plans.toMutableList().also { it[index] = updated })
                )
            },
            onRemove = {
                // 删除 index 后：>index 的展开下标整体 -1，==index 移除
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
                        plans = config.plans.toMutableList().also { it.removeAt(index) })
                )
            },
        )
    }
}

/**
 * 预设选择器：点开后就地展开单选组，选中再确认才追加计划
 *
 * 有进行中的活动时分「常驻关卡」「当前活动」两组，否则不显示分组标题
 */
@Composable
private fun ColumnScope.PresetPicker(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    activityPresets: List<DepotActivityPreset>,
    skipDuringActivity: Boolean,
    selected: DepotPresetSelection?,
    onSelectedChange: (DepotPresetSelection?) -> Unit,
    /** 返回 false 表示所选活动已失效，未导入 */
    onApply: (DepotPresetSelection) -> Boolean,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaaMotion.spec(LocalReduceMotion.current, MaaMotion.Fast),
        label = "presetArrow",
    )

    OutlinedButton(
        onClick = {
            onExpandedChange(!expanded)
            if (expanded) onSelectedChange(null)
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(stringResource(R.string.panel_depot_preset))
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .rotate(arrowRotation),
        )
    }
    MaaAnimatedVisibility(
        visible = expanded,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                val grouped = activityPresets.isNotEmpty()
                val groupHeaderModifier = Modifier.padding(start = 12.dp, top = 4.dp)
                if (grouped) {
                    SectionHeader(stringResource(R.string.panel_fight_stage_group_permanent), groupHeaderModifier)
                }
                DepotMaintainPreset.entries.forEach { preset ->
                    val option = DepotPresetSelection.Builtin(preset)
                    PresetOptionRow(
                        label = stringResource(preset.labelRes),
                        planCount = preset.plans.size,
                        selected = selected == option,
                        onSelect = { onSelectedChange(option) },
                    )
                }
                if (grouped) {
                    SectionHeader(stringResource(R.string.panel_depot_preset_group_activity), groupHeaderModifier)
                    activityPresets.forEach { preset ->
                        val option = DepotPresetSelection.Activity(preset.key)
                        PresetOptionRow(
                            label = preset.name,
                            planCount = preset.plans.size,
                            selected = selected == option,
                            onSelect = { onSelectedChange(option) },
                        )
                    }
                }
                // 导入不改运行语义，只在确认前提示，对齐上游 DepotPresetActivitySkipConflict
                if (selected is DepotPresetSelection.Activity && skipDuringActivity) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .size(16.dp),
                        )
                        Text(
                            text = stringResource(
                                R.string.panel_depot_preset_activity_skip_conflict,
                                stringResource(R.string.panel_depot_skip_during_activity),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                InlineActionRow(
                    confirmText = stringResource(R.string.panel_depot_preset_apply),
                    dismissText = stringResource(R.string.common_cancel),
                    confirmEnabled = selected != null,
                    onConfirm = {
                        // 活动已失效时不收起，列表已随之刷新
                        if (selected != null && onApply(selected)) onExpandedChange(false)
                        onSelectedChange(null)
                    },
                    onDismiss = {
                        onExpandedChange(false)
                        onSelectedChange(null)
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PresetOptionRow(
    label: String,
    planCount: Int,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        // 提前告知会加几条，芯片预设一次进 8 条
        Text(
            text = stringResource(R.string.panel_depot_preset_plan_count, planCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 高级设置页：对齐上游 DepotMaintainTaskUserControl 的三个分组 */
@Composable
private fun ColumnScope.AdvancedTab(
    config: DepotMaintainConfig,
    onConfigChange: (DepotMaintainConfig) -> Unit,
) {
    SectionHeader(stringResource(R.string.panel_depot_group_task_behavior))

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

    CheckBoxWithExpandableTip(
        checked = config.onlyFirstInsufficientPlan,
        onCheckedChange = { onConfigChange(config.copy(onlyFirstInsufficientPlan = it)) },
        label = stringResource(R.string.panel_depot_only_first_insufficient_plan),
        tipText = stringResource(R.string.panel_depot_only_first_insufficient_plan_tip),
    )

    CheckBoxWithExpandableTip(
        checked = config.sortByProgress,
        onCheckedChange = { onConfigChange(config.copy(sortByProgress = it)) },
        label = stringResource(R.string.panel_depot_sort_by_progress),
        tipText = stringResource(R.string.panel_depot_sort_by_progress_tip),
    )

    CheckBoxWithExpandableTip(
        checked = config.countDualChipAsChipPack,
        onCheckedChange = { onConfigChange(config.copy(countDualChipAsChipPack = it)) },
        label = stringResource(R.string.panel_depot_count_dual_chip),
        tipText = stringResource(R.string.panel_depot_count_dual_chip_tip),
    )

    CheckBoxWithLabel(
        checked = config.skipDuringResourceCollection,
        onCheckedChange = { onConfigChange(config.copy(skipDuringResourceCollection = it)) },
        label = stringResource(R.string.panel_depot_skip_during_resource),
    )

    SectionHeader(
        title = stringResource(R.string.panel_depot_group_stage_battle),
        modifier = Modifier.padding(top = 4.dp),
    )

    CheckBoxWithExpandableTip(
        checked = config.customStageCode,
        onCheckedChange = { onConfigChange(config.copy(customStageCode = it)) },
        label = stringResource(R.string.panel_fight_custom_stage_code),
        tipText = stringResource(R.string.panel_fight_custom_stage_code_tip),
    )

    CheckBoxWithExpandableTip(
        checked = config.useAutoSeries,
        onCheckedChange = { onConfigChange(config.copy(useAutoSeries = it)) },
        label = stringResource(R.string.panel_depot_use_auto_series),
        tipText = stringResource(R.string.panel_depot_use_auto_series_tip),
    )

    SectionHeader(
        title = stringResource(R.string.panel_depot_group_sanity),
        modifier = Modifier.padding(top = 4.dp),
    )

    CheckBoxWithLabel(
        checked = config.useMedicine,
        onCheckedChange = { onConfigChange(config.copy(useMedicine = it)) },
        label = stringResource(R.string.panel_depot_enable_use_medicine),
    )

    CheckBoxWithLabel(
        checked = config.useStone,
        onCheckedChange = { onConfigChange(config.copy(useStone = it)) },
        label = stringResource(R.string.panel_depot_enable_use_stone),
    )

    CheckBoxWithExpandableTip(
        checked = config.useExpiringMedicine,
        onCheckedChange = { onConfigChange(config.copy(useExpiringMedicine = it)) },
        label = stringResource(R.string.panel_depot_use_expiring_medicine),
        tipText = stringResource(R.string.panel_depot_use_expiring_medicine_tip),
    )
}

@Composable
private fun PlanCard(
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (plan.dropId.isNotEmpty()) {
                    ItemIcon(itemId = plan.dropId, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = stageDisplayName(
                        plan.stage,
                        stageGroups,
                    ) + " · ${itemNameMap[plan.dropId] ?: notSelectedLabel}" + " x${plan.dropCount}",
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
                            onItemSelected = { onPlanChange(plan.copy(stage = it)) })
                    }

                    ItemButtonGroup(
                        label = stringResource(R.string.panel_fight_material),
                        selectedValue = plan.dropId,
                        items = itemIds,
                        onItemSelected = { onPlanChange(plan.copy(dropId = it)) },
                        displayMapper = { id -> itemNameMap[id] ?: id })

                    INumericField(
                        value = plan.dropCount,
                        onValueChange = { onPlanChange(plan.copy(dropCount = it)) },
                        label = stringResource(R.string.panel_depot_target_inventory),
                        // 展开时 dropCount <= 0 会被按 ERROR 拒绝，UI 不该放行 0
                        minimum = 1,
                        maximum = MAX_TARGET_INVENTORY,
                        modifier = Modifier.fillMaxWidth()
                    )

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

                    // 移动端无 hover，删除按钮在展开态底部常驻（上游为 hover 显示）
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
                        Text(stringResource(R.string.panel_depot_remove_plan))
                    }
                }
            }
        }
    }
}
