package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.constant.MaaApi
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.model.CustomInfrastConfig
import com.aliothmoon.maameow.data.model.InfrastConfig
import com.aliothmoon.maameow.domain.enums.InfrastMode
import com.aliothmoon.maameow.domain.enums.InfrastRoomType
import com.aliothmoon.maameow.domain.enums.UiUsageConstants
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableColumn
import java.io.File
import java.time.LocalTime

/**
 * 基建换班配置面板
 */
@Composable
fun InfrastConfigPanel(
    config: InfrastConfig,
    onConfigChange: (InfrastConfig) -> Unit,
    modifier: Modifier = Modifier
) {
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
                top = 4.dp,
                bottom = 8.dp
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
                        item {
                            // 基建模式选择
                            InfrastModeSection(config, onConfigChange)
                        }
                        item {
                            // 自定义基建配置 (仅 Custom 模式显示)
                            AnimatedVisibility(
                                visible = config.mode == InfrastMode.Custom,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                CustomInfrastSection(config, onConfigChange)
                            }
                        }
                        item {
                            // 无人机用途 (Custom 模式下禁用)
                            AnimatedVisibility(
                                visible = config.mode != InfrastMode.Custom,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                UsesOfDronesSection(config, onConfigChange)
                            }
                        }
                        item {
                            // 心情阈值 (仅 Normal 模式显示)
                            AnimatedVisibility(
                                visible = config.mode != InfrastMode.Rotation,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                DormThresholdSection(config, onConfigChange)
                            }
                        }
                        item {
                            // 设施列表
                            FacilitiesSection(config, onConfigChange)
                        }
                    }

                    // 高级设置 Tab
                    else -> {
                        item {
                            // 宿舍信赖模式 (仅 Normal 模式显示)
                            AnimatedVisibility(
                                visible = config.mode != InfrastMode.Rotation,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                DormTrustEnabledSection(config, onConfigChange)
                            }
                        }
                        item {
                            // 不将已进驻干员放入宿舍 (仅 Normal 模式显示)
                            AnimatedVisibility(
                                visible = config.mode != InfrastMode.Rotation,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                DormFilterNotStationedSection(config, onConfigChange)
                            }
                        }
                        item {
                            // 制造站搓玉自动补货
                            OriginiumShardAutoReplenishmentSection(config, onConfigChange)
                        }
                        item {
                            // 会客室留言板领取信用
                            ReceptionMessageBoardReceiveSection(config, onConfigChange)
                        }
                        item {
                            // 会客室线索交流
                            ReceptionClueExchangeSection(config, onConfigChange)
                        }
                        item {
                            // 会客室赠送线索
                            ReceptionSendClueSection(config, onConfigChange)
                        }
                        item {
                            // 继续专精
                            ContinueTrainingSection(config, onConfigChange)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 基建模式选择区域
 */
@Composable
private fun InfrastModeSection(
    config: InfrastConfig,
    onConfigChange: (InfrastConfig) -> Unit
) {
    // 模式选项 (对应WPF的InfrastModeList)
    val modeOptions = listOf(
        "Normal" to "常规模式",
        "Rotation" to "轮换模式"
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "基建模式",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfrastMode.values.forEach {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = config.mode == it,
                        onClick = { onConfigChange(config.copy(mode = it)) },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = it.displayName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Rotation 模式提示文字
        AnimatedVisibility(
            visible = config.mode == InfrastMode.Rotation,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF5F5F5),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = """
                        ｢队列轮换｣ 的换班逻辑与游戏内基建右下角的「队列轮换」完全一致：
                        当当前班次中任意干员心情值为 0 时，会整队替换为下一班干员。
                        若需自定义轮换班次，请使用「自定义基建配置」
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

/**
 * 自定义基建配置区域（仅 Custom 模式显示）
 */
@Composable
private fun CustomInfrastSection(
    config: InfrastConfig,
    onConfigChange: (InfrastConfig) -> Unit
) {
    val pathConfig: MaaPathConfig = koinInject()
    val json = remember { Json { ignoreUnknownKeys = true } }

    // 解析后的配置（用于计划下拉框）
    var parsedConfig by remember { mutableStateOf<CustomInfrastConfig?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }

    // 当文件路径变化时解析配置
    LaunchedEffect(config.customInfrastFile) {
        if (config.customInfrastFile.isBlank()) {
            parsedConfig = null
            parseError = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val file = File(config.customInfrastFile)
                if (file.exists()) {
                    val content = file.readText()
                    val parsed = json.decodeFromString<CustomInfrastConfig>(content)
                    parsedConfig = parsed
                    parseError = null
                    // 同步时间段数据到 config 用于 toTaskParams 的时间轮换
                    val periods = parsed.plans.map { it.period }
                    if (periods != config.customPlanPeriods) {
                        onConfigChange(config.copy(customPlanPeriods = periods))
                    }
                } else {
                    parsedConfig = null
                    parseError = "文件不存在"
                }
            } catch (e: Exception) {
                parsedConfig = null
                parseError = "解析失败: ${e.message}"
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val parsed = parsedConfig

        // 1. 配置信息卡片（仅当 parsed 有 title/description 时显示）
        if (parsed != null && parsed.plans.isNotEmpty()) {
            if (!parsed.title.isNullOrBlank() || !parsed.description.isNullOrBlank()) {
                var descExpanded by remember { mutableStateOf(false) }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.clickable(
                        enabled = !parsed.description.isNullOrBlank()
                    ) { descExpanded = !descExpanded }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!parsed.title.isNullOrBlank()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = parsed.title.replace("\\n", "\n"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!parsed.description.isNullOrBlank()) {
                                    ExpandableTipIcon(
                                        expanded = descExpanded,
                                        onExpandedChange = { descExpanded = it }
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = descExpanded && !parsed.description.isNullOrBlank(),
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Text(
                                text = parsed.description?.replace("\\n", "\n") ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. 排班计划选择
            PlanSelectButtonGroup(
                plans = parsed.plans,
                selectedPlanIndex = config.customInfrastPlanSelect,
                onPlanSelected = {
                    onConfigChange(config.copy(customInfrastPlanSelect = it))
                }
            )
        }

        // 3. 在线生成器链接
        val handler = LocalUriHandler.current
        Text(
            text = "自定义基建排班制作器",
            style = MaterialTheme.typography.bodySmall.copy(
                textDecoration = TextDecoration.Underline
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                handler.openUri(MaaApi.BASE_SCHEDULING_SCHEMA)
            }
        )

        // 4. 内置配置选择
        PresetButtonGroup(
            selectedPreset = config.defaultInfrast,
            onPresetSelected = { preset ->
                if (preset == UiUsageConstants.USER_DEFINED_INFRAST) {
                    onConfigChange(
                        config.copy(
                            defaultInfrast = preset,
                            customInfrastFile = "",
                            customInfrastPlanSelect = -1
                        )
                    )
                } else {
                    val filePath = File(
                        pathConfig.resourceDir,
                        "custom_infrast/$preset"
                    ).absolutePath
                    onConfigChange(
                        config.copy(
                            defaultInfrast = preset,
                            customInfrastFile = filePath,
                            customInfrastPlanSelect = -1
                        )
                    )
                }
            }
        )

        // 5. 解析错误提示
        if (parseError != null) {
            Text(
                text = parseError!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * 内置预设按钮组
 */
@Composable
private fun PresetButtonGroup(
    selectedPreset: String,
    onPresetSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "内置配置",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        UiUsageConstants.defaultInfrastPresets.forEach { (key, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onPresetSelected(key) }
            ) {
                RadioButton(
                    selected = selectedPreset == key,
                    onClick = { onPresetSelected(key) },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * 计划选择按钮组
 */
@Composable
private fun PlanSelectButtonGroup(
    plans: List<CustomInfrastConfig.Plan>,
    selectedPlanIndex: Int,
    onPlanSelected: (Int) -> Unit
) {
    val hasPeriodicPlan = plans.any { it.period.isNotEmpty() }
    val hasNonPeriodicPlan = plans.any { it.period.isEmpty() }

    // 计算当前时间匹配的计划名（用于时间轮换显示）
    // TODO: 定时刷新时间轮换显示（WPF 每分钟调用 RefreshInfrastTimeRotationDisplay 更新）
    val currentPlanName = if (hasPeriodicPlan) {
        val now = LocalTime.now()
        val formatter = java.time.format.DateTimeFormatter.ofPattern("H:mm")
        val matched = plans.firstOrNull { plan ->
            plan.period.any { range ->
                if (range.size < 2) return@any false
                val start =
                    runCatching { LocalTime.parse(range[0], formatter) }.getOrNull()
                        ?: return@any false
                val end = runCatching { LocalTime.parse(range[1], formatter) }.getOrNull()
                    ?: return@any false
                if (start <= end) now in start..end
                else now >= start || now <= end
            }
        }
        matched?.name ?: plans.firstOrNull()?.name ?: "???"
    } else null

    var tipExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "排班计划",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            ExpandableTipIcon(
                expanded = tipExpanded,
                onExpandedChange = { tipExpanded = it }
            )
        }

        val tip =
            "如「基建计划」存在执行时间，「基建换班」任务开始前或任务运行时不会自动切换，将保持Link Start!时的状态，仅在空闲或任务完成后切换"
        ExpandableTipContent(
            visible = tipExpanded,
            tipText = tip
        )

        // 时间轮换项（仅当存在带 period 的计划时显示）
        if (hasPeriodicPlan) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onPlanSelected(-1) }
            ) {
                RadioButton(
                    selected = selectedPlanIndex == -1,
                    onClick = { onPlanSelected(-1) },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "根据时间段自动切换 ($currentPlanName)",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // 各计划选项
        plans.forEachIndexed { index, plan ->
            val periodText = if (plan.period.isNotEmpty()) {
                plan.period.joinToString(", ") { range ->
                    if (range.size >= 2) "${range[0]}-${range[1]}" else ""
                }
            } else ""
            val label = buildString {
                append(plan.name ?: "Plan ${'A' + index}")
                if (periodText.isNotBlank()) append(" ($periodText)")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onPlanSelected(index) }
            ) {
                RadioButton(
                    selected = selectedPlanIndex == index,
                    onClick = { onPlanSelected(index) },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // 当前选中计划的描述
        if (selectedPlanIndex >= 0 && selectedPlanIndex < plans.size) {
            val desc = plans[selectedPlanIndex].description
            if (!desc.isNullOrBlank()) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }

        // 部分计划无时间段警告
        if (hasPeriodicPlan && hasNonPeriodicPlan) {
            Text(
                text = "自定义基建配置仅有部分计划存在时间段，可能会存在预期外的行为。如需设置换班时间段请保证所有计划均设置时间段，否则请全部留空。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * 无人机用途选择区域
 * 使用 RadioButton 单选按钮组，FlowRow 自动换行
 */
@Composable
private fun UsesOfDronesSection(
    config: InfrastConfig,
    onConfigChange: (InfrastConfig) -> Unit
) {


    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "无人机用途",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            UiUsageConstants.dronesUsages.forEach { (value, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .widthIn(min = 80.dp)
                        .clickable { onConfigChange(config.copy(usesOfDrones = value)) }
                ) {
                    RadioButton(
                        selected = config.usesOfDrones == value,
                        onClick = { onConfigChange(config.copy(usesOfDrones = value)) },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
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
 * 心情阈值设置区域
 */
@Composable
private fun DormThresholdSection(
    config: InfrastConfig,
    onConfigChange: (InfrastConfig) -> Unit
) {
    var tipExpanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "宿舍心情阈值",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                ExpandableTipIcon(
                    expanded = tipExpanded,
                    onExpandedChange = { tipExpanded = it }
                )
            }
            Text(
                text = "${config.dormThreshold}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        ExpandableTipContent(
            visible = tipExpanded,
            tipText = "若启用自定义换班，该字段仅针对autofill 和使用工员编组的房间有效"
        )

        Slider(
            value = config.dormThreshold.toFloat(),
            onValueChange = { onConfigChange(config.copy(dormThreshold = it.toInt())) },
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 设施列表区域
 */
@Composable
private fun FacilitiesSection(
    config: InfrastConfig,
    onConfigChange: (InfrastConfig) -> Unit
) {

    var tipExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "基建设施",
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
            tipText = "勾选需要换班的设施\n设施顺序代表换班优先级"
        )

        // 设施列表（支持拖拽排序 + 勾选）
        FacilityList(
            facilities = config.facilities,
            onFacilitiesChange = { onConfigChange(config.copy(facilities = it)) }
        )

        // 全选/清除按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    onConfigChange(
                        config.copy(
                            facilities = config.facilities.map { it.first to true }
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("全选")
            }

            OutlinedButton(
                onClick = {
                    onConfigChange(
                        config.copy(
                            facilities = config.facilities.map { it.first to false }
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("清除")
            }
        }
    }
}

/**
 * 设施列表展示（支持拖拽排序 + 勾选）
 *
 * @param facilities 设施列表（有序，含启用状态）
 * @param onFacilitiesChange 设施列表变化回调
 */
@Composable
private fun FacilityList(
    facilities: List<Pair<InfrastRoomType, Boolean>>,
    onFacilitiesChange: (List<Pair<InfrastRoomType, Boolean>>) -> Unit
) {

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFFF5F5F5),
        border = BorderStroke(1.dp, Color.LightGray)
    ) {
        ReorderableColumn(
            list = facilities,
            onSettle = { fromIndex, toIndex ->
                val newList = facilities.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
                onFacilitiesChange(newList)
            },
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) { _, entry, _ ->
            key(entry.first) {
                ReorderableItem {
                    val (facility, enabled) = entry
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .longPressDraggableHandle()
                            .clickable {
                                val newList = facilities.map {
                                    if (it.first == facility) it.first to !it.second else it
                                }
                                onFacilitiesChange(newList)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                val newList = facilities.map {
                                    if (it.first == facility) it.first to checked else it
                                }
                                onFacilitiesChange(newList)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = facility.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

/**
 * 宿舍信赖模式（仅Normal模式显示）
 */
@Composable
private fun DormTrustEnabledSection(
    config: InfrastConfig,
    onConfigChange: (InfrastConfig) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = config.dormTrustEnabled,
            onCheckedChange = { onConfigChange(config.copy(dormTrustEnabled = it)) },
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "宿舍空余位置蹭信赖",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * 不将已进驻干员放入宿舍（仅Normal模式显示）
 */
@Composable
private fun DormFilterNotStationedSection(
    config: InfrastConfig,
    onConfigChange: (InfrastConfig) -> Unit
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
                checked = config.dormFilterNotStationedEnabled,
                onCheckedChange = { onConfigChange(config.copy(dormFilterNotStationedEnabled = it)) },
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "不将已进驻的干员放入宿舍",
                style = MaterialTheme.typography.bodyMedium
            )
            ExpandableTipIcon(
                expanded = tipExpanded,
                onExpandedChange = { tipExpanded = it }
            )
        }
        ExpandableTipContent(
            visible = tipExpanded,
            tipText = "勾选则不会将艾丽妮等干员从训练室移除，但也会导致加工站干员不能进入宿舍。",
            modifier = Modifier.padding(start = 28.dp)
        )
    }
}

/**
 * 制造站搓玉自动补货
 */
@Composable
private fun OriginiumShardAutoReplenishmentSection(
    config: InfrastConfig,
    onConfigChange: (InfrastConfig) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = config.originiumShardAutoReplenishment,
            onCheckedChange = { onConfigChange(config.copy(originiumShardAutoReplenishment = it)) },
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "源石碎片自动补货",
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )
    }
}

/**
 * 会客室留言板领取信用
 */
@Composable
private fun ReceptionMessageBoardReceiveSection(
    config: InfrastConfig,
    onConfigChange: (InfrastConfig) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = config.receptionMessageBoard,
            onCheckedChange = { onConfigChange(config.copy(receptionMessageBoard = it)) },
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "会客室信息板收取信用",
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )
    }
}

/**
 * 会客室线索交流
 */
@Composable
private fun ReceptionClueExchangeSection(
    config: InfrastConfig,
    onConfigChange: (InfrastConfig) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = config.receptionClueExchange,
            onCheckedChange = { onConfigChange(config.copy(receptionClueExchange = it)) },
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "进行线索交流",
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )
    }
}

/**
 * 会客室赠送线索
 */
@Composable
private fun ReceptionSendClueSection(
    config: InfrastConfig,
    onConfigChange: (InfrastConfig) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = config.receptionSendClue,
            onCheckedChange = { onConfigChange(config.copy(receptionSendClue = it)) },
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "赠送线索",
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )
    }
}

/**
 * 继续专精
 */
@Composable
private fun ContinueTrainingSection(
    config: InfrastConfig,
    onConfigChange: (InfrastConfig) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = config.continueTraining,
            onCheckedChange = { onConfigChange(config.copy(continueTraining = it)) },
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "训练完成后继续尝试专精当前技能",
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )
    }
}
