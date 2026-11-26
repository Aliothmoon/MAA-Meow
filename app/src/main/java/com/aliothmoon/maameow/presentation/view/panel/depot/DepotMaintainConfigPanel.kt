package com.aliothmoon.maameow.presentation.view.panel.depot

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import com.aliothmoon.maameow.data.resource.ItemIconLoader
import com.aliothmoon.maameow.data.resource.StageGroup
import com.aliothmoon.maameow.domain.models.DepotActivityPreset
import com.aliothmoon.maameow.domain.models.DualChipConversion
import com.aliothmoon.maameow.presentation.LocalFloatingWindowContext
import com.aliothmoon.maameow.presentation.LocalInputFocusManager
import com.aliothmoon.maameow.presentation.components.CheckBoxWithExpandableTip
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.InlineActionRow
import com.aliothmoon.maameow.presentation.components.InlineConfirmPanel
import com.aliothmoon.maameow.presentation.components.SectionHeader
import com.aliothmoon.maameow.presentation.components.rememberStagedBeyondViewportCount
import com.aliothmoon.maameow.theme.LocalReduceMotion
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility
import com.aliothmoon.maameow.theme.MaaMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun DepotMaintainConfigPanel(
    config: DepotMaintainConfig,
    onConfigChange: (DepotMaintainConfig) -> Unit,
    nodeEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    depotRepository: DepotRepository = koinInject(),
    itemHelper: ItemHelper = koinInject(),
    activityManager: ActivityManager = koinInject(),
    iconLoader: ItemIconLoader = koinInject(),
) {
    val isFloatingWindow = LocalFloatingWindowContext.current
    val snapshot by depotRepository.snapshot.collectAsStateWithLifecycle()
    val itemIds = rememberDepotItemIds(itemHelper)
    val itemIndex by itemHelper.items.collectAsStateWithLifecycle()
    val activityStages by activityManager.activityStages.collectAsStateWithLifecycle()

    val itemNameMap = remember(itemIndex) { itemIndex.mapValues { it.value.name } }

    // 拖拽中只改本地顺序，松手再落盘；异步写回若逐格触发会按旧列表回跳
    var nextPlanUid by remember { mutableLongStateOf(0L) }
    var orderedPlans by remember {
        mutableStateOf(config.plans.map { IndexedDepotPlan(nextPlanUid++, it) })
    }
    val currentConfig by rememberUpdatedState(config)
    val currentOnConfigChange by rememberUpdatedState(onConfigChange)
    LaunchedEffect(config.plans) {
        if (orderedPlans.map { it.plan } != config.plans) {
            orderedPlans = reconcileOrderedPlans(orderedPlans, config.plans) { nextPlanUid++ }
        }
    }
    val displayPlans = remember(isFloatingWindow, config.plans, orderedPlans) {
        if (isFloatingWindow) config.plans else orderedPlans.map { it.plan }
    }
    val planOutcomes = remember(displayPlans, config.countDualChipAsChipPack, snapshot, activityStages) {
        displayPlans.map { plan ->
            val current = DualChipConversion.count(plan.dropId, config.countDualChipAsChipPack, snapshot::countOf)
            depotPlanOutcome(plan, current) { activityManager.isStageOpen(it) }
        }
    }

    // 概览与编辑面板按双芯片折算后口径展示库存，与执行层保持一致
    val effectiveInventory = remember(snapshot, config.countDualChipAsChipPack) {
        if (!config.countDualChipAsChipPack) {
            snapshot.items
        } else {
            (snapshot.items.keys + DualChipConversion.DUAL_CHIP_BY_PACK.keys).associateWith { id ->
                snapshot.countOf(id) + DualChipConversion.extra(id, snapshot::countOf)
            }
        }
    }

    // 悬浮窗内沿用旧版内联 PlanCard（悬浮窗无法弹 ModalBottomSheet）
    val stageGroups = rememberDepotStageGroups(activityManager)
    val stageCodes = remember(stageGroups) { stageGroups.allStageCodes() }
    val expandedIndices = remember { mutableStateListOf<Int>() }

    var activeSheet by remember { mutableStateOf<DepotSheetMode?>(null) }
    val notSelectedLabel = stringResource(R.string.panel_depot_not_selected)
    val unplannedCells = remember(config.plans, itemIds, itemNameMap, effectiveInventory, notSelectedLabel) {
        depotMaintainItemCells(
            plans = config.plans,
            itemIds = itemIds,
            itemNameMap = itemNameMap,
            inventory = effectiveInventory,
            notSelectedLabel = notSelectedLabel,
        ).unplanned
    }

    // 删除撤销窗口提升至面板顶层：左滑删除与编辑面板移除共用，清空或新增时同步收起
    var pendingRemoval by remember { mutableStateOf<DepotPlanRemoval?>(null) }
    var undoVisible by remember { mutableStateOf(false) }
    var undoJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val dismissUndo: () -> Unit = {
        undoJob?.cancel()
        undoVisible = false
        pendingRemoval = null
    }
    val deletePlanWithUndo: (Int) -> Unit = { index ->
        config.plans.getOrNull(index)?.let { plan ->
            pendingRemoval = DepotPlanRemoval(
                index = index,
                plan = plan,
                name = itemNameMap[plan.dropId] ?: notSelectedLabel,
            )
            undoVisible = true
            undoJob?.cancel()
            undoJob = coroutineScope.launch {
                delay(DepotPlanUndoMillis)
                undoVisible = false
            }
            val plans = config.plans.toMutableList()
            plans.removeAt(index)
            onConfigChange(config.copy(plans = plans))
        }
    }

    var presetPanelExpanded by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf<DepotPresetSelection?>(null) }
    val knownItemIds = itemIndex.keys
    var presetClock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val activityPresets = remember(activityStages, knownItemIds, presetClock) {
        DepotActivityPreset.listOpen(activityStages, knownItemIds, presetClock)
    }
    val validPresetSelection = selectedPreset?.takeIf { selection ->
        selection !is DepotPresetSelection.Activity || activityPresets.any { it.key == selection.key }
    }
    val applyPreset: (DepotPresetSelection) -> Boolean = { selection ->
        val now = System.currentTimeMillis()
        val plans = when (selection) {
            is DepotPresetSelection.Builtin -> appendDepotMaintainPreset(config.plans, selection.preset)
            is DepotPresetSelection.Activity ->
                DepotActivityPreset.listOpen(activityStages, knownItemIds, now)
                    .firstOrNull { it.key == selection.key }
                    ?.let { config.plans + it.plans }
        }
        if (plans != null) {
            dismissUndo()
            onConfigChange(config.copy(plans = plans))
        } else {
            presetClock = now
        }
        plans != null
    }
    var showClearConfirm by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    LaunchedEffect(pagerState.currentPage) {
        presetPanelExpanded = false
        selectedPreset = null
        showClearConfirm = false
    }
    val inputFocus = LocalInputFocusManager.current
    val generalScrollState = rememberLazyListState()
    val advancedScrollState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(generalScrollState) { from, to ->
        val list = orderedPlans.toMutableList()
        val fromIndex = list.indexOfFirst { it.uid == from.key }
        val toIndex = list.indexOfFirst { it.uid == to.key }
        // 控制区、概览标题等非计划项取不到下标，直接忽略
        if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) {
            return@rememberReorderableLazyListState
        }
        list.add(toIndex, list.removeAt(fromIndex))
        orderedPlans = list
    }
    val persistPlanOrder: () -> Unit = {
        val updated = orderedPlans.map { it.plan }
        if (updated != currentConfig.plans) {
            currentOnConfigChange(currentConfig.copy(plans = updated))
        }
    }
    val swipeState = rememberDepotPlanSwipeState()
    LaunchedEffect(orderedPlans) {
        val revealed = swipeState.revealedKey
        if (revealed != null && orderedPlans.none { it.uid == revealed }) {
            swipeState.collapse(animated = false)
        }
    }
    val orderEntries = remember(
        displayPlans,
        planOutcomes,
        snapshot.items,
        config.countDualChipAsChipPack,
        itemNameMap,
        notSelectedLabel,
    ) {
        depotPlanOrderEntries(
            plans = displayPlans,
            outcomes = planOutcomes,
            inventory = snapshot.items,
            itemNameMap = itemNameMap,
            notSelectedLabel = notSelectedLabel,
            countDualChips = config.countDualChipAsChipPack,
        )
    }

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
                    0 -> if (isFloatingWindow) {
                        FloatingGeneralTab(
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
                    } else {
                        GeneralTab(
                            config = config,
                            orderedPlans = orderedPlans,
                            planOutcomes = planOutcomes,
                            entries = orderEntries,
                            reorderableState = reorderableState,
                            swipeState = swipeState,
                            stageGroups = stageGroups,
                            inventorySynced = snapshot.hasSynced,
                            iconLoader = iconLoader,
                            pendingRemovalName = pendingRemoval?.name,
                            undoVisible = undoVisible,
                            onDragStopped = persistPlanOrder,
                            onDeletePlan = deletePlanWithUndo,
                            onUndoDelete = {
                                undoVisible = false
                                undoJob?.cancel()
                                pendingRemoval?.let { removed ->
                                    val plans = config.plans.toMutableList()
                                    plans.add(removed.index.coerceIn(0, plans.size), removed.plan)
                                    onConfigChange(config.copy(plans = plans))
                                    pendingRemoval = null
                                }
                            },
                            onClearAllPlans = {
                                dismissUndo()
                                onConfigChange(config.copy(plans = emptyList()))
                            },
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
                            onOpenPlan = { activeSheet = DepotSheetMode.Edit(it) },
                            onAddPlan = { activeSheet = DepotSheetMode.Add },
                        )
                    }

                    else -> item(key = "advanced") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdvancedTab(
                                config = config,
                                onConfigChange = onConfigChange,
                                showCustomStageCode = isFloatingWindow,
                            )
                        }
                    }
                }
            }
        }
    }

    if (!isFloatingWindow) {
        activeSheet?.let { mode ->
            DepotPlanFlowSheet(
                initialTarget = (mode as? DepotSheetMode.Edit)?.target,
                unplannedCells = unplannedCells,
                existingForIndex = { index -> config.plans.getOrNull(index) },
                countOf = { id -> effectiveInventory[id] ?: 0 },
                dualChipExtraOf = { id ->
                    if (config.countDualChipAsChipPack) DualChipConversion.extra(id, snapshot::countOf) else 0
                },
                nodeEnabled = nodeEnabled,
                synced = snapshot.hasSynced,
                showMedicine = config.useMedicine,
                showStone = config.useStone,
                iconLoader = iconLoader,
                onDismiss = { activeSheet = null },
                onSave = { index, plan ->
                    // 新增或修改到与待撤销项同物品时清掉撤销条，避免撤销插回重复计划
                    if (pendingRemoval?.plan?.dropId == plan.dropId) {
                        dismissUndo()
                    }
                    val plans = config.plans.toMutableList()
                    if (index != null && index in plans.indices) {
                        plans[index] = plan
                    } else {
                        plans.add(plan)
                    }
                    onConfigChange(config.copy(plans = plans))
                },
                onRemove = { index -> deletePlanWithUndo(index) },
            )
        }
    }
}



