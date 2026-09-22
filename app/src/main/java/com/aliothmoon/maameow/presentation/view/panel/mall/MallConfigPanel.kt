package com.aliothmoon.maameow.presentation.view.panel.mall

import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.MallConfig
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.domain.models.MallCreditFightAvailability
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility
import com.aliothmoon.maameow.utils.i18n.asString
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun MallConfigPanel(config: MallConfig, onConfigChange: (MallConfig) -> Unit) {
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 2 }
    )
    val coroutineScope = rememberCoroutineScope()
    var isDraggingPriority by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 4.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Tab 行（常规设置 / 高级设置）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.common_tab_general),
                style = MaterialTheme.typography.bodyMedium,
                color = if (pagerState.currentPage == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable {
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                }
            )
            Text(
                text = stringResource(R.string.common_tab_advanced),
                style = MaterialTheme.typography.bodyMedium,
                color = if (pagerState.currentPage == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable {
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                }
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
        )

        HorizontalPager(
            pageSize = PageSize.Fill,
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            userScrollEnabled = !isDraggingPriority
        ) { page ->
            when (page) {
                0 -> MallGeneralTab(
                    config = config,
                    onConfigChange = onConfigChange,
                    onDraggingChanged = { isDraggingPriority = it }
                )

                1 -> MallAdvancedTab(config = config, onConfigChange = onConfigChange)
            }
        }
    }
}

@Composable
private fun BasicMallSettings(config: MallConfig, onConfigChange: (MallConfig) -> Unit) {
    var shoppingTipExpanded by remember { mutableStateOf(false) }
    var creditFightTipExpanded by remember { mutableStateOf(false) }
    val taskChainState: TaskChainState = koinInject()
    val activityManager: ActivityManager = koinInject()
    val chain by taskChainState.chain.collectAsStateWithLifecycle()
    val creditFightAvailability = remember(chain, activityManager) {
        MallCreditFightAvailability.resolve(chain, activityManager)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 访问好友
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CheckBoxWithLabel(
                    checked = config.visitFriends,
                    onCheckedChange = { onConfigChange(config.copy(visitFriends = it)) },
                    label = stringResource(R.string.panel_mall_visit_friends)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
        }

        // 购物开关
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CheckBoxWithLabel(
                    checked = config.shopping,
                    onCheckedChange = { onConfigChange(config.copy(shopping = it)) },
                    label = stringResource(R.string.panel_mall_shopping)
                )
                Spacer(modifier = Modifier.width(4.dp))
                ExpandableTipIcon(
                    expanded = shoppingTipExpanded,
                    onExpandedChange = { shoppingTipExpanded = it })
            }
            ExpandableTipContent(
                visible = shoppingTipExpanded,
                tipText = stringResource(R.string.panel_mall_shopping_tip)
            )
        }

        // 借助战
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CheckBoxWithLabel(
                    checked = config.creditFight,
                    onCheckedChange = { onConfigChange(config.copy(creditFight = it)) },
                    label = stringResource(R.string.panel_mall_credit_fight)
                )
                Spacer(modifier = Modifier.width(4.dp))
                ExpandableTipIcon(
                    expanded = creditFightTipExpanded,
                    onExpandedChange = { creditFightTipExpanded = it })
            }
            ExpandableTipContent(
                visible = creditFightTipExpanded,
                tipText = stringResource(R.string.panel_mall_credit_fight_tip)
            )
        }

        if (config.creditFight && !creditFightAvailability.isAvailable) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = creditFightAvailability.message?.asString()
                        ?.takeIf { it.isNotEmpty() }
                        ?: stringResource(R.string.panel_mall_credit_fight_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        // 借助战编队选择
        if (config.creditFight) {
            FormationSelector(
                selectedFormation = config.creditFightFormation,
                onFormationChange = { onConfigChange(config.copy(creditFightFormation = it)) }
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    stringResource(R.string.panel_mall_credit_fight_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun FormationSelector(selectedFormation: Int, onFormationChange: (Int) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            stringResource(R.string.panel_mall_use_formation),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MallConfig.FORMATION_OPTIONS.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .clickable { onFormationChange(value) }
                        .background(
                            if (selectedFormation == value) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedFormation == value,
                        onClick = { onFormationChange(value) },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// 加前缀跟区段 key 错开，物品名是用户输入的
private fun priorityItemKey(item: String) = "buy:$item"

/** 优先购买列表直接铺在 LazyColumn 里，拖到边缘才能自动滚动 */
@Composable
private fun MallGeneralTab(
    config: MallConfig,
    onConfigChange: (MallConfig) -> Unit,
    onDraggingChanged: (Boolean) -> Unit
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    // 拖拽中先改本地顺序，松手再落盘
    // 不能带 key：拖拽回调被 pointerInput 长期持有，重建会写到旧 state
    // distinct 不能省，LazyColumn 重复 key 会抛异常
    var priorityItems by remember { mutableStateOf(config.buyFirst.distinct()) }
    var showAddPanel by remember { mutableStateOf(false) }
    var tipExpanded by remember { mutableStateOf(false) }

    // 同理，落盘回调要取最新值；其余回调随重组更新，用不着
    val currentConfig by rememberUpdatedState(config)
    val currentOnConfigChange by rememberUpdatedState(onConfigChange)

    LaunchedEffect(config.buyFirst) {
        priorityItems = config.buyFirst.distinct()
    }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val list = priorityItems.toMutableList()
        val fromIndex = list.indexOfFirst { priorityItemKey(it) == from.key }
        val toIndex = list.indexOfFirst { priorityItemKey(it) == to.key }
        // 表头、按钮这些非列表项取不到下标
        if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) {
            return@rememberReorderableLazyListState
        }
        list.add(toIndex, list.removeAt(fromIndex))
        priorityItems = list
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(end = 12.dp, bottom = 8.dp)
    ) {
        item(key = "mall_basic") {
            BasicMallSettings(config, onConfigChange)
        }
        item(key = "mall_basic_divider") {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp
            )
        }
        item(key = "mall_priority_header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.panel_mall_priority_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    ExpandableTipIcon(
                        expanded = tipExpanded,
                        onExpandedChange = { tipExpanded = it })
                }
                ExpandableTipContent(
                    visible = tipExpanded,
                    tipText = stringResource(R.string.panel_mall_priority_reorder_tip)
                )
                Text(
                    stringResource(R.string.panel_mall_priority_drag_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!config.shopping) {
            item(key = "mall_priority_shopping_hint") {
                ShoppingDisabledHint()
            }
        }
        if (priorityItems.isEmpty()) {
            item(key = "mall_priority_empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.extraSmall
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = MaterialTheme.shapes.extraSmall
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.panel_mall_priority_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        items(priorityItems, key = { priorityItemKey(it) }) { item ->
            ReorderableItem(reorderableState, key = priorityItemKey(item)) { isDragging ->
                PriorityItemRow(
                    item = item,
                    isDragging = isDragging,
                    enabled = config.shopping,
                    onDragStarted = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDraggingChanged(true)
                    },
                    onDragStopped = {
                        onDraggingChanged(false)
                        // 没换位就别写盘，一次落盘是全量 profile JSON 重写
                        if (priorityItems != currentConfig.buyFirst) {
                            currentOnConfigChange(currentConfig.copy(buyFirst = priorityItems))
                        }
                    },
                    onRemove = {
                        val newList = priorityItems.filterNot { it == item }
                        priorityItems = newList
                        onConfigChange(config.copy(buyFirst = newList))
                    }
                )
            }
        }
        item(key = "mall_priority_add") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showAddPanel = !showAddPanel },
                    enabled = config.shopping,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (showAddPanel) {
                            stringResource(R.string.common_collapse)
                        } else {
                            stringResource(R.string.panel_mall_add_item)
                        }
                    )
                }
                MaaAnimatedVisibility(
                    visible = showAddPanel,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    InlineAddItemPanel(
                        onItemAdded = { newItem ->
                            val trimmed = newItem.trim()
                            if (trimmed.isNotEmpty() && trimmed !in priorityItems) {
                                val newList = priorityItems + trimmed
                                priorityItems = newList
                                onConfigChange(config.copy(buyFirst = newList))
                            }
                            showAddPanel = false
                        },
                        onCancel = { showAddPanel = false }
                    )
                }
            }
        }
        item(key = "mall_info") {
            MallInfoText()
        }
    }
}

