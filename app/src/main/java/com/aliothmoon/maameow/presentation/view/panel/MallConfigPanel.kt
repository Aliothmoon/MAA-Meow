package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.model.MallConfig
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 信用收支配置面板 - 迁移自 WPF MallSettingsUserControl.xaml
 *
 * 已实现功能：
 * - 访问好友开关
 * - 信用交易所自动购物及优先购买列表（可拖拽排序）
 * - 借助战打 OF-1 赚信用及编队选择
 * - 黑名单管理
 * - 高级选项：信用溢出时无视黑名单、只购买打折的信用商品、信用点低于300时停止购买
 *
 * 布局结构：
 * - Tab 1 (常规设置): 访问好友、购物开关、借助战、优先购买物品列表
 * - Tab 2 (高级设置): 黑名单管理、溢出时无视黑名单、只买打折商品、预留信用点
 *
 * 注意：悬浮窗中无法使用 AlertDialog，因此使用内联展开面板替代
 */
@Composable
fun MallConfigPanel(config: MallConfig, onConfigChange: (MallConfig) -> Unit) {
    // HorizontalPager 状态
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 2 }
    )
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(start = 12.dp, top = 2.dp, bottom = 4.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Tab 行（常规设置 / 高级设置）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "常规设置",
                style = MaterialTheme.typography.bodyMedium,
                color = if (pagerState.currentPage == 0) MaterialTheme.colorScheme.primary else Color.Gray,
                fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable {
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                }
            )
            Text(
                text = "高级设置",
                style = MaterialTheme.typography.bodyMedium,
                color = if (pagerState.currentPage == 1) MaterialTheme.colorScheme.primary else Color.Gray,
                fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable {
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                }
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
        )

        // Tab 内容区 - 使用 HorizontalPager 实现左右滑动切换
        HorizontalPager(
            pageSize = PageSize.Fill,
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            userScrollEnabled = true
        ) { page ->
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(end = 12.dp, bottom = 8.dp)
            ) {
                when (page) {
                    // 常规设置 Tab
                    0 -> {
                        // 基础设置：访问好友、购物开关、借助战
                        item {
                            BasicMallSettings(config, onConfigChange)
                        }
                        item {
                            HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)
                        }
                        // 优先购买物品列表（可拖拽排序）
                        item {
                            PriorityItemsSection(config, onConfigChange)
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
                            HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)
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
    var visitFriendsTipExpanded by remember { mutableStateOf(false) }
    var shoppingTipExpanded by remember { mutableStateOf(false) }
    var creditFightTipExpanded by remember { mutableStateOf(false) }
    val visitFriendsTipText = "访问好友基地获取信用点（每日约300信用）"
    val shoppingTipText = "自动使用信用点购买商店中的物品"
    val creditFightTipText = "访问好友后借助战打一把 OF-1 赚 30 信用"

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
                    label = "访问好友"
                )
                Spacer(modifier = Modifier.width(4.dp))
                ExpandableTipIcon(
                    expanded = visitFriendsTipExpanded,
                    onExpandedChange = { visitFriendsTipExpanded = it })
            }
            ExpandableTipContent(
                visible = visitFriendsTipExpanded,
                tipText = visitFriendsTipText,
                modifier = Modifier.padding(start = 28.dp)
            )
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
                    label = "信用交易所自动购物"
                )
                Spacer(modifier = Modifier.width(4.dp))
                ExpandableTipIcon(
                    expanded = shoppingTipExpanded,
                    onExpandedChange = { shoppingTipExpanded = it })
            }
            ExpandableTipContent(
                visible = shoppingTipExpanded,
                tipText = shoppingTipText,
                modifier = Modifier.padding(start = 28.dp)
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
                    label = "借助战打 OF-1 赚信用"
                )
                Spacer(modifier = Modifier.width(4.dp))
                ExpandableTipIcon(
                    expanded = creditFightTipExpanded,
                    onExpandedChange = { creditFightTipExpanded = it })
            }
            ExpandableTipContent(
                visible = creditFightTipExpanded,
                tipText = creditFightTipText,
                modifier = Modifier.padding(start = 28.dp)
            )
        }

        // 借助战编队选择
        if (config.creditFight) {
            FormationSelector(
                selectedFormation = config.creditFightFormation,
                onFormationChange = { onConfigChange(config.copy(creditFightFormation = it)) }
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFE3F2FD),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    "借助战需要好友支援，确保好友列表有可用支援干员",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1976D2),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormationSelector(selectedFormation: Int, onFormationChange: (Int) -> Unit) {
    Column(
        modifier = Modifier.padding(start = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("使用编队", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MallConfig.FORMATION_OPTIONS.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .clickable { onFormationChange(value) }
                        .background(
                            if (selectedFormation == value) Color(0xFFE3F2FD) else Color.Transparent,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PriorityItemsSection(config: MallConfig, onConfigChange: (MallConfig) -> Unit) {
    var priorityItems by remember(config.buyFirst) { mutableStateOf(config.buyFirst.toMutableList()) }
    var showAddPanel by remember { mutableStateOf(false) }
    var tipExpanded by remember { mutableStateOf(false) }
    val tipText = "拖动调整购买优先级，越靠上优先级越高"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "优先购买物品",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                ExpandableTipIcon(
                    expanded = tipExpanded,
                    onExpandedChange = { tipExpanded = it })
            }
            ExpandableTipContent(visible = tipExpanded, tipText = tipText)
        }
        Text(
            "长按拖动可调整优先级顺序，点击×删除",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        if (!config.shopping) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFFF3E0),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    "请先启用「购买信用商店物品」功能",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF57C00),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        ReorderablePriorityList(
            items = priorityItems,
            enabled = config.shopping,
            onItemsReordered = { newList ->
                priorityItems = newList.toMutableList()
                onConfigChange(config.copy(buyFirst = newList))
            },
            onItemRemoved = { index ->
                priorityItems = priorityItems.filterIndexed { i, _ -> i != index }.toMutableList()
                onConfigChange(config.copy(buyFirst = priorityItems))
            }
        )

        // 添加按钮
        Button(
            onClick = { showAddPanel = !showAddPanel },
            enabled = config.shopping,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (showAddPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
        ) { Text(if (showAddPanel) "收起" else "添加物品") }

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

/**
 * 内联添加物品面板 - 输入框形式
 */
@Composable
private fun InlineAddItemPanel(
    onItemAdded: (String) -> Unit,
    onCancel: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFE3F2FD),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "输入物品名称",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            ITextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "如：招聘许可",
                singleLine = true,
                onImeAction = {
                    if (inputText.isNotBlank()) {
                        onItemAdded(inputText)
                    }
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) { Text("取消") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onItemAdded(inputText) },
                    enabled = inputText.isNotBlank()
                ) { Text("添加") }
            }
        }
    }
}

