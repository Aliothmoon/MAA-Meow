package com.aliothmoon.maameow.presentation.view.panel.fight

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.data.model.StageResetMode
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.StageAliasMapper
import com.aliothmoon.maameow.data.resource.StageGroup
import com.aliothmoon.maameow.data.resource.StageItem
import com.aliothmoon.maameow.domain.enums.UiUsageConstants
import com.aliothmoon.maameow.presentation.components.CheckBoxWithExpandableTip
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.ITextFieldWithFocus
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon
import com.aliothmoon.maameow.theme.MaaThemeAlphas
import com.aliothmoon.maameow.R
import kotlinx.coroutines.launch
import org.koin.compose.koinInject


@Composable
fun FightConfigPanel(
    config: FightConfig,
    onConfigChange: (FightConfig) -> Unit,
    modifier: Modifier = Modifier,
    activityManager: ActivityManager = koinInject(),
    itemHelper: ItemHelper = koinInject()
) {
    // 资源收集
    val resourceCollectionInfo by activityManager.resourceCollection.collectAsStateWithLifecycle()
    val isResourceCollectionOpen = resourceCollectionInfo?.isOpen == true

    val dropItemsList by itemHelper.dropItems.collectAsStateWithLifecycle()
    val allStageItems = remember { activityManager.getMergedStageList(filterByToday = false) }
    val stageTips = remember { activityManager.getStageTips() }
    val todayName = remember { activityManager.getYjDayOfWeekName() }

    // 分组列表 -- 依赖 hideUnavailableStage
    val stageGroups = remember(config.hideUnavailableStage) {
        activityManager.getMergedStageGroups(config.hideUnavailableStage)
    }


    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 4.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp)
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
                text = stringResource(R.string.tab_general),
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
                text = stringResource(R.string.tab_advanced),
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
            modifier = Modifier.padding(
                top = 2.dp,
                bottom = 4.dp
            )
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
                modifier = Modifier.fillMaxSize()
            ) {
                when (page) {
                    // 常规设置 Tab
                    0 -> {
                        // 今日开放关卡提示
                        item {
                            TodayStagesHint(
                                stageGroups = stageGroups,
                                isResourceCollectionOpen = isResourceCollectionOpen,
                                stageTips = stageTips,
                                todayName = todayName
                            )
                        }
                        item {
                            // 理智药/源石/次数
                            MedicineAndStoneSection(config, onConfigChange)
                        }
                        item {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        }
                        item {
                            // 指定材料掉落
                            SpecifiedDropsSection(
                                config, onConfigChange,
                                dropItemsList
                            )
                        }
                        item {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        }
                        // 代理倍率（HideSeries=false 时显示）
                        if (!config.hideSeries) {
                            item {
                                SeriesSection(config, onConfigChange)
                            }
                            item {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                            }
                        }
                        item {
                            // 关卡选择
                            // stageGroups: 分组后的关卡列表（用于分组显示）
                            // allStageItems: 完整列表（用于剩余理智关卡选择）
                            GroupedStageSelectionSection(
                                config = config,
                                onConfigChange = onConfigChange,
                                stageGroups = stageGroups,
                                allStageItems = allStageItems
                            )
                        }
                    }

                    // 高级设置 Tab
                    else -> {
                        item {
                            // 自定义剿灭
                            CustomAnnihilationSection(config, onConfigChange)
                        }
                        item {
                            // 博朗台模式
                            CheckBoxWithExpandableTip(
                                checked = config.isDrGrandet,
                                onCheckedChange = { onConfigChange(config.copy(isDrGrandet = it)) },
                                label = stringResource(R.string.bulong_tai_mode),
                                tipText = stringResource(R.string.bulong_tai_tip)
                            )
                        }
                        item {
                            // 自定义关卡代码
                            CheckBoxWithExpandableTip(
                                checked = config.customStageCode,
                                onCheckedChange = { onConfigChange(config.copy(customStageCode = it)) },
                                label = stringResource(R.string.custom_stage_code),
                                tipText = stringResource(R.string.custom_stage_tip)
                            )
                        }
                        item {
                            // 使用备选关卡
                            CheckBoxWithExpandableTip(
                                checked = config.useAlternateStage,
                                onCheckedChange = {
                                    onConfigChange(
                                        config.copy(
                                            useAlternateStage = it,
                                            // 启用备选关卡时，自动禁用隐藏不可用关卡，重置策略设为 IGNORE
                                            hideUnavailableStage = if (it) false else config.hideUnavailableStage,
                                            stageResetMode = if (it) StageResetMode.IGNORE else config.stageResetMode
                                        )
                                    )
                                },
                                label = stringResource(R.string.use_alternate_stage),
                                tipText = stringResource(R.string.use_alternate_stage_tip)
                            )
                        }
                        // TODO 暂时关闭 源石使用
//                        item {
//                            // 允许保存源石使用
//                            AllowUseStoneSaveSection(config, onConfigChange)
//                        }
                        item {
                            // 使用即将过期的理智药
                            CheckBoxWithExpandableTip(
                                checked = config.useExpiringMedicine,
                                onCheckedChange = { onConfigChange(config.copy(useExpiringMedicine = it)) },
                                label = stringResource(R.string.use_expiring_medicine),
                                tipText = stringResource(R.string.use_expiring_medicine_tip)
                            )
                        }
                        item {
                            // 隐藏不可用关卡
                            CheckBoxWithExpandableTip(
                                checked = config.hideUnavailableStage,
                                onCheckedChange = {
                                    onConfigChange(
                                        config.copy(
                                            hideUnavailableStage = it,
                                            // 启用隐藏不可用关卡时，自动禁用使用备选关卡，重置策略设为 CURRENT
                                            useAlternateStage = if (it) false else config.useAlternateStage,
                                            stageResetMode = if (it) StageResetMode.CURRENT else config.stageResetMode
                                        )
                                    )
                                },
                                label = stringResource(R.string.hide_unavailable_stages),
                                tipText = stringResource(R.string.hide_unavailable_stages_tip)
                            )
                        }
                        item {
                            // 未开放关卡重置策略
                            StageResetModeSection(config, onConfigChange)
                        }
                        item {
                            // 隐藏代理倍率
                            CheckBoxWithLabel(
                                checked = config.hideSeries,
                                onCheckedChange = { onConfigChange(config.copy(hideSeries = it)) },
                                label = stringResource(R.string.hide_proxy_ratio)
                            )
                        }
                        item {
                            // 游戏掉线时自动重连
                            CheckBoxWithExpandableTip(
                                checked = config.autoRestartOnDrop,
                                onCheckedChange = { onConfigChange(config.copy(autoRestartOnDrop = it)) },
                                label = stringResource(R.string.auto_reconnect),
                                tipText = stringResource(R.string.auto_reconnect_tip)
                            )
                        }
                        item {
                            WeeklyScheduleSection(config, onConfigChange)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 代理倍率选择区域
 * 使用 RadioButton 单选按钮组，FlowRow 自动换行
 */
@Composable
private fun SeriesSection(
    config: FightConfig,
    onConfigChange: (FightConfig) -> Unit
) {
    var tipExpanded by remember { mutableStateOf(false) }
    val seriesTipText = stringResource(R.string.proxy_ratio_tip)



    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.proxy_ratio),
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
            tipText = seriesTipText
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            UiUsageConstants.seriesOptions.forEach { (value, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .width(72.dp)
                        .clickable { onConfigChange(config.copy(series = value)) }
                ) {
                    RadioButton(
                        selected = config.series == value,
                        onClick = { onConfigChange(config.copy(series = value)) },
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * 未开放关卡重置策略选择
 * 迁移自 WPF FightStageResetMode 下拉框
 */
@Composable
private fun StageResetModeSection(
    config: FightConfig,
    onConfigChange: (FightConfig) -> Unit
) {
    val options = listOf(
        StageResetMode.CURRENT to stringResource(R.string.stage_reset_current),
        StageResetMode.IGNORE to stringResource(R.string.stage_reset_ignore)
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.unopened_stage_fallback),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { (mode, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .width(100.dp)
                        .clickable { onConfigChange(config.copy(stageResetMode = mode)) }
                ) {
                    RadioButton(
                        selected = config.stageResetMode == mode,
                        onClick = { onConfigChange(config.copy(stageResetMode = mode)) },
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * 分组关卡选择区域（新版）
 * 支持活动关卡和常驻关卡分组显示
 *
 * @param stageGroups 分组后的关卡列表
 * @param allStageItems 完整关卡列表（用于剩余理智关卡选择）
 */
@Composable
private fun GroupedStageSelectionSection(
    config: FightConfig,
    onConfigChange: (FightConfig) -> Unit,
    stageGroups: List<StageGroup>,
    allStageItems: List<StageItem>
) {
    var tipExpanded by remember { mutableStateOf(false) }

    // 扁平的关卡代码列表（用于输入框模式）
    val stageCodes = remember(stageGroups) {
        stageGroups.flatMap { group -> group.stages.map { it.code } }
    }

    // 构建关卡代码到 StageItem 的映射
    val stageMap = remember(allStageItems) {
        allStageItems.associateBy { it.code }
    }

    // 检查关卡是否今日开放
    fun isStageOpenToday(stageCode: String): Boolean {
        if (stageCode.isBlank()) return true
        return stageMap[stageCode]?.isOpenToday ?: true
    }

    // 检查首选关卡开放状态
    val stage1Open = isStageOpenToday(config.stage1)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.stage_selection),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (config.customStageCode) {
                    ExpandableTipIcon(
                        expanded = tipExpanded,
                        onExpandedChange = { tipExpanded = it }
                    )
                }
            }
            if (config.customStageCode) {
                ExpandableTipContent(
                    visible = tipExpanded,
                    tipText = stringResource(R.string.stage_manual_input_tip)
                )
            }
        }

        // 首选关卡
        if (config.customStageCode) {
            // 文本输入模式
            StageInputField(
                value = config.stage1,
                onValueChange = { onConfigChange(config.copy(stage1 = it)) },
                label = stringResource(R.string.preferred_stage),
                placeholder = stringResource(R.string.preferred_stage_placeholder),
                stageCodes = stageCodes
            )
        } else {
            // 分组按钮选择模式
            GroupedStageButtonGroup(
                label = stringResource(R.string.preferred_stage),
                selectedValue = config.stage1,
                stageGroups = stageGroups,
                onItemSelected = { onConfigChange(config.copy(stage1 = it)) },
                annihilationDisplayName = if (config.useCustomAnnihilation) {
                    UiUsageConstants.annihilations
                        .firstOrNull { it.second == config.annihilationStage }
                        ?.first
                } else null
            )
        }

        // 首选关卡不开放时显示警告
        if (!stage1Open && config.stage1.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.stage_not_open_today, config.stage1) +
                        if (config.useAlternateStage) stringResource(R.string.will_use_alternate) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        // 备选关卡（UseAlternateStage 启用时显示）
        if (config.useAlternateStage) {
            if (config.customStageCode) {
                StageInputField(
                    value = config.stage2,
                    onValueChange = { onConfigChange(config.copy(stage2 = it)) },
                    label = stringResource(R.string.alternate_stage_2),
                    placeholder = stringResource(R.string.alternate_stage_2_placeholder),
                    stageCodes = stageCodes
                )
                StageInputField(
                    value = config.stage3,
                    onValueChange = { onConfigChange(config.copy(stage3 = it)) },
                    label = stringResource(R.string.alternate_stage_3),
                    placeholder = stringResource(R.string.alternate_stage_3_placeholder),
                    stageCodes = stageCodes
                )
                StageInputField(
                    value = config.stage4,
                    onValueChange = { onConfigChange(config.copy(stage4 = it)) },
                    label = stringResource(R.string.alternate_stage_4),
                    placeholder = stringResource(R.string.alternate_stage_4_placeholder),
                    stageCodes = stageCodes
                )
            } else {
                GroupedStageButtonGroup(
                    label = stringResource(R.string.alternate_stage_2),
                    selectedValue = config.stage2,
                    stageGroups = stageGroups,
                    onItemSelected = { onConfigChange(config.copy(stage2 = it)) }
                )
                GroupedStageButtonGroup(
                    label = stringResource(R.string.alternate_stage_3),
                    selectedValue = config.stage3,
                    stageGroups = stageGroups,
                    onItemSelected = { onConfigChange(config.copy(stage3 = it)) }
                )
                GroupedStageButtonGroup(
                    label = stringResource(R.string.alternate_stage_4),
                    selectedValue = config.stage4,
                    stageGroups = stageGroups,
                    onItemSelected = { onConfigChange(config.copy(stage4 = it)) }
                )
            }
        }

    }
}

/**
 * 分组关卡选择按钮组
 * 显示分组标题，每个分组下的关卡自动换行平铺
 */
@Composable
private fun GroupedStageButtonGroup(
    label: String,
    selectedValue: String,
    stageGroups: List<StageGroup>,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    annihilationDisplayName: String? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 显示每个分组
        stageGroups.forEach { group ->
            // 分组标题
            Text(
                text = group.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (group.title == "常驻关卡") Color(0xFF388E3C) else Color(0xFFE65100),
                modifier = Modifier.padding(top = 4.dp)
            )

            // 分组内的关卡（自动换行平铺）
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                group.stages.forEach { stage ->
                    val isSelected = stage.code == selectedValue
                    val isOpen = stage.isOpenToday
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onItemSelected(stage.code) },
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            !isOpen -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = if (stage.code == "Annihilation" && annihilationDisplayName != null) {
                                annihilationDisplayName
                            } else {
                                stage.displayName
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                !isOpen -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 自定义剿灭区域
 * 使用 RadioButton 按钮组替代下拉框
 */
@Composable
private fun CustomAnnihilationSection(
    config: FightConfig,
    onConfigChange: (FightConfig) -> Unit
) {

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CheckBoxWithLabel(
            checked = config.useCustomAnnihilation,
            onCheckedChange = { onConfigChange(config.copy(useCustomAnnihilation = it)) },
            label = stringResource(R.string.use_custom_annihilation)
        )

        // 剿灭关卡选择（启用时显示）
        AnimatedVisibility(
            visible = config.useCustomAnnihilation,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.annihilation_stage),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UiUsageConstants.annihilations.forEach { (displayName, value) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { onConfigChange(config.copy(annihilationStage = value)) }
                        ) {
                            RadioButton(
                                selected = config.annihilationStage == value,
                                onClick = { onConfigChange(config.copy(annihilationStage = value)) },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}


/**
 * 关卡代码输入框
 * 支持别名自动映射：失去焦点时自动转换别名为实际关卡代码
 *
 * 例如：龙门币 → CE-6，经验 → LS-6
 *
 */
@Composable
private fun StageInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    stageCodes: List<String>,
    modifier: Modifier = Modifier
) {
    var textValue by remember(value) { mutableStateOf(value) }
    var showConvertedHint by remember { mutableStateOf(false) }
    var convertedCode by remember { mutableStateOf("") }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ITextFieldWithFocus(
            value = textValue,
            onValueChange = { newValue ->
                textValue = newValue
                // 检查是否是已知别名，显示转换提示
                val mapped = StageAliasMapper.mapToStageCode(newValue, stageCodes)
                if (mapped != newValue.uppercase() && newValue.isNotBlank()) {
                    showConvertedHint = true
                    convertedCode = mapped
                } else {
                    showConvertedHint = false
                }
            },
            onFocusLost = {
                if (textValue.isNotBlank()) {
                    // 失去焦点时应用别名映射
                    val mapped = StageAliasMapper.mapToStageCode(textValue, stageCodes)
                    textValue = mapped
                    onValueChange(mapped)
                    showConvertedHint = false
                }
            },
            label = label,
            placeholder = placeholder,
            singleLine = true,
            supportingText = if (showConvertedHint) {
                {
                    Text(
                        stringResource(R.string.will_convert_to, convertedCode),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else null
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeeklyScheduleSection(
    config: FightConfig,
    onConfigChange: (FightConfig) -> Unit
) {
    val weekDays = listOf(
        "MONDAY" to stringResource(R.string.day_mon),
        "TUESDAY" to stringResource(R.string.day_tue),
        "WEDNESDAY" to stringResource(R.string.day_wed),
        "THURSDAY" to stringResource(R.string.day_thu),
        "FRIDAY" to stringResource(R.string.day_fri),
        "SATURDAY" to stringResource(R.string.day_sat),
        "SUNDAY" to stringResource(R.string.day_sun),
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CheckBoxWithExpandableTip(
            checked = config.useWeeklySchedule,
            onCheckedChange = { onConfigChange(config.copy(useWeeklySchedule = it)) },
            label = stringResource(R.string.enable_weekly_plan),
            tipText = stringResource(R.string.enable_weekly_plan_tip)
        )
        AnimatedVisibility(
            visible = config.useWeeklySchedule,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 4.dp)
            ) {
                weekDays.forEach { (key, display) ->
                    val selected = config.weeklySchedule[key] != false
                    Surface(
                        onClick = {
                            val updated = config.weeklySchedule.toMutableMap()
                            updated[key] = !selected
                            onConfigChange(config.copy(weeklySchedule = updated))
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = if (selected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Text(
                            text = display,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
