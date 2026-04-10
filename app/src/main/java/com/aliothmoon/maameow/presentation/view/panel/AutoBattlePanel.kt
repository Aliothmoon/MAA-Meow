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
import androidx.compose.ui.res.stringResource
import com.aliothmoon.maameow.R

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
    val tabSpecs = remember(context) {
        listOf(
            CopilotTabUiSpec(
                index = 0,
                title = context.getString(R.string.copilot_tab_main_title),
                subtitle = "SideStory",
                fullLabel = context.getString(R.string.copilot_tab_main),
                helperText = context.getString(R.string.copilot_tab_main_helper),
                capabilities = listOf(
                    context.getString(R.string.copilot_cap_single),
                    context.getString(R.string.copilot_cap_set),
                    context.getString(R.string.copilot_cap_battle_list),
                    context.getString(R.string.copilot_cap_formation)
                ),
                supportsBattleList = true,
                supportsSetImport = true,
                supportsRegularOptions = true,
            ),
            CopilotTabUiSpec(
                index = 1,
                title = context.getString(R.string.copilot_tab_sss),
                fullLabel = context.getString(R.string.copilot_tab_sss),
                helperText = context.getString(R.string.copilot_tab_sss_helper),
                capabilities = listOf(
                    context.getString(R.string.copilot_cap_single),
                    context.getString(R.string.copilot_cap_loop),
                    context.getString(R.string.copilot_cap_builtin)
                ),
                supportsBattleList = false,
                supportsSetImport = false,
                supportsRegularOptions = false,
            ),
            CopilotTabUiSpec(
                index = 2,
                title = context.getString(R.string.copilot_tab_paradox),
                fullLabel = context.getString(R.string.copilot_tab_paradox),
                helperText = context.getString(R.string.copilot_tab_paradox_helper),
                capabilities = listOf(
                    context.getString(R.string.copilot_cap_single),
                    context.getString(R.string.copilot_cap_battle_list)
                ),
                supportsBattleList = true,
                supportsSetImport = true,
                supportsRegularOptions = false,
            ),
            CopilotTabUiSpec(
                index = 3,
                title = context.getString(R.string.copilot_tab_other),
                fullLabel = context.getString(R.string.copilot_tab_other),
                helperText = context.getString(R.string.copilot_tab_other_helper),
                capabilities = listOf(
                    context.getString(R.string.copilot_cap_single),
                    context.getString(R.string.copilot_cap_formation),
                    context.getString(R.string.copilot_cap_support_unit)
                ),
                supportsBattleList = false,
                supportsSetImport = false,
                supportsRegularOptions = true,
            )
        )
    }
    val current = tabSpecs.firstOrNull { it.index == state.tabIndex } ?: tabSpecs.first()
    val regularCopilotTab = current.supportsRegularOptions
    val loopCountSupportedTab = current.index == 1 || current.index == 3
    val battleListSupportedTab = current.supportsBattleList
    val setImportSupported = current.supportsSetImport


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
                                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                                        alpha = 0.8f
                                                    )
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
                    label = stringResource(R.string.copilot_secret_code_label),
                    placeholder = stringResource(R.string.copilot_secret_code_placeholder),
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
                            text = state.statusMessage.ifBlank { stringResource(R.string.copilot_waiting_op) },
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
                        Text(if (state.isLoading) stringResource(R.string.copilot_reading) else stringResource(R.string.copilot_read_single))
                    }
                    Button(
                        onClick = viewModel::onParseSetInput,
                        enabled = !state.isLoading && !isStarting && setImportSupported,
                        shape = compactButtonShape,
                        contentPadding = compactButtonPadding
                    ) {
                        Text(stringResource(R.string.copilot_read_set))
                    }
                    OutlinedButton(
                        onClick = { Misc.openUriSafely(context, "https://zoot.plus") },
                        shape = compactButtonShape,
                        contentPadding = compactButtonPadding
                    ) {
                        Text(stringResource(R.string.copilot_job_station))
                    }
                }
            }

            // 作业详情 + 视频链接
            if (state.currentCopilot != null) {
                val doc = state.currentCopilot!!.doc
                val hasDetail =
                    doc.title.isNotBlank() || doc.details.isNotBlank() || state.operatorSummary?.isEmpty == false
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
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                                                alpha = 0.2f
                                            )
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
                                                    text = stringResource(R.string.copilot_operators_section),
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
                                                    text = stringResource(R.string.copilot_operator_group, groupName),
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
                                            text = stringResource(R.string.copilot_total_count_fmt, summary.totalCount),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                                                alpha = 0.6f
                                            )
                                        )
                                    }
                                }
                                if (hasVideo) {
                                    Text(
                                        text = stringResource(R.string.copilot_video_link),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.clickable {
                                            Misc.openUriSafely(
                                                context,
                                                state.videoUrl
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (regularCopilotTab) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CheckBoxWithExpandableTip(
                            checked = state.config.formation,
                            onCheckedChange = {
                                viewModel.onConfigChanged(state.config.copy(formation = it))
                            },
                            label = stringResource(R.string.auto_formation),
                            tipText = stringResource(R.string.auto_formation_tip)
                        )
                        if (state.config.formation) {
                            CheckBoxWithLabel(
                                checked = state.config.useFormation,
                                onCheckedChange = { enabled ->
                                    viewModel.onConfigChanged(
                                        state.config.copy(
                                            useFormation = enabled,
                                            formationIndex = state.config.formationIndex.coerceIn(
                                                1,
                                                4
                                            )
                                        )
                                    )
                                },
                                label = stringResource(R.string.use_formation)
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
                                label = stringResource(R.string.ignore_requirements),
                                tipText = stringResource(R.string.ignore_requirements_tip)
                            )

                            CheckBoxWithExpandableTip(
                                checked = state.config.useSupportUnit,
                                onCheckedChange = {
                                    viewModel.onConfigChanged(state.config.copy(useSupportUnit = it))
                                },
                                label = stringResource(R.string.support_unit_label),
                                tipText = stringResource(R.string.support_unit_tip)
                            )

                            if (state.config.useSupportUnit) {
                                val supportOptions = listOf(
                                    1 to stringResource(R.string.support_fill),
                                    3 to stringResource(R.string.support_random)
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    supportOptions.forEach { (value, label) ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable {
                                                viewModel.onConfigChanged(
                                                    state.config.copy(
                                                        supportUnitUsage = value
                                                    )
                                                )
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
                                onCheckedChange = {
                                    viewModel.onConfigChanged(
                                        state.config.copy(
                                            addTrust = it
                                        )
                                    )
                                },
                                label = stringResource(R.string.add_trust)
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (battleListSupportedTab) {
                        CheckBoxWithExpandableTip(
                            checked = state.useCopilotList,
                            onCheckedChange = viewModel::onToggleListMode,
                            label = stringResource(R.string.battle_list_label),
                            tipText = stringResource(R.string.battle_list_tip)
                        )
                    }

                    if (state.useCopilotList && state.tabIndex == 0) {
                        CheckBoxWithLabel(
                            checked = state.config.useSanityPotion,
                            onCheckedChange = {
                                viewModel.onConfigChanged(state.config.copy(useSanityPotion = it))
                            },
                            label = stringResource(R.string.use_sanity_potion)
                        )
                    }

                    if (!state.useCopilotList && loopCountSupportedTab) {
                        CheckBoxWithLabel(
                            checked = state.config.loop,
                            onCheckedChange = {
                                viewModel.onConfigChanged(state.config.copy(loop = it))
                            },
                            label = stringResource(R.string.loop_count)
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
                                label = stringResource(R.string.loop_count),
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
                            stringResource(R.string.battle_list_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        var sequenceTipExpanded by remember { mutableStateOf(false) }
                        ExpandableTipIcon(
                            expanded = sequenceTipExpanded,
                            onExpandedChange = { sequenceTipExpanded = it })
                        ExpandableTipContent(
                            visible = sequenceTipExpanded,
                            tipText = stringResource(R.string.drag_reorder_tip)
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
                                stringResource(R.string.no_entries),
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
                                                    .padding(
                                                        start = 8.dp,
                                                        end = 4.dp,
                                                        top = 2.dp,
                                                        bottom = 2.dp
                                                    ),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                CheckBoxWithLabel(
                                                    checked = item.isChecked,
                                                    onCheckedChange = {
                                                        viewModel.onToggleListItem(
                                                            index
                                                        )
                                                    },
                                                    label = item.name + if (item.isRaid) stringResource(R.string.copilot_added_raid_suffix) else "",
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedButton(
                                                    onClick = { viewModel.onSelectListItem(index) },
                                                    shape = compactButtonShape,
                                                    contentPadding = compactButtonPadding
                                                ) { Text(stringResource(R.string.load_job)) }
                                                IconButton(
                                                    onClick = { viewModel.onRemoveFromList(index) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = stringResource(R.string.delete),
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
                        OutlinedButton(
                            onClick = viewModel::onCleanUnchecked,
                            shape = compactButtonShape,
                            contentPadding = compactButtonPadding
                        ) { Text(stringResource(R.string.clear_unchecked)) }
                        OutlinedButton(
                            onClick = viewModel::onClearList,
                            shape = compactButtonShape,
                            contentPadding = compactButtonPadding
                        ) { Text(stringResource(R.string.clear_list)) }
                    }
                }
            }




            item {
                var expanded by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.tips_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ExpandableTipIcon(expanded = expanded, onExpandedChange = { expanded = it })
                    }
                    ExpandableTipContent(
                        visible = expanded,
                        tipText = stringResource(R.string.copilot_tips_content)
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