@Composable
private fun ReorderablePriorityList(
    items: List<String>,
    enabled: Boolean,
    onItemsReordered: (List<String>) -> Unit,
    onItemRemoved: (Int) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(lazyListState = lazyListState, onMove = { from, to ->
            val mutableList = items.toMutableList()
            val item = mutableList.removeAt(from.index)
            mutableList.add(to.index, item)
            onItemsReordered(mutableList)
        })

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
    ) {
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂无优先购买物品\n点击下方按钮添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(items, key = { index, item -> "$item-$index" }) { index, item ->
                    ReorderableItem(reorderableState, key = "$item-$index") { isDragging ->
                        PriorityItemRow(
                            item = item,
                            index = index,
                            isDragging = isDragging,
                            enabled = enabled,
                            onRemove = { onItemRemoved(index) },
                            modifier = Modifier.longPressDraggableHandle()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PriorityItemRow(
    item: String,
    index: Int,
    isDragging: Boolean,
    enabled: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) Color.White else Color(0xFFF5F5F5)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(28.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (enabled) MaterialTheme.colorScheme.primary else Color.Gray
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                item,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) Color.Black else Color.Gray,
                modifier = Modifier.weight(1f)
            )
            // 删除按钮
            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "删除",
                    modifier = Modifier.size(16.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.error else Color.LightGray
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.Default.Menu,
                "拖动排序",
                modifier = Modifier.size(20.dp),
                tint = if (enabled) Color.Gray else Color.LightGray
            )
        }
    }
}

