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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.aliothmoon.maameow.data.resource.ItemIconLoader
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
import com.aliothmoon.maameow.presentation.viewmodel.DepotPlanContext
import com.aliothmoon.maameow.presentation.view.panel.common.GroupedStageButtonGroup
import com.aliothmoon.maameow.presentation.view.panel.common.ItemButtonGroup
import com.aliothmoon.maameow.presentation.view.panel.common.StageInputField
import com.aliothmoon.maameow.presentation.view.panel.common.StageRow
import com.aliothmoon.maameow.presentation.view.panel.common.stageDisplayName
import com.aliothmoon.maameow.theme.LocalReduceMotion
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility
import com.aliothmoon.maameow.theme.MaaMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

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
    val snapshot by depotRepository.snapshot.collectAsStateWithLifecycle()
    // 物品源与名字都取仓库口径：dropItems 是战斗掉落口径（含声望 / 龙门币这类永不进仓库的东西，
    // 又把芯片助剂等合法材料排除在外），拿它当库存保持的物品源会既多出死格、又漏掉材料
    val itemIds = rememberDepotItemIds(itemHelper)
    val itemIndex by itemHelper.items.collectAsStateWithLifecycle()
    val activityStages by activityManager.activityStages.collectAsStateWithLifecycle()

    // 名字映射用全表：老计划里可能存着已经不在识别列表里的 id
    val itemNameMap = remember(itemIndex) { itemIndex.mapValues { it.value.name } }
    val planOutcomes = remember(config.plans, config.countDualChipAsChipPack, snapshot, activityStages) {
        config.plans.map { plan ->
            val current = DualChipConversion.count(plan.dropId, config.countDualChipAsChipPack, snapshot::countOf)
            depotPlanOutcome(plan, current) { activityManager.isStageOpen(it) }
        }
    }

    // 概览与详情面板都按「折算后」的库存算缺口：勾了双芯片折算的档，
    // 两处显示的「现在有多少」才和执行层一致。
    // 键集合取并集：快照只收录 count > 0 的物品，芯片组自身为 0 时不会出现，
    // 但折算后它可能「有货」（只掉了双芯片没掉芯片组），漏键会比执行层少算
    val effectiveInventory = remember(snapshot, config.countDualChipAsChipPack) {
        if (!config.countDualChipAsChipPack) {
            snapshot.items
        } else {
            (snapshot.items.keys + DualChipConversion.DUAL_CHIP_BY_PACK.keys).associateWith { id ->
                snapshot.countOf(id) + DualChipConversion.extra(id, snapshot::countOf)
            }
        }
    }
    // 点开的那一格；planIndex 为空 = 这一格还没有计划，点开是新建
    var sheetTarget by remember { mutableStateOf<DepotPlanSheetTarget?>(null) }
    var presetPanelExpanded by remember { mutableStateOf(false) }
    // 只在展开期间有效，应用或取消后清掉
    var selectedPreset by remember { mutableStateOf<DepotPresetSelection?>(null) }
    // 物品索引只订阅一次：itemIds / 名字表 / 活动预设键集都从这份快照派生
    val knownItemIds = itemIndex.keys
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
                        itemIds = itemIds,
                        itemNameMap = itemNameMap,
                        inventory = effectiveInventory,
                        inventorySynced = snapshot.hasSynced,
                        iconLoader = iconLoader,
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
                        onOpenPlan = { sheetTarget = it },
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

    // 详情面板挂在滚动容器外面：它自己就是一层模态，跟着页面滚没有意义
    sheetTarget?.let { target ->
        val index = target.planIndex
        DepotPlanEditorSheet(
            itemId = target.itemId,
            itemName = target.itemName,
            // 与概览同一口径：勾了双芯片折算的档，这里显示的「现在有多少」也跟着折算
            count = effectiveInventory[target.itemId] ?: 0,
            // 计划里的 dropId 与点开的那一行不一定相同（历史数据/导入），按它自己取库存
            countOf = { id -> effectiveInventory[id] ?: 0 },
            existing = index?.let { config.plans.getOrNull(it) },
            existingOutcome = index?.let { planOutcomes.getOrNull(it) },
            context = DepotPlanContext(nodeEnabled = nodeEnabled),
            synced = snapshot.hasSynced,
            showMedicine = config.useMedicine,
            showStone = config.useStone,
            onDismiss = { sheetTarget = null },
            onSave = { plan ->
                val plans = config.plans.toMutableList()
                if (index != null && index in plans.indices) {
                    plans[index] = plan
                } else {
                    plans.add(plan)
                }
                onConfigChange(config.copy(plans = plans))
            },
            onRemove = {
                val plans = config.plans.toMutableList()
                if (index != null && index in plans.indices) plans.removeAt(index)
                onConfigChange(config.copy(plans = plans))
            },
        )
    }
}

