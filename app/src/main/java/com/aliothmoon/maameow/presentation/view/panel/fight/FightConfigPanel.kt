package com.aliothmoon.maameow.presentation.view.panel.fight

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.model.FightConfig
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
import kotlinx.coroutines.launch
import org.koin.compose.koinInject


@Composable
fun FightConfigPanel(
    config: FightConfig,
    onConfigChange: (FightConfig) -> Unit,
    modifier: Modifier = Modifier,
    resourceManager: ActivityManager = koinInject(),
    itemHelper: ItemHelper = koinInject()
) {
    // 资源收集
    val resourceCollectionInfo by resourceManager.resourceCollection.collectAsState()
    val isResourceCollectionOpen = resourceCollectionInfo?.isOpen == true

    val dropItemsList by itemHelper.dropItems.collectAsState()
    val allStageItems = remember { resourceManager.getMergedStageList(filterByToday = false) }
    val stageTips = remember { resourceManager.getStageTips() }

    // 分组列表 -- 依赖 hideUnavailableStage
    val stageGroups = remember(config.hideUnavailableStage) {
        resourceManager.getMergedStageGroups(config.hideUnavailableStage)
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
                                stageTips = stageTips
                            )
                        }
                        item {
                            // 理智药/源石/次数
                            MedicineAndStoneSection(config, onConfigChange)
                        }
                        item {
                            HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)
                        }
                        item {
                            // 指定材料掉落
                            SpecifiedDropsSection(
                                config, onConfigChange,
                                dropItemsList
                            )
                        }
                        item {
                            HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)
                        }
                        // 代理倍率（HideSeries=false 时显示）
                        if (!config.hideSeries) {
                            item {
                                SeriesSection(config, onConfigChange)
                            }
                            item {
                                HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)
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
                                label = "博朗台模式",
                                tipText = "等待理智恢复后再开始行动，在理智即将溢出时开始作战（无限吃药时不生效）"
                            )
                        }
                        item {
                            // 自定义关卡代码
                            CheckBoxWithExpandableTip(
                                checked = config.customStageCode,
                                onCheckedChange = { onConfigChange(config.copy(customStageCode = it)) },
                                label = "自定义关卡代码",
                                tipText = "启用后可手动输入关卡代码，支持简写（如\"龙门币\"→\"CE-6\"）"
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
                                            // 启用备选关卡时，自动禁用隐藏不可用关卡
                                            hideUnavailableStage = if (it) false else config.hideUnavailableStage
                                        )
                                    )
                                },
                                label = "使用备选关卡",
                                tipText = "首选关卡不可用时自动使用备选关卡"
                            )
                        }
                        item {
                            // 使用剩余理智关卡
                            CheckBoxWithExpandableTip(
                                checked = config.useRemainingSanityStage,
                                onCheckedChange = {
                                    onConfigChange(
                                        config.copy(
                                            useRemainingSanityStage = it
                                        )
                                    )
                                },
                                label = "使用剩余理智关卡",
                                tipText = "主要关卡刷完后，将剩余理智用于指定关卡"
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
                                label = "使用即将过期的理智药",
                                tipText = "优先使用48小时内过期的理智药"
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
                                            // 启用隐藏不可用关卡时，自动禁用使用备选关卡
                                            useAlternateStage = if (it) false else config.useAlternateStage
                                        )
                                    )
                                },
                                label = "隐藏不可用关卡",
                                tipText = "在关卡列表中隐藏当前不可用的关卡"
                            )
                        }
                        item {
                            // 隐藏代理倍率
                            CheckBoxWithLabel(
                                checked = config.hideSeries,
                                onCheckedChange = { onConfigChange(config.copy(hideSeries = it)) },
                                label = "隐藏代理倍率"
                            )
                        }
                        item {
                            // 游戏掉线时自动重连
                            CheckBoxWithExpandableTip(
                                checked = config.autoRestartOnDrop,
                                onCheckedChange = { onConfigChange(config.copy(autoRestartOnDrop = it)) },
                                label = "游戏掉线时自动重连",
                                tipText = "检测到游戏掉线时会自动尝试重连并继续作战"
                            )
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
    val seriesTipText = """
• AUTO:
自动识别关卡最大代理倍率, 保持最大代理倍率且使用理智药后理智不溢出

• 数值 (1~6):
按设定倍率执行代理
若当前理智不足完成设定倍率 (如仅够 5 次但设为 6 倍), 则无法进入战斗
 或者
战斗次数不足完成设定倍率 (如仅够 5 次但设为 6 倍), 也将无法进入战斗

• 不切换:
不调整游戏内代理倍率设定
    """.trimIndent()



    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "代理倍率",
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

    // 完整关卡代码列表（用于剩余理智关卡，排除「当前/上次」选项）
    val allStageCodes = remember(allStageItems) {
        allStageItems.filter { it.code.isNotEmpty() }.map { it.code }
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

    // 剩余理智关卡列表（使用完整列表 + "不使用"选项）
    // 注意：这里的空字符串表示「不使用」，与首选关卡的空字符串（当前/上次）含义不同
    val remainingStageCodes = remember(allStageCodes) {
        listOf("") + allStageCodes
    }

    // 关卡代码到显示名称的映射函数
    val stageDisplayMapper: (String) -> String = { code ->
        if (code.isEmpty()) {
            "不使用"
        } else {
            stageMap[code]?.displayName ?: code
        }
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
                    text = "关卡选择",
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
                    tipText = "可手动输入关卡代码，支持简写\n例如：龙门币→CE-6，经验→LS-6"
                )
            }
        }

        // 首选关卡
        if (config.customStageCode) {
            // 文本输入模式
            StageInputField(
                value = config.stage1,
                onValueChange = { onConfigChange(config.copy(stage1 = it)) },
                label = "首选关卡",
                placeholder = "例如: CE-6 或 龙门币",
                stageCodes = stageCodes
            )
        } else {
            // 分组按钮选择模式
            GroupedStageButtonGroup(
                label = "首选关卡",
                selectedValue = config.stage1,
                stageGroups = stageGroups,
                onItemSelected = { onConfigChange(config.copy(stage1 = it)) }
            )
        }

        // 首选关卡不开放时显示警告
        if (!stage1Open && config.stage1.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFFF3E0),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "「${config.stage1}」今日不开放" +
                            if (config.useAlternateStage) "，将使用备选关卡" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF57C00),
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
                    label = "备选关卡2",
                    placeholder = "例如: CE-5",
                    stageCodes = stageCodes
                )
                StageInputField(
                    value = config.stage3,
                    onValueChange = { onConfigChange(config.copy(stage3 = it)) },
                    label = "备选关卡3",
                    placeholder = "例如: CE-4",
                    stageCodes = stageCodes
                )
                StageInputField(
                    value = config.stage4,
                    onValueChange = { onConfigChange(config.copy(stage4 = it)) },
                    label = "备选关卡4",
                    placeholder = "例如: CE-3",
                    stageCodes = stageCodes
                )
            } else {
                GroupedStageButtonGroup(
                    label = "备选关卡2",
                    selectedValue = config.stage2,
                    stageGroups = stageGroups,
                    onItemSelected = { onConfigChange(config.copy(stage2 = it)) }
                )
                GroupedStageButtonGroup(
                    label = "备选关卡3",
                    selectedValue = config.stage3,
                    stageGroups = stageGroups,
                    onItemSelected = { onConfigChange(config.copy(stage3 = it)) }
                )
                GroupedStageButtonGroup(
                    label = "备选关卡4",
                    selectedValue = config.stage4,
                    stageGroups = stageGroups,
                    onItemSelected = { onConfigChange(config.copy(stage4 = it)) }
                )
            }
        }

        // 剩余理智关卡（UseRemainingSanityStage 启用时显示）
        if (config.useRemainingSanityStage) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFE3F2FD),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "在完成主任务后刷取指定关卡，不使用理智药/碎石",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1976D2),
                    modifier = Modifier.padding(8.dp)
                )
            }

            if (config.customStageCode) {
                StageInputField(
                    value = config.remainingSanityStage,
                    onValueChange = { onConfigChange(config.copy(remainingSanityStage = it)) },
                    label = "剩余理智关卡",
                    placeholder = "留空表示不使用",
                    stageCodes = stageCodes
                )
            } else {
                // 剩余理智关卡使用扁平列表（带"不使用"选项）
                StageButtonGroup(
                    label = "剩余理智关卡",
                    selectedValue = config.remainingSanityStage,
                    items = remainingStageCodes,
                    onItemSelected = { onConfigChange(config.copy(remainingSanityStage = it)) },
                    displayMapper = stageDisplayMapper,
                    isOpenCheck = ::isStageOpenToday
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
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
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
                            !isOpen -> Color(0xFFF0F0F0)
                            else -> Color(0xFFE0E0E0)
                        },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = stage.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                isSelected -> Color.White
                                !isOpen -> Color.LightGray
                                else -> Color.DarkGray
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
            label = "使用自定义剿灭"
        )

        // 剿灭关卡选择（启用时显示）
        AnimatedVisibility(
            visible = config.useCustomAnnihilation,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "剿灭关卡",
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
                { Text("将转换为: $convertedCode", color = MaterialTheme.colorScheme.primary) }
            } else null
        )
    }
}