@Composable
private fun MallAdvancedTab(config: MallConfig, onConfigChange: (MallConfig) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(end = 12.dp, bottom = 8.dp)
    ) {
        item(key = "mall_blacklist") {
            BlacklistSection(config, onConfigChange)
        }
        item(key = "mall_advanced_divider") {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp
            )
        }
        item(key = "mall_advanced_options") {
            AdvancedOptionsSection(config, onConfigChange)
        }
    }
}

@Composable
private fun ShoppingDisabledHint() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            stringResource(R.string.panel_mall_enable_shopping_first),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
private fun MallInfoText() {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.panel_mall_info_line_priority),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.panel_mall_info_line_blacklist),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.panel_mall_info_line_credit_fight),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BlacklistSection(config: MallConfig, onConfigChange: (MallConfig) -> Unit) {
    var blacklistItems by remember(config.blacklist) {
        mutableStateOf(config.blacklist)
    }
    var showAddPanel by remember { mutableStateOf(false) }
    var tipExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.panel_mall_blacklist_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                ExpandableTipIcon(expanded = tipExpanded, onExpandedChange = { tipExpanded = it })
            }
            ExpandableTipContent(
                visible = tipExpanded,
                tipText = stringResource(R.string.panel_mall_blacklist_tip)
            )
        }

        Text(
            stringResource(R.string.panel_mall_blacklist_delete_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!config.shopping) {
            ShoppingDisabledHint()
        }

        // 黑名单列表
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            if (blacklistItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.panel_mall_blacklist_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(blacklistItems) { index, item ->
                        BlacklistItemRow(
                            item = item,
                            enabled = config.shopping,
                            onRemove = {
                                blacklistItems = blacklistItems.filterIndexed { i, _ -> i != index }
                                    .toMutableList()
                                onConfigChange(config.copy(blacklist = blacklistItems))
                            }
                        )
                    }
                }
            }
        }

        // 添加按钮
        Button(
            onClick = { showAddPanel = !showAddPanel },
            enabled = config.shopping,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (showAddPanel) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error.copy(
                    alpha = 0.8f
                )
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                if (showAddPanel) {
                    stringResource(R.string.common_collapse)
                } else {
                    stringResource(R.string.panel_mall_add_blacklist)
                }
            )
        }

        MaaAnimatedVisibility(
            visible = showAddPanel,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            InlineBlacklistAddPanel(
                onItemAdded = { newItem ->
                    if (newItem.isNotBlank() && newItem !in blacklistItems) {
                        blacklistItems = (blacklistItems + newItem.trim()).toMutableList()
                        onConfigChange(config.copy(blacklist = blacklistItems))
                    }
                    showAddPanel = false
                },
                onCancel = { showAddPanel = false }
            )
        }
    }
}
