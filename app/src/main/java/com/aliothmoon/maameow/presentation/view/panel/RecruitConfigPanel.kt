package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.model.RecruitConfig
import com.aliothmoon.maameow.presentation.components.INumericField
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon
import kotlinx.coroutines.launch

/**
 * 自动公招配置面板
 *
 *
 * WPF源文件: RecruitSettingsUserControl.xaml
 * WPF ViewModel: RecruitSettingsUserControlModel.cs
 */
@Composable
fun RecruitConfigPanel(
    config: RecruitConfig,
    onConfigChange: (RecruitConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 4.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val pagerState = rememberPagerState(
            initialPage = 0,
            pageCount = { 2 }
        )
        val coroutineScope = rememberCoroutineScope()

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
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(0)
                    }
                }
            )
            Text(
                text = "高级设置",
                style = MaterialTheme.typography.bodyMedium,
                color = if (pagerState.currentPage == 1) MaterialTheme.colorScheme.primary else Color.Gray,
                fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(1)
                    }
                }
            )
        }
        HorizontalDivider(
            modifier = modifier.padding(
                top = 2.dp,
                bottom = 4.dp
            )
        )

        HorizontalPager(
            pageSize = PageSize.Fill,
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),  // 使用 weight(1f) 让 Pager 占据剩余空间
            userScrollEnabled = true
        ) { page ->
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()  // LazyColumn 填充 Pager 的全部空间
            ) {
                when (page) {
                    0 -> {
                        item {
                            UseExpeditedSection(config, onConfigChange)
                        }
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            RecruitMaxTimesSection(config, onConfigChange)
                        }
                    }

                    else -> {
                        // 高级设置：使用单个 item 包含所有内容，避免 LazyColumn 对 AndroidView 的频繁重组
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // 自动公招选择策略
                                SelectExtraTagsSection(config, onConfigChange)

                                // 高优先级Tag列表
                                AutoRecruitFirstListSection(config, onConfigChange)

                                // 自动刷新3星tags
                                RefreshLevel3Section(config, onConfigChange)

                                // 无招聘许可时继续尝试刷新Tags
                                ForceRefreshSection(config, onConfigChange)

                                // 手动确认1星
                                NotChooseLevel1Section(config, onConfigChange)

                                HorizontalDivider(color = Color.LightGray, thickness = 1.dp)

                                // 自动选择三星
                                ChooseLevel3Section(config, onConfigChange)

                                // 自动选择四星
                                ChooseLevel4Section(config, onConfigChange)

                                // 自动选择五星
                                ChooseLevel5Section(config, onConfigChange)
                            }
                        }
                    }
                }

            }
        }
    }
}


@Composable
private fun UseExpeditedSection(
    config: RecruitConfig,
    onConfigChange: (RecruitConfig) -> Unit
) {
    var tipExpanded by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = config.useExpedited,
                onCheckedChange = { onConfigChange(config.copy(useExpedited = it)) },
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = "自动使用加急许可",
                style = MaterialTheme.typography.bodyMedium
            )

            ExpandableTipIcon(
                expanded = tipExpanded,
                onExpandedChange = { tipExpanded = it }
            )
        }
        ExpandableTipContent(
            visible = tipExpanded,
            tipText = "此选项不会被保存，每次任务开始前需重新勾选",
            modifier = Modifier.padding(start = 28.dp)
        )
    }
}