/**
 * 计划概览：一条计划一行，可拖动排序、左划删除、两秒内可撤销，点行进详情面板。
 *
 * 序号与运行日志的编号同源（1 起、按当前顺序），右侧给出这一趟会不会白跑；
 * 逐条的缺口由行自己讲，这里只回答「会跑几条」。
 *
 * 不再沿用计划卡片就地展开编辑：展开态挂在行下标上，一旦支持拖动排序，
 * 顺序一变展开的就是隔壁那条计划；七个字段挤在卡片里也会把物料按钮组折行。
 *
 * @param outcomes 与 [plans] 同序且等长，由调用方算好，避免每次重组重跑判定
 * @param onMove 松手后回调：第 [from] 条挪到了第 [to] 位
 * @param onDelete 划开并点了删除：删掉第 [index] 条
 * @param onRestore 撤销：把 [plan] 放回第 [index] 位
 */
@Composable
private fun PlanOverview(
    plans: List<DepotMaintainPlan>,
    outcomes: List<DepotPlanOutcome>,
    itemNameMap: Map<String, String>,
    inventory: Map<String, Int>,
    inventorySynced: Boolean,
    iconLoader: ItemIconLoader,
    onClick: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onDelete: (Int) -> Unit,
    onRestore: (index: Int, plan: DepotMaintainPlan) -> Unit,
) {
    val notSelectedLabel = stringResource(R.string.panel_depot_not_selected)
    val entries = remember(plans, outcomes, inventory, itemNameMap, notSelectedLabel) {
        depotPlanOrderEntries(
            plans = plans,
            outcomes = outcomes,
            inventory = inventory,
            itemNameMap = itemNameMap,
            notSelectedLabel = notSelectedLabel,
        )
    }
    val runnableCount = outcomes.count { it == DepotPlanOutcome.Runnable }

    // 划掉的那一条。撤销之后不清空：收起动画还在播的时候文案不该先空掉
    var pendingRemoval by remember { mutableStateOf<DepotPlanRemoval?>(null) }
    var undoVisible by remember { mutableStateOf(false) }
    var undoJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    val removePlan: (Int) -> Unit = { index ->
        plans.getOrNull(index)?.let { plan ->
            pendingRemoval = DepotPlanRemoval(
                index = index,
                plan = plan,
                name = itemNameMap[plan.dropId] ?: notSelectedLabel,
            )
            undoVisible = true
            // 又划掉一条就重新计时：上一个计时器到点也不该把新的这条收掉
            undoJob?.cancel()
            undoJob = scope.launch {
                delay(DepotPlanUndoMillis)
                undoVisible = false
            }
            onDelete(index)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // 列表空的时候不画汇总：0/0 那一行说明不了任何事
        if (plans.isNotEmpty()) {
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
                    color = if (runnableCount == 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = stringResource(R.string.depot_inventory_order_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PlanRemovalUndoBar(
            name = pendingRemoval?.name,
            visible = undoVisible,
            onUndo = {
                undoVisible = false
                undoJob?.cancel()
                pendingRemoval?.let {
                    onRestore(it.index, it.plan)
                    pendingRemoval = null
                }
            },
        )
        DepotPlanOrderColumn(
            entries = entries,
            synced = inventorySynced,
            iconLoader = iconLoader,
            onMove = onMove,
            onClick = onClick,
            onDelete = removePlan,
        )
    }
}

/** 刚划掉、还能撤回的那一条：计划本体 + 原来的位置 + 列表里显示的名字 */
private data class DepotPlanRemoval(
    val index: Int,
    val plan: DepotMaintainPlan,
    val name: String,
)

/** 划掉之后「撤销」亮多久 */
private const val DepotPlanUndoMillis = 2_000L

/**
 * 划掉一条计划之后浮出来的撤销条：左边说划掉了谁，右边一个「撤销」。
 *
 * 就摆在概览标题下面 —— 划的是列表里的行，提示也在同一片区域里。
 *
 * @param name 被划掉的那条计划叫什么；null = 还没划过
 */
@Composable
private fun PlanRemovalUndoBar(
    name: String?,
    visible: Boolean,
    onUndo: () -> Unit,
) {
    // 一直留在组合里（收起时内容不组合）：第一次划掉时 visible 是由 false 翻上来的，
    // 展开动效才播得出来
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
                TextButton(onClick = onUndo) {
                    Text(stringResource(R.string.panel_depot_undo_remove))
                }
            }
        }
    }
}

/** 要点开哪一条计划的详情设置；[planIndex] 为空 = 这一格还没有计划，点开是新建 */
private data class DepotPlanSheetTarget(
    val itemId: String,
    val itemName: String,
    val planIndex: Int?,
)

private fun LazyListScope.GeneralTab(
    config: DepotMaintainConfig,
    onConfigChange: (DepotMaintainConfig) -> Unit,
    planOutcomes: List<DepotPlanOutcome>,
    itemIds: List<String>,
    itemNameMap: Map<String, String>,
    inventory: Map<String, Int>,
    inventorySynced: Boolean,
    iconLoader: ItemIconLoader,
    presetPanelExpanded: Boolean,
    onPresetPanelExpandedChange: (Boolean) -> Unit,
    activityPresets: List<DepotActivityPreset>,
    selectedPreset: DepotPresetSelection?,
    onSelectedPresetChange: (DepotPresetSelection?) -> Unit,
    onApplyPreset: (DepotPresetSelection) -> Boolean,
    showClearConfirm: Boolean,
    onShowClearConfirmChange: (Boolean) -> Unit,
    onOpenPlan: (DepotPlanSheetTarget) -> Unit,
) {
    // 概览不论列表是否为空都要组合：最后一条被划掉时撤销条还得在
    item(key = "summary", contentType = "summary") {
        val notSelectedLabel = stringResource(R.string.panel_depot_not_selected)
        PlanOverview(
            plans = config.plans,
            outcomes = planOutcomes,
            itemNameMap = itemNameMap,
            inventory = inventory,
            inventorySynced = inventorySynced,
            iconLoader = iconLoader,
            onClick = { index ->
                config.plans.getOrNull(index)?.let { plan ->
                    onOpenPlan(
                        DepotPlanSheetTarget(
                            itemId = plan.dropId,
                            itemName = itemNameMap[plan.dropId] ?: notSelectedLabel,
                            planIndex = index,
                        )
                    )
                }
            },
            onMove = { from, to ->
                // 拖动改的就是执行顺序：执行侧按节点内 plans 的下标依次跑
                if (from != to && from in config.plans.indices && to in config.plans.indices) {
                    onConfigChange(
                        config.copy(
                            plans = config.plans.toMutableList()
                                .also { it.add(to, it.removeAt(from)) }
                        )
                    )
                }
            },
            onDelete = { index ->
                val plans = config.plans.toMutableList()
                if (index in plans.indices) plans.removeAt(index)
                onConfigChange(config.copy(plans = plans))
            },
            onRestore = { index, plan ->
                val plans = config.plans.toMutableList()
                // 撤销窗口里列表还可能被拖动过，原位放不下就贴到末尾
                plans.add(index.coerceIn(0, plans.size), plan)
                onConfigChange(config.copy(plans = plans))
            },
        )
    }
    item(key = "controls", contentType = "controls") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    onShowClearConfirmChange(true)
                    onPresetPanelExpandedChange(false)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = config.plans.isNotEmpty(),
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
                    onConfigChange(config.copy(plans = emptyList()))
                    onShowClearConfirmChange(false)
                },
                onDismiss = { onShowClearConfirmChange(false) },
            )
        }
    }
    // 加计划不再有按钮：点「未计划」里的图标，进的就是同一个详情面板
    item(key = "grid", contentType = "grid") {
        MaintainPlanGrid(
            plans = config.plans,
            itemIds = itemIds,
            itemNameMap = itemNameMap,
            inventory = inventory,
            synced = inventorySynced,
            iconLoader = iconLoader,
            onOpenPlan = onOpenPlan,
        )
    }
}

