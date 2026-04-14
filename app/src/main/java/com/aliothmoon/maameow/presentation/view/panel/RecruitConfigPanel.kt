package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.RecruitConfig
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.presentation.components.INumericField
import com.aliothmoon.maameow.presentation.components.RecruitTimeSelector
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

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
                text = stringResource(R.string.common_tab_general),
                style = MaterialTheme.typography.bodyMedium,
                color = if (pagerState.currentPage == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(0)
                    }
                }
            )
            Text(
                text = stringResource(R.string.common_tab_advanced),
                style = MaterialTheme.typography.bodyMedium,
                color = if (pagerState.currentPage == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

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
                text = stringResource(R.string.panel_recruit_use_expedited),
                style = MaterialTheme.typography.bodyMedium
            )

            ExpandableTipIcon(
                expanded = tipExpanded,
                onExpandedChange = { tipExpanded = it }
            )
        }
        ExpandableTipContent(
            visible = tipExpanded,
            tipText = stringResource(R.string.panel_recruit_use_expedited_tip)
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
            text = stringResource(R.string.panel_recruit_max_times_title),
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
            text = stringResource(R.string.panel_recruit_max_times_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
        "0" to stringResource(R.string.panel_recruit_extra_tags_none),
        "1" to stringResource(R.string.panel_recruit_extra_tags_select),
        "2" to stringResource(R.string.panel_recruit_extra_tags_rare_only)
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            modifier = Modifier.padding(vertical = 2.dp),
            text = stringResource(R.string.panel_recruit_extra_tags_strategy),
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
 */
@Composable
private fun AutoRecruitFirstListSection(
    config: RecruitConfig,
    onConfigChange: (RecruitConfig) -> Unit,
    resourceDataManager: ResourceDataManager = koinInject()
) {
    val recruitTags by resourceDataManager.recruitTags.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var tipExpanded by remember { mutableStateOf(false) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.panel_recruit_level3_preference),
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
            tipText = stringResource(R.string.panel_recruit_level3_preference_tip)
        )

        // 多选标签面板 - 使用 FlowRow 自动换行布局
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ResourceDataManager.AUTO_RECRUIT_TAG_KEYS.mapNotNull { key ->
                        recruitTags[key]
                    }.forEach { (display, client) ->
                        val isSelected = config.autoRecruitFirstList.contains(client)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val newList = if (isSelected) {
                                    config.autoRecruitFirstList - client
                                } else {
                                    config.autoRecruitFirstList + client
                                }
                                onConfigChange(config.copy(autoRecruitFirstList = newList))
                            },
                            label = {
                                Text(
                                    text = display,
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
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            border = null,
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                // 已选择计数
                if (config.autoRecruitFirstList.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.panel_recruit_selected_count, config.autoRecruitFirstList.size),
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
            text = stringResource(R.string.panel_recruit_refresh_level3),
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
            text = stringResource(R.string.panel_recruit_force_refresh),
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
                text = stringResource(R.string.panel_recruit_not_choose_level1),
                style = MaterialTheme.typography.bodyMedium
            )
            ExpandableTipIcon(
                expanded = tipExpanded,
                onExpandedChange = { tipExpanded = it }
            )
        }
        ExpandableTipContent(
            visible = tipExpanded,
            tipText = stringResource(R.string.panel_recruit_not_choose_level1_tip)
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
                text = stringResource(R.string.panel_recruit_choose_level3),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // 时长选择器
        RecruitTimeSelector(
            enabled = config.chooseLevel3,
            totalMinutes = config.chooseLevel3Hour * 60 + config.chooseLevel3Min,
            onTimeChange = { total ->
                onConfigChange(config.copy(
                    chooseLevel3Hour = total / 60,
                    chooseLevel3Min = total % 60
                ))
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
                text = stringResource(R.string.panel_recruit_choose_level4),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        RecruitTimeSelector(
            enabled = config.chooseLevel4,
            totalMinutes = config.chooseLevel4Hour * 60 + config.chooseLevel4Min,
            onTimeChange = { total ->
                onConfigChange(config.copy(
                    chooseLevel4Hour = total / 60,
                    chooseLevel4Min = total % 60
                ))
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
                text = stringResource(R.string.panel_recruit_choose_level5),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        RecruitTimeSelector(
            enabled = config.chooseLevel5,
            totalMinutes = config.chooseLevel5Hour * 60 + config.chooseLevel5Min,
            onTimeChange = { total ->
                onConfigChange(config.copy(
                    chooseLevel5Hour = total / 60,
                    chooseLevel5Min = total % 60
                ))
            }
        )
    }
}
