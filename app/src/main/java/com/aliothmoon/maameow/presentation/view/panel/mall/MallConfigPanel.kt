package com.aliothmoon.maameow.presentation.view.panel.mall

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.data.model.MallConfig
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.models.resolveMallCreditFightAvailability
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun MallConfigPanel(config: MallConfig, onConfigChange: (MallConfig) -> Unit) {
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 2 }
    )
    val coroutineScope = rememberCoroutineScope()
    var isReorderMode by remember { mutableStateOf(false) }
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
                text = stringResource(R.string.tab_general),
                style = MaterialTheme.typography.bodyMedium,
                color = if (pagerState.currentPage == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable {
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                }
            )
            Text(
                text = stringResource(R.string.tab_advanced),
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
            userScrollEnabled = !isReorderMode && !isDraggingPriority
        ) { page ->
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(end = 12.dp, bottom = 8.dp),
                userScrollEnabled = !isDraggingPriority
            ) {
                when (page) {
                    // 常规设置 Tab
                    0 -> {
                        // 基础设置：访问好友、购物开关、借助战
                        item {
                            BasicMallSettings(config, onConfigChange)
                        }
                        item {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp
                            )
                        }
                        // 优先购买物品列表（可拖拽排序）
                        item {
                            PriorityItemsSection(
                                config = config,
                                onConfigChange = onConfigChange,
                                isReorderMode = isReorderMode,
                                onReorderModeChange = { isReorderMode = it },
                                onDraggingChanged = { isDraggingPriority = it }
                            )
                        }
                        // 提示信息
                        item {
                            MallInfoText()
                        }
                    }

                    // 高级设置 Tab
                    1 -> {
                        // 黑名单管理
                        item {
                            BlacklistSection(config, onConfigChange)
                        }
                        item {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp
                            )
                        }
                        // 高级选项：溢出时无视黑名单、只买打折商品、预留信用点
                        item {
                            AdvancedOptionsSection(config, onConfigChange)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BasicMallSettings(config: MallConfig, onConfigChange: (MallConfig) -> Unit) {
    var shoppingTipExpanded by remember { mutableStateOf(false) }
    var creditFightTipExpanded by remember { mutableStateOf(false) }
    val taskChainState: TaskChainState = koinInject()
    val chain by taskChainState.chain.collectAsStateWithLifecycle()
    val creditFightAvailability = remember(chain) { resolveMallCreditFightAvailability(chain) }

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
                    label = stringResource(R.string.visit_friends)
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
                    label = stringResource(R.string.auto_shopping)
                )
                Spacer(modifier = Modifier.width(4.dp))
                ExpandableTipIcon(
                    expanded = shoppingTipExpanded,
                    onExpandedChange = { shoppingTipExpanded = it })
            }
            ExpandableTipContent(
                visible = shoppingTipExpanded,
                tipText = stringResource(R.string.auto_shopping_tip)
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
                    label = stringResource(R.string.maa_credit_fight)
                )
                Spacer(modifier = Modifier.width(4.dp))
                ExpandableTipIcon(
                    expanded = creditFightTipExpanded,
                    onExpandedChange = { creditFightTipExpanded = it })
            }
            ExpandableTipContent(
                visible = creditFightTipExpanded,
                tipText = stringResource(R.string.credit_fight_tip)
            )
        }

        if (config.creditFight && !creditFightAvailability.isAvailable) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = creditFightAvailability.warningMessage
                        ?: stringResource(R.string.invalid_stage_warning),
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
                    stringResource(R.string.credit_fight_requires_friend),
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
        Text(stringResource(R.string.use_formation), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
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

@Composable
private fun PriorityItemsSection(
    config: MallConfig,
    onConfigChange: (MallConfig) -> Unit,
    isReorderMode: Boolean,
    onReorderModeChange: (Boolean) -> Unit,
    onDraggingChanged: (Boolean) -> Unit
) {
    var priorityItems by remember(config.buyFirst) {
        mutableStateOf(config.buyFirst)
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
                    stringResource(R.string.priority_buy_section),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                if (config.shopping && priorityItems.isNotEmpty()) {
                    IconButton(
                        onClick = { onReorderModeChange(!isReorderMode) },
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isReorderMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            contentColor = if (isReorderMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            if (isReorderMode) Icons.Default.Done else Icons.Default.SwapVert,
                            contentDescription = if (isReorderMode) {
                                stringResource(R.string.done)
                            } else {
                                stringResource(R.string.drag_sort)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                ExpandableTipIcon(
                    expanded = tipExpanded,
                    onExpandedChange = { tipExpanded = it })
            }
            ExpandableTipContent(
                visible = tipExpanded,
                tipText = stringResource(R.string.drag_to_sort_tip)
            )
        }
        Text(
            if (isReorderMode) {
                stringResource(R.string.sort_mode_hint)
            } else {
                stringResource(R.string.normal_mode_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (isReorderMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!config.shopping) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    stringResource(R.string.enable_shopping_first),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        ReorderablePriorityList(
            items = priorityItems,
            enabled = config.shopping,
            isReorderMode = isReorderMode,
            onItemsReordered = { newList ->
                priorityItems = newList.toMutableList()
            },
            onItemRemoved = { index ->
                val newList = priorityItems.filterIndexed { i, _ -> i != index }
                priorityItems = newList.toMutableList()
                onConfigChange(config.copy(buyFirst = newList))
            },
            onDraggingChanged = { dragging ->
                onDraggingChanged(dragging)
                if (!dragging) {
                    onConfigChange(config.copy(buyFirst = priorityItems))
                }
            }
        )

        // 添加按钮
        AnimatedVisibility(visible = !isReorderMode) {
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
                    if (showAddPanel) stringResource(R.string.collapse) else stringResource(R.string.add_item)
                )
            }
        }

        // 内联添加面板（输入框形式）
        AnimatedVisibility(
            visible = showAddPanel,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            InlineAddItemPanel(
                onItemAdded = { newItem ->
                    if (newItem.isNotBlank() && newItem !in priorityItems) {
                        priorityItems = (priorityItems + newItem.trim()).toMutableList()
                        onConfigChange(config.copy(buyFirst = priorityItems))
                    }
                    showAddPanel = false
                },
                onCancel = { showAddPanel = false }
            )
        }
    }
}

@Composable
private fun MallInfoText() {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.priority_buy_note1),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.priority_buy_note2),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.priority_buy_note3),
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
                    stringResource(R.string.blacklist_section),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                ExpandableTipIcon(expanded = tipExpanded, onExpandedChange = { tipExpanded = it })
            }
            ExpandableTipContent(
                visible = tipExpanded,
                tipText = stringResource(R.string.blacklist_tip)
            )
        }

        Text(
            stringResource(R.string.delete_item_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!config.shopping) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    stringResource(R.string.enable_shopping_first),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }
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
                        stringResource(R.string.empty_blacklist),
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
            )
        ) {
            Text(
                if (showAddPanel) stringResource(R.string.collapse) else stringResource(R.string.add_blacklist)
            )
        }

        AnimatedVisibility(
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