private data class DepotPlanRemoval(
    val index: Int,
    val plan: DepotMaintainPlan,
    val name: String,
)

private const val DepotPlanUndoMillis = 2_000L

@Composable
private fun PlanRemovalUndoBar(
    name: String?,
    visible: Boolean,
    onUndo: () -> Unit,
) {
    MaaAnimatedVisibility(visible = visible) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.panel_depot_plan_removed, name.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onUndo,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(stringResource(R.string.panel_depot_undo_remove))
                }
            }
        }
    }
}

private sealed interface DepotSheetMode {
    data object Add : DepotSheetMode
    data class Edit(val target: DepotPlanSheetTarget) : DepotSheetMode
}

private fun LazyListScope.GeneralTab(
    config: DepotMaintainConfig,
    orderedPlans: List<IndexedDepotPlan>,
    planOutcomes: List<DepotPlanOutcome>,
    entries: List<DepotPlanOrderEntry>,
    reorderableState: ReorderableLazyListState,
    swipeState: DepotPlanSwipeState,
    stageGroups: List<StageGroup>,
    inventorySynced: Boolean,
    iconLoader: ItemIconLoader,
    pendingRemovalName: String?,
    undoVisible: Boolean,
    onDragStopped: () -> Unit,
    onDeletePlan: (Int) -> Unit,
    onUndoDelete: () -> Unit,
    onClearAllPlans: () -> Unit,
    presetPanelExpanded: Boolean,
    onPresetPanelExpandedChange: (Boolean) -> Unit,
    activityPresets: List<DepotActivityPreset>,
    selectedPreset: DepotPresetSelection?,
    onSelectedPresetChange: (DepotPresetSelection?) -> Unit,
    onApplyPreset: (DepotPresetSelection) -> Boolean,
    showClearConfirm: Boolean,
    onShowClearConfirmChange: (Boolean) -> Unit,
    onOpenPlan: (DepotPlanSheetTarget) -> Unit,
    onAddPlan: () -> Unit,
) {
    item(key = "controls", contentType = "controls") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onAddPlan,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.panel_depot_add_plan))
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
                        contentColor = MaterialTheme.colorScheme.error,
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
                    onClearAllPlans()
                    onShowClearConfirmChange(false)
                },
                onDismiss = { onShowClearConfirmChange(false) },
            )
        }
    }
    if (entries.isNotEmpty() || undoVisible) {
        item(key = "summary_header", contentType = "summary_header") {
            val runnableCount = planOutcomes.count { it == DepotPlanOutcome.Runnable }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (entries.isNotEmpty()) {
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
                                entries.size,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                PlanRemovalUndoBar(
                    name = pendingRemovalName,
                    visible = undoVisible,
                    onUndo = onUndoDelete,
                )
            }
        }
    }
    depotPlanOrderItems(
        entries = entries,
        keys = orderedPlans.map { it.uid },
        reorderableState = reorderableState,
        swipeState = swipeState,
        synced = inventorySynced,
        stageGroups = stageGroups,
        iconLoader = iconLoader,
        onDragStopped = onDragStopped,
        onClick = { index ->
            orderedPlans.getOrNull(index)?.plan?.let { plan ->
                onOpenPlan(
                    DepotPlanSheetTarget(
                        itemId = plan.dropId,
                        itemName = entries.getOrNull(index)?.itemName.orEmpty(),
                        planIndex = index,
                    )
                )
            }
        },
        onDelete = onDeletePlan,
    )
}

