package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.model.ReclamationConfig
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.ITextField
import kotlinx.coroutines.launch

@Composable
fun ReclamationConfigPanel(config: ReclamationConfig, onConfigChange: (ReclamationConfig) -> Unit) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(start = 12.dp, top = 2.dp, bottom = 4.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Tab 行
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
            Text(
                text = "说明",
                style = MaterialTheme.typography.bodyMedium,
                color = if (pagerState.currentPage == 2) MaterialTheme.colorScheme.primary else Color.Gray,
                fontWeight = if (pagerState.currentPage == 2) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable {
                    coroutineScope.launch { pagerState.animateScrollToPage(2) }
                }
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
        )

        // Tab 内容区
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
                        item {
                            ReclamationButtonGroup(
                                label = "主题",
                                options = ReclamationConfig.THEME_OPTIONS,
                                selectedValue = config.theme,
                                onValueChange = { onConfigChange(config.copy(theme = it as String)) }
                            )
                        }
                        item {
                            ReclamationButtonGroup(
                                label = "策略",
                                options = ReclamationConfig.MODE_OPTIONS,
                                selectedValue = config.mode,
                                onValueChange = { onConfigChange(config.copy(mode = it as Int)) }
                            )
                        }
                        item {
                            AnimatedVisibility(visible = config.mode == 0) {
                                CheckBoxWithLabel(
                                    checked = config.clearStore,
                                    onCheckedChange = { onConfigChange(config.copy(clearStore = it)) },
                                    label = "任务完成后购买商店"
                                )
                            }
                        }
                        item {
                            AnimatedVisibility(visible = config.mode == 1) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFFE3F2FD),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "通过组装支援道具刷生息点数，需要有存档",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF1976D2),
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 高级设置 Tab
                    1 -> {
                        val isArchiveMode = config.mode == 1
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "支援道具名称",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                ITextField(
                                    value = config.toolToCraft,
                                    onValueChange = { onConfigChange(config.copy(toolToCraft = it)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = "荧光棒",
                                    singleLine = false,
                                    enabled = isArchiveMode
                                )
                                Text(
                                    "多个道具用分号分隔",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                        item {
                            ReclamationButtonGroup(
                                label = "增加方式",
                                options = ReclamationConfig.INCREMENT_MODE_OPTIONS,
                                selectedValue = config.incrementMode,
                                onValueChange = { onConfigChange(config.copy(incrementMode = it as Int)) },
                                enabled = isArchiveMode
                            )
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "单次最大组装轮数",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                ITextField(
                                    value = config.maxCraftCountPerRound.toString(),
                                    onValueChange = {
                                        val newValue = it.toIntOrNull()
                                        if (newValue != null && newValue > 0) {
                                            onConfigChange(config.copy(maxCraftCountPerRound = newValue))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = "16",
                                    singleLine = true,
                                    enabled = isArchiveMode
                                )
                            }
                        }
                    }

                    // 说明 Tab
                    2 -> {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFFFF3E0),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "目前生息演算的支持仍处于中期阶段，使用时请注意以下几点。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFF57C00),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "通过开局刷点数（小心已有存档被删除）",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "1. 点数刷满后会输出「任务完成」，自动停止任务",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Text(
                                    "2. 不要在生息演算的编队中有干员的情况下使用",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Text(
                                    "3. 手动确认存档情况并删除，以免 MAA 删除你的珍贵存档",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFE65100)
                                )
                                Text(
                                    "4. 导航还没写，不能从生息演算以外的位置开始任务",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Text(
                                    "5. 如果任务过程中报错、点数没刷满就任务完成，请前往 GitHub 提交 Issue",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "有存档通过制造刷点数（高级设置）",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "1. 要求是结算后的第一天，且后续三天没有敌袭进入驻扎地",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Text(
                                    "2. 必须在进入大地图后的界面开始（能看到驻扎地的界面）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Text(
                                    "3. 如果能制造的数量刚好是 99 的倍数会卡住，在存档前可以先用掉一点",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReclamationButtonGroup(
    label: String,
    options: List<Pair<Any, String>>,
    selectedValue: Any,
    onValueChange: (Any) -> Unit,
    enabled: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { (value, displayName) ->
                val isSelected = value == selectedValue
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = enabled) { onValueChange(value) },
                    color = when {
                        !enabled -> Color(0xFFE0E0E0)
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> Color(0xFFE0E0E0)
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            !enabled -> Color.LightGray
                            isSelected -> Color.White
                            else -> Color.DarkGray
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