/**
 * 预设下面那两组格子：已经有计划的在上，还没有计划的在下。
 *
 * 上面那组一条计划一格，顺序就是概览、刷取顺序的顺序；下面那组是物品表里其余的全部材料，
 * 默认收起（上百个材料不该一进来就铺满一屏），点标题行展开。
 */
@Composable
private fun MaintainPlanGrid(
    plans: List<DepotMaintainPlan>,
    itemIds: List<String>,
    itemNameMap: Map<String, String>,
    inventory: Map<String, Int>,
    synced: Boolean,
    iconLoader: ItemIconLoader,
    onOpenPlan: (DepotPlanSheetTarget) -> Unit,
) {
    val notSelectedLabel = stringResource(R.string.panel_depot_not_selected)
    val groups = remember(plans, itemIds, itemNameMap, inventory, notSelectedLabel) {
        depotMaintainItemCells(
            plans = plans,
            itemIds = itemIds,
            itemNameMap = itemNameMap,
            inventory = inventory,
            notSelectedLabel = notSelectedLabel,
        )
    }
    // 收起态是纯 UI 局部状态：切页回来时页内容会被回收，正好回到默认的收起
    var unplannedCollapsed by rememberSaveable { mutableStateOf(true) }
    val sectionColor = MaterialTheme.colorScheme.onSurfaceVariant
    val openCell: (DepotItemCellUi) -> Unit = { cell ->
        onOpenPlan(
            DepotPlanSheetTarget(
                itemId = cell.itemId,
                itemName = cell.name,
                planIndex = cell.planIndex,
            )
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (groups.planned.isNotEmpty()) {
            DepotSectionBreak(
                textRes = R.string.panel_depot_section_planned,
                count = groups.planned.size,
                color = sectionColor,
            )
            DepotItemFlow(
                cells = groups.planned,
                synced = synced,
                iconLoader = iconLoader,
                onClick = openCell,
            )
        }
        DepotSectionBreak(
            textRes = R.string.panel_depot_section_unplanned,
            count = groups.unplanned.size,
            color = sectionColor,
            collapsed = unplannedCollapsed,
            onToggle = { unplannedCollapsed = !unplannedCollapsed },
        )
        // 收起时整片不组合：上百个图标没必要在看不见的时候也去解码
        MaaAnimatedVisibility(visible = !unplannedCollapsed) {
            DepotItemFlow(
                cells = groups.unplanned,
                synced = synced,
                iconLoader = iconLoader,
                onClick = openCell,
            )
        }
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

    // 「按最大倍率刷取」取代了原来的「AUTO 代理倍率」：两者对 core 的效果完全一致
    // （都是 series=0，由 core 取当前可用的最大倍率），差别只在开不开按掉率估算次数。
    // 两个开关表达同一件事，合成一个，顺带让计划里不再需要单独的开关。
    CheckBoxWithExpandableTip(
        checked = config.useAutoSeries,
        onCheckedChange = { onConfigChange(config.copy(useAutoSeries = it)) },
        label = stringResource(R.string.panel_depot_use_expected_drop_rate),
        tipText = stringResource(R.string.panel_depot_use_expected_drop_rate_tip),
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