@Composable
private fun MallInfoText() {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "* 优先购买列表中的物品会按顺序优先购买",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Text(
            "* 黑名单物品不会被购买（除非信用溢出且启用强制购买）",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Text(
            "* 借助战每日可获得额外信用点，但需要消耗好友支援次数",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun BlacklistSection(config: MallConfig, onConfigChange: (MallConfig) -> Unit) {
    var blacklistItems by remember(config.blacklist) { mutableStateOf(config.blacklist.toMutableList()) }
    var showAddPanel by remember { mutableStateOf(false) }
    var tipExpanded by remember { mutableStateOf(false) }
    val tipText = "黑名单中的物品不会被购买（除非启用「溢出时无视黑名单」）"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "黑名单（不购买）",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                ExpandableTipIcon(expanded = tipExpanded, onExpandedChange = { tipExpanded = it })
            }
            ExpandableTipContent(visible = tipExpanded, tipText = tipText)
        }

        Text(
            "点击×删除物品",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        if (!config.shopping) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFFF3E0),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    "请先启用「购买信用商店物品」功能",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF57C00),
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
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
        ) {
            if (blacklistItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "暂无黑名单物品",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
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
                                blacklistItems = blacklistItems.filterIndexed { i, _ -> i != index }.toMutableList()
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
                containerColor = if (showAddPanel) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
        ) { Text(if (showAddPanel) "收起" else "添加黑名单") }

        // 内联添加黑名单面板（输入框形式）
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

/**
 * 内联添加黑名单面板 - 输入框形式
 */
@Composable
private fun InlineBlacklistAddPanel(
    onItemAdded: (String) -> Unit,
    onCancel: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFFEBEE),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "输入不想购买的物品名称",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            ITextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "如：家具零件",
                singleLine = true,
                onImeAction = {
                    if (inputText.isNotBlank()) {
                        onItemAdded(inputText)
                    }
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) { Text("取消") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onItemAdded(inputText) },
                    enabled = inputText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("添加") }
            }
        }
    }
}

@Composable
private fun BlacklistItemRow(item: String, enabled: Boolean, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) Color(0xFFFFEBEE) else Color(0xFFF5F5F5)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                item,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) Color.Black else Color.Gray,
                modifier = Modifier.weight(1f)
            )
            // 删除按钮
            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "删除",
                    modifier = Modifier.size(16.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.error else Color.LightGray
                )
            }
        }
    }
}

@Composable
private fun AdvancedOptionsSection(config: MallConfig, onConfigChange: (MallConfig) -> Unit) {
    var forceShoppingTipExpanded by remember { mutableStateOf(false) }
    var onlyDiscountTipExpanded by remember { mutableStateOf(false) }
    var reserveCreditTipExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "高级选项",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        // 溢出时无视黑名单
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CheckBoxWithLabel(
                    checked = config.forceShoppingIfCreditFull,
                    onCheckedChange = { onConfigChange(config.copy(forceShoppingIfCreditFull = it)) },
                    label = "信用溢出时无视黑名单",
                    enabled = config.shopping
                )
                Spacer(modifier = Modifier.width(4.dp))
                ExpandableTipIcon(
                    expanded = forceShoppingTipExpanded,
                    onExpandedChange = { forceShoppingTipExpanded = it })
            }
            ExpandableTipContent(
                visible = forceShoppingTipExpanded,
                tipText = "信用点即将溢出时，强制购买黑名单物品避免浪费",
                modifier = Modifier.padding(start = 28.dp)
            )
        }

        // 只买打折物品
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CheckBoxWithLabel(
                    checked = config.onlyBuyDiscount,
                    onCheckedChange = { onConfigChange(config.copy(onlyBuyDiscount = it)) },
                    label = "只购买打折的信用商品",
                    enabled = config.shopping
                )
                Spacer(modifier = Modifier.width(4.dp))
                ExpandableTipIcon(
                    expanded = onlyDiscountTipExpanded,
                    onExpandedChange = { onlyDiscountTipExpanded = it })
            }
            ExpandableTipContent(
                visible = onlyDiscountTipExpanded,
                tipText = "注意：可能会导致信用点数溢出！仍然会购买未打折的白名单物品！",
                modifier = Modifier.padding(start = 28.dp)
            )
        }

        // 预留300信用点
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CheckBoxWithLabel(
                    checked = config.reserveMaxCredit,
                    onCheckedChange = { onConfigChange(config.copy(reserveMaxCredit = it)) },
                    label = "信用点低于 300 时停止购买",
                    enabled = config.shopping
                )
                Spacer(modifier = Modifier.width(4.dp))
                ExpandableTipIcon(
                    expanded = reserveCreditTipExpanded,
                    onExpandedChange = { reserveCreditTipExpanded = it })
            }
            ExpandableTipContent(
                visible = reserveCreditTipExpanded,
                tipText = "低于 300 信用点也仍然会购买白名单物品！",
                modifier = Modifier.padding(start = 28.dp)
            )
        }
    }
}
