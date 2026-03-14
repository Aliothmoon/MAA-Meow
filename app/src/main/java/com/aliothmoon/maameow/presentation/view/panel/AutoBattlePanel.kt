package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.utils.Misc
import com.aliothmoon.maameow.domain.service.OperatorDisplayItem
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.presentation.components.CheckBoxWithExpandableTip
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon
import com.aliothmoon.maameow.presentation.viewmodel.CopilotViewModel
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private data class CopilotTabUiSpec(
    val index: Int,
    val title: String,
    val subtitle: String? = null,
    val fullLabel: String,
    val helperText: String,
    val capabilities: List<String>,
    val supportsBattleList: Boolean,
    val supportsSetImport: Boolean,
    val supportsRegularOptions: Boolean,
)

@Composable
fun AutoBattlePanel(
    modifier: Modifier = Modifier,
    viewModel: CopilotViewModel = koinInject()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val maaState by viewModel.maaState.collectAsStateWithLifecycle()
    val isStarting = maaState == MaaExecutionState.STARTING
    val context = LocalContext.current
    val compactButtonShape = RoundedCornerShape(8.dp)
    val compactButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    val tabTitleTextStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = 13.sp,
        lineHeight = 16.sp
    )
    val tabSubtitleTextStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.5.sp,
        lineHeight = 12.sp
    )
    val tabSpecs = remember {
        listOf(
            CopilotTabUiSpec(
                index = 0,
                title = "主线 / 故事集",
                subtitle = "SideStory",
                fullLabel = "主线/故事集/SideStory",
                helperText = "支持单作业、作业集、战斗列表与常规 Copilot 配置，适合主线、故事集和 SideStory。",
                capabilities = listOf("单作业", "作业集", "战斗列表", "自动编队"),
                supportsBattleList = true,
                supportsSetImport = true,
                supportsRegularOptions = true,
            ),
            CopilotTabUiSpec(
                index = 1,
                title = "保全派驻",
                fullLabel = "保全派驻",
                helperText = "使用 resource/copilot 内置作业，建议先手动编队后启动。支持循环次数，不支持作业集和战斗列表。",
                capabilities = listOf("单作业", "循环次数", "内置作业"),
                supportsBattleList = false,
                supportsSetImport = false,
                supportsRegularOptions = false,
            ),
            CopilotTabUiSpec(
                index = 2,
                title = "悖论模拟",
                fullLabel = "悖论模拟",
                helperText = "适合悖论模拟单作业或战斗列表，请从技能选择或干员列表界面启动。不支持作业集导入和常规编队配置。",
                capabilities = listOf("单作业", "战斗列表"),
                supportsBattleList = true,
                supportsSetImport = true,
                supportsRegularOptions = false,
            ),
            CopilotTabUiSpec(
                index = 3,
                title = "其他活动",
                fullLabel = "其他活动",
                helperText = "用于主线/故事集/SideStory 以外的活动单作业。支持常规 Copilot 配置，不支持作业集和战斗列表。",
                capabilities = listOf("单作业", "自动编队", "借助战"),
                supportsBattleList = false,
                supportsSetImport = false,
                supportsRegularOptions = true,
            )
        )
    }
    val selectedTabSpec = tabSpecs.firstOrNull { it.index == state.tabIndex } ?: tabSpecs.first()
    val regularCopilotTab = selectedTabSpec.supportsRegularOptions
    val loopCountSupportedTab = selectedTabSpec.index == 1
    val battleListSupportedTab = selectedTabSpec.supportsBattleList
    val setImportSupported = selectedTabSpec.supportsSetImport


    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 4.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tabSpecs.chunked(2).forEach { rowSpecs ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowSpecs.forEach { spec ->
                            val selected = state.tabIndex == spec.index
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .clickable { viewModel.onTabChanged(spec.index) }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = spec.title,
                                        style = tabTitleTextStyle,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    spec.subtitle?.let { subtitle ->
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = subtitle,
                                            style = tabSubtitleTextStyle,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                        if (rowSpecs.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                HorizontalDivider()
            }
        }

        item {
            ITextField(
                value = state.inputText,
                onValueChange = viewModel::onInputChanged,
                label = "作业站神秘代码",
                placeholder = "输入 maa://1234、1234",
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(
                        text = state.statusMessage.ifBlank { "等待操作..." },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::onParseSingleInput,
                    enabled = !state.isLoading && !isStarting,
                    shape = compactButtonShape,
                    contentPadding = compactButtonPadding
                ) {
                    Text(if (state.isLoading) "读取中..." else "读取作业")
                }
                Button(
                    onClick = viewModel::onParseSetInput,
                    enabled = !state.isLoading && !isStarting && setImportSupported,
                    shape = compactButtonShape,
                    contentPadding = compactButtonPadding
                ) {
                    Text("读取作业集")
                }
                OutlinedButton(
                    onClick = { Misc.openUriSafely(context, "https://zoot.plus") },
                    shape = compactButtonShape,
                    contentPadding = compactButtonPadding
                ) {
                    Text("作业站")
                }
            }
        }

        // 作业详情 + 视频链接
        if (state.currentCopilot != null) {
            val doc = state.currentCopilot!!.doc
            val hasDetail = doc.title.isNotBlank() || doc.details.isNotBlank() || state.operatorSummary?.isEmpty == false
            val hasVideo = state.videoUrl.isNotBlank()
            if (hasDetail || hasVideo) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (doc.title.isNotBlank()) {
                                        Text(
                                            text = doc.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    if (doc.details.isNotBlank()) {
                                        Text(
                                            text = doc.details,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                            val summary = state.operatorSummary
                            if (summary != null && !summary.isEmpty) {
                                if (doc.title.isNotBlank() || doc.details.isNotBlank()) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                                    )
                                }
                                val textMeasurer = rememberTextMeasurer()
                                val labelStyle = MaterialTheme.typography.labelSmall
                                val density = LocalDensity.current
                                val nameColumnWidth = remember(summary) {
                                    val allNames = summary.operators.map { it.name } +
                                            summary.groups.flatMap { (_, opers) -> opers.map { it.name } }
                                    val maxTextWidth = allNames.maxOfOrNull { name ->
                                        textMeasurer.measure(name, labelStyle).size.width
                                    } ?: 0
                                    maxTextWidth + with(density) { 8.dp.roundToPx() }
                                }
                                val nameWidth = remember(nameColumnWidth) {
                                    with(density) { nameColumnWidth.toDp() }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // 独立干员
                                    if (summary.operators.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = "干员",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            summary.operators.forEach { oper ->
                                                OperatorRow(oper, nameWidth = nameWidth)
                                            }
                                        }
                                    }
                                    // 备选组
                                    summary.groups.forEach { (groupName, opers) ->
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = "干员组: $groupName",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            opers.forEach { oper ->
                                                OperatorRow(oper, nameWidth = nameWidth)
                                            }
                                        }
                                    }
                                    // 统计
                                    Text(
                                        text = "共 ${summary.totalCount} 名干员/组",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            if (hasVideo) {
                                Text(
                                    text = "视频",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clickable { Misc.openUriSafely(context, state.videoUrl) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (regularCopilotTab) {
                    CheckBoxWithExpandableTip(
                        checked = state.config.formation,
                        onCheckedChange = {
                            viewModel.onConfigChanged(state.config.copy(formation = it))
                        },
                        label = "自动编队",
                        tipText = "自动编队可能无法识别带有 ｢特别关注｣ 标记的干员"
                    )
                }
                if (regularCopilotTab && state.config.formation) {
                    CheckBoxWithLabel(
                        checked = state.config.useFormation,
                        onCheckedChange = { enabled ->
                            viewModel.onConfigChanged(
                                state.config.copy(
                                    useFormation = enabled,
                                    formationIndex = state.config.formationIndex.coerceIn(1, 4)
                                )
                            )
                        },
                        label = "使用编队"
                    )

                    if (state.config.useFormation) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 3, 4).forEach { index ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        viewModel.onConfigChanged(
                                            state.config.copy(
                                                formationIndex = index
                                            )
                                        )
                                    }
                                ) {
                                    RadioButton(
                                        selected = state.config.formationIndex == index,
                                        onClick = {
                                            viewModel.onConfigChanged(
                                                state.config.copy(
                                                    formationIndex = index
                                                )
                                            )
                                        }
                                    )
                                    Text(index.toString())
                                }
                            }
                        }
                    }

                    CheckBoxWithExpandableTip(
                        checked = state.config.ignoreRequirements,
                        onCheckedChange = {
                            viewModel.onConfigChanged(state.config.copy(ignoreRequirements = it))
                        },
                        label = "忽略干员属性要求",
                        tipText = "某些作业需要特定模组等作为前置条件\n勾选此项将跳过这些检查，但可能导致作业无法正常运行"
                    )

                    CheckBoxWithExpandableTip(
                        checked = state.config.useSupportUnit,
                        onCheckedChange = {
                            viewModel.onConfigChanged(state.config.copy(useSupportUnit = it))
                        },
                        label = "借助战",
                        tipText = "缺一个还能用用，缺两个以上还是换份作业吧"
                    )

                    if (state.config.useSupportUnit) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1 to "补漏", 3 to "随机").forEach { (value, label) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        viewModel.onConfigChanged(state.config.copy(supportUnitUsage = value))
                                    }
                                ) {
                                    RadioButton(
                                        selected = state.config.supportUnitUsage == value,
                                        onClick = {
                                            viewModel.onConfigChanged(
                                                state.config.copy(
                                                    supportUnitUsage = value
                                                )
                                            )
                                        }
                                    )
                                    Text(label)
                                }
                            }
                        }
                    }

                    CheckBoxWithLabel(
                        checked = state.config.addTrust,
                        onCheckedChange = { viewModel.onConfigChanged(state.config.copy(addTrust = it)) },
                        label = "补充低信赖干员"
                    )
                }
            }
        }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (battleListSupportedTab) {
                        CheckBoxWithExpandableTip(
                            checked = state.useCopilotList,
                            onCheckedChange = viewModel::onToggleListMode,
                            label = "战斗列表",
                            tipText = """
仅支持以下模式:
  1. 主线: 同一章节内导航
  2. SideStory: 当前页面内导航（普通/EX/S 不能互跳）
  3. 故事集: 当前页面内导航
  4. 悖论模拟: 从干员列表启动
请在对应界面启动，不支持跨章节导航

当 ｢战斗列表｣ 启用后, 选择单个作业时会自动添加到 ｢战斗列表｣
                            """.trimIndent()
                        )
                    }

                    if (state.useCopilotList && state.tabIndex == 0) {
                        CheckBoxWithLabel(
                            checked = state.config.useSanityPotion,
                            onCheckedChange = {
                                viewModel.onConfigChanged(state.config.copy(useSanityPotion = it))
                            },
                            label = "使用理智药"
                        )
                    }

                    if (!state.useCopilotList && loopCountSupportedTab) {
                        CheckBoxWithLabel(
                            checked = state.config.loop,
                            onCheckedChange = {
                                viewModel.onConfigChanged(state.config.copy(loop = it))
                            },
                            label = "循环次数"
                        )
                        if (state.config.loop) {
                            ITextField(
                                value = state.config.loopTimes.toString(),
                                onValueChange = { text ->
                                    text.toIntOrNull()?.let {
                                        viewModel.onConfigChanged(
                                            state.config.copy(
                                                loopTimes = it.coerceAtLeast(
                                                    1
                                                )
                                            )
                                        )
                                    }
                                },
                                label = "循环次数",
                                placeholder = "1"
                            )
                        }
                    }
                }
            }
        

        item {
            if (state.useCopilotList && battleListSupportedTab) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "战斗列表",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    var sequenceTipExpanded by remember { mutableStateOf(false) }
                    ExpandableTipIcon(
                        expanded = sequenceTipExpanded,
                        onExpandedChange = { sequenceTipExpanded = it })
                    ExpandableTipContent(
                        visible = sequenceTipExpanded,
                        tipText = "标签顺序可拖动"
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    if (state.taskList.isEmpty()) {
                        Text(
                            "暂无条目",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        val lazyListState = rememberLazyListState()
                        val reorderableState = rememberReorderableLazyListState(
                            lazyListState = lazyListState,
                            onMove = { from, to ->
                                viewModel.onReorderList(from.index, to.index)
                            }
                        )
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(
                                state.taskList,
                                key = { index, item -> "${item.filePath}-${item.name}-$index" }
                            ) { index, item ->
                                ReorderableItem(
                                    reorderableState,
                                    key = "${item.filePath}-${item.name}-$index"
                                ) { isDragging ->
                                    Surface(
                                        tonalElevation = if (isDragging) 4.dp else 0.dp,
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier
                                            .longPressDraggableHandle()
                                            .fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            CheckBoxWithLabel(
                                                checked = item.isChecked,
                                                onCheckedChange = { viewModel.onToggleListItem(index) },
                                                label = item.name + if (item.isRaid) " (突袭)" else "",
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedButton(
                                                onClick = { viewModel.onSelectListItem(index) },
                                                shape = compactButtonShape,
                                                contentPadding = compactButtonPadding
                                            ) { Text("加载") }
                                            IconButton(
                                                onClick = { viewModel.onRemoveFromList(index) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "删除",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // TODO: 恢复手动输入关卡名 + 添加普通/添加突袭功能
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = viewModel::onCleanUnchecked, shape = compactButtonShape, contentPadding = compactButtonPadding) { Text("清除未勾选") }
                    OutlinedButton(onClick = viewModel::onClearList, shape = compactButtonShape, contentPadding = compactButtonPadding) { Text("清空列表") }
                }
            }
        }




            item {
                var expanded by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("小提示", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ExpandableTipIcon(expanded = expanded, onExpandedChange = { expanded = it })
                    }
                    ExpandableTipContent(
                        visible = expanded,
                        tipText = """
1. 使用前请确认作业与所选的关卡类型一致。
2. 主线、故事集、SideStory: 请在关卡界面的右下角存在 ｢开始行动｣ 按钮界面启动。
3. 保全派驻: resource/copilot 文件夹内置多份作业。请先手动编队，在右下角存在 ｢开始部署｣ 按钮界面启动，可配合 ｢循环次数｣。
4. 悖论模拟: 选好技能后，在技能选择界面存在 ｢开始模拟｣ 按钮界面启动，1/2 星干员（无技能）在右下角存在 ｢开始模拟｣ 按钮界面开始。若使用 ｢战斗列表｣，请从干员列表 ｢等级/稀有度｣ 筛选下启动。
5. 其他活动: 仅支持单作业与常规 Copilot 配置，请在目标活动关卡界面直接启动，不支持作业集和战斗列表。
6. 使用好友助战时，请关闭 ｢自动编队｣ 和 ｢战斗列表｣，手动选择干员后，在编队界面右下角存在 ｢开始行动｣ 按钮界面启动。
7. 干员若被标记为 ｢特别关注｣，可能影响 ｢自动编队｣ 的识别与选择。建议使用 ｢自动编队｣ 时移除关注，或在报错后关闭 ｢自动编队｣，根据提示手动补充缺失的干员。
8. Copilot 作业站的神秘代码可在输入框输入后读取：
● 点击「读取作业」= 读取单作业。
● 点击「读取作业集」= 导入作业集（仅主线/故事集/SideStory、悖论模拟）。
9. 战斗列表:
● 选择作业后，检查下方关卡名是否正确 (例: CV-EX-1)。
● 可通过「添加普通/添加突袭」按钮添加任务（突袭仅主线）。
● 可通过「清空列表/清除未勾选」按钮快速整理任务。
● 请在能看到目标关卡名的界面启动，不支持跨章节导航。
● 遇到理智不足、战斗失败、未能三星结算时将自动中止。
                        """.trimIndent()
                    )
                }
            }
        }

    }
}

@Composable
private fun OperatorRow(
    item: OperatorDisplayItem,
    nameWidth: Dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .width(nameWidth)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                textAlign = TextAlign.Start
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            item.tags.forEach { tag ->
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