/** 预设选择器：点开就地展开单选组，确认后追加计划 */
@Composable
internal fun ColumnScope.PresetPicker(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    activityPresets: List<DepotActivityPreset>,
    skipDuringActivity: Boolean,
    selected: DepotPresetSelection?,
    onSelectedChange: (DepotPresetSelection?) -> Unit,
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
                if (selected is DepotPresetSelection.Activity && skipDuringActivity) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
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
                            )
                        }
                    }
                }
                InlineActionRow(
                    confirmText = stringResource(R.string.panel_depot_preset_apply),
                    dismissText = stringResource(R.string.common_cancel),
                    confirmEnabled = selected != null,
                    onConfirm = {
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
        Text(
            text = stringResource(R.string.panel_depot_preset_plan_count, planCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 高级设置页；悬浮窗模式保留「手动输入关卡名」开关以配合旧版内联卡片 */
@Composable
private fun ColumnScope.AdvancedTab(
    config: DepotMaintainConfig,
    onConfigChange: (DepotMaintainConfig) -> Unit,
    showCustomStageCode: Boolean,
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

    if (showCustomStageCode) {
        CheckBoxWithExpandableTip(
            checked = config.customStageCode,
            onCheckedChange = { onConfigChange(config.copy(customStageCode = it)) },
            label = stringResource(R.string.panel_fight_custom_stage_code),
            tipText = stringResource(R.string.panel_fight_custom_stage_code_tip),
        )
    }

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