@Composable
private fun RecruitMaxTimesSection(
    config: RecruitConfig,
    onConfigChange: (RecruitConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "招募次数",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        INumericField(
            value = config.maxRecruitTimes,
            onValueChange = { onConfigChange(config.copy(maxRecruitTimes = it)) },
            minimum = 0,
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(56.dp)
        )

        Text(
            text = "单次任务最多进行的公招次数（0表示不限制）",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}


/**
 * 自动公招选择策略
 * WPF: ComboBox with AutoRecruitSelectExtraTagsList
 * 改用 RadioButton 单选按钮组
 */
@Composable
private fun SelectExtraTagsSection(
    config: RecruitConfig,
    onConfigChange: (RecruitConfig) -> Unit
) {
    // 选项列表（AutoRecruitSelectExtraTagsList）
    val options = listOf(
        "0" to "默认不选额外标签",
        "1" to "选择额外标签",
        "2" to "仅选择稀有标签"
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            modifier = Modifier.padding(vertical = 2.dp),
            text = "公招多选 Tag 的策略",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        Column(
            modifier = Modifier.padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            options.forEach { (value, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onConfigChange(config.copy(selectExtraTags = value)) }
                ) {
                    RadioButton(
                        selected = config.selectExtraTags == value,
                        onClick = { onConfigChange(config.copy(selectExtraTags = value)) },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * 3星Tag倾向（多选）
 * WPF: CheckComboBox with AutoRecruitFirstList binding + AutoRecruitTagShowList
 */
@Composable
private fun AutoRecruitFirstListSection(
    config: RecruitConfig,
    onConfigChange: (RecruitConfig) -> Unit
) {
    // 可选Tag列表（AutoRecruitTagShowList）
    val availableTags = listOf(
        "治疗", "输出", "生存", "群攻", "防护", "减速",
        "近战位", "远程位",
        "费用回复",
        "先锋干员", "近卫干员", "狙击干员", "重装干员", "医疗干员", "辅助干员", "术师干员"
    )


    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var tipExpanded by remember { mutableStateOf(false) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "3 星 Tag 倾向",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            ExpandableTipIcon(
                expanded = tipExpanded,
                onExpandedChange = { tipExpanded = it }
            )
        }

        ExpandableTipContent(
            visible = tipExpanded,
            tipText = "当只能匹配 3 星干员时，会尽可能多地选择倾向的 Tag"
        )

        // 多选标签面板 - 使用 FlowRow 自动换行布局
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFFF5F5F5),
            border = BorderStroke(1.dp, Color.LightGray)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    availableTags.forEach { tag ->
                        val isSelected = config.autoRecruitFirstList.contains(tag)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val newList = if (isSelected) {
                                    config.autoRecruitFirstList - tag
                                } else {
                                    config.autoRecruitFirstList + tag
                                }
                                onConfigChange(config.copy(autoRecruitFirstList = newList))
                            },
                            label = {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null,
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                // 已选择计数
                if (config.autoRecruitFirstList.isNotEmpty()) {
                    Text(
                        text = "已选 ${config.autoRecruitFirstList.size} 个",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * 刷新三星Tags
 * WPF: CheckBox with RefreshLevel3 binding
 */
@Composable
private fun RefreshLevel3Section(
    config: RecruitConfig,
    onConfigChange: (RecruitConfig) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = config.refreshLevel3,
            onCheckedChange = { onConfigChange(config.copy(refreshLevel3 = it)) },
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "自动刷新3星Tags",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * 无招聘许可时继续尝试刷新Tags（依赖RefreshLevel3）
 * WPF: CheckBox with ForceRefresh binding, enabled by RefreshLevel3
 */
@Composable
private fun ForceRefreshSection(
    config: RecruitConfig,
    onConfigChange: (RecruitConfig) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (config.refreshLevel3) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = config.forceRefresh,
            onCheckedChange = { if (config.refreshLevel3) onConfigChange(config.copy(forceRefresh = it)) },
            enabled = config.refreshLevel3,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "无招聘许可时继续尝试刷新 Tags",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * 不选一星
 * WPF: CheckBox with NotChooseLevel1 binding + TooltipBlock
 */
@Composable
private fun NotChooseLevel1Section(
    config: RecruitConfig,
    onConfigChange: (RecruitConfig) -> Unit
) {
    var tipExpanded by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = config.notChooseLevel1,
                onCheckedChange = { onConfigChange(config.copy(notChooseLevel1 = it)) },
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "手动确认 1 星",
                style = MaterialTheme.typography.bodyMedium
            )
            ExpandableTipIcon(
                expanded = tipExpanded,
                onExpandedChange = { tipExpanded = it }
            )
        }
        ExpandableTipContent(
            visible = tipExpanded,
            tipText = "勾选时识别到1星词条时跳过该次招募，未勾选时将忽略1星词条",
            modifier = Modifier.padding(start = 28.dp)
        )
    }
}

/**
 * 自动选择三星 + 时长设置
 * WPF: CheckBox + 两个NumericUpDown (Hour + Min)
 */
@Composable
private fun ChooseLevel3Section(
    config: RecruitConfig,
    onConfigChange: (RecruitConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 复选框
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = config.chooseLevel3,
                onCheckedChange = { onConfigChange(config.copy(chooseLevel3 = it)) },
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "自动确认 3 星",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // 时长选择器
        TimeSelector(
            enabled = config.chooseLevel3,
            hour = config.chooseLevel3Hour,
            minute = config.chooseLevel3Min,
            onTimeChange = { h, m ->
                onConfigChange(config.copy(chooseLevel3Hour = h, chooseLevel3Min = m))
            }
        )
    }
}

/**
 * 自动选择四星 + 时长设置
 * WPF: CheckBox + 两个NumericUpDown (Hour + Min)
 */
@Composable
private fun ChooseLevel4Section(
    config: RecruitConfig,
    onConfigChange: (RecruitConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = config.chooseLevel4,
                onCheckedChange = { onConfigChange(config.copy(chooseLevel4 = it)) },
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "自动确认 4 星",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        TimeSelector(
            enabled = config.chooseLevel4,
            hour = config.chooseLevel4Hour,
            minute = config.chooseLevel4Min,
            onTimeChange = { h, m ->
                onConfigChange(config.copy(chooseLevel4Hour = h, chooseLevel4Min = m))
            }
        )
    }
}

/**
 * 自动选择五星 + 时长设置
 * WPF: CheckBox + 两个NumericUpDown (Hour + Min)
 */
@Composable
private fun ChooseLevel5Section(
    config: RecruitConfig,
    onConfigChange: (RecruitConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = config.chooseLevel5,
                onCheckedChange = { onConfigChange(config.copy(chooseLevel5 = it)) },
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "自动确认 5 星",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        TimeSelector(
            enabled = config.chooseLevel5,
            hour = config.chooseLevel5Hour,
            minute = config.chooseLevel5Min,
            onTimeChange = { h, m ->
                onConfigChange(config.copy(chooseLevel5Hour = h, chooseLevel5Min = m))
            }
        )
    }
}


/**
 * 时长选择器（小时:分钟）
 * WPF: 两个NumericUpDown + 冒号分隔
 *
 * 时间验证逻辑（对应 WPF ChooseLevelXTime 属性）：
 * - 总时长范围: 60-540 分钟
 * - 分钟必须是 10 的倍数
 * - 超出范围时自动修正: < 60 → 540, > 540 → 60
 */
@Composable
private fun TimeSelector(
    enabled: Boolean,
    hour: Int,
    minute: Int,
    onTimeChange: (Int, Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 小时选择器
        INumericField(
            value = hour,
            onValueChange = { newHour ->
                if (enabled) {
                    // 计算新的总时长（分钟）
                    val totalMinutes = newHour * 60 + minute

                    // 公招时间验证
                    val validatedMinutes = when {
                        totalMinutes < 60 -> 540  // 小于 1 小时 → 9 小时
                        totalMinutes > 540 -> 60  // 大于 9 小时 → 1 小时
                        else -> totalMinutes
                    }

                    // 分解为小时和分钟
                    val validatedHour = validatedMinutes / 60
                    val validatedMin = validatedMinutes % 60
                    onTimeChange(validatedHour, validatedMin)
                }
            },
            minimum = 1,
            maximum = 9,
            valueFormat = "%02d",
            enabled = enabled,
            modifier = Modifier
                .width(90.dp)
                .height(60.dp)
        )

        Text(
            text = ":",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // 分钟选择器
        INumericField(
            value = minute,
            onValueChange = { newMinute ->
                if (enabled) {
                    // 计算新的总时长（分钟）
                    val totalMinutes = hour * 60 + newMinute

                    val validatedMinutes = when {
                        totalMinutes < 60 -> 540  // 小于 1 小时 → 9 小时
                        totalMinutes > 540 -> 60  // 大于 9 小时 → 1 小时
                        else -> totalMinutes
                    }

                    // 分解为小时和分钟
                    val validatedHour = validatedMinutes / 60
                    val validatedMin = validatedMinutes % 60
                    onTimeChange(validatedHour, validatedMin)
                }
            },
            minimum = 0,
            maximum = 50,
            increment = 10,
            valueFormat = "%02d",
            enabled = enabled,
            modifier = Modifier
                .width(90.dp)
                .height(60.dp)
        )
    }
}
