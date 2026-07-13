package com.aliothmoon.maameow.presentation.view.panel

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.resource.CopilotResourceProvider
import com.aliothmoon.maameow.domain.service.OperatorDisplayItem
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.presentation.LocalFloatingWindowContext
import com.aliothmoon.maameow.presentation.components.CheckBoxWithExpandableTip
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon
import com.aliothmoon.maameow.presentation.viewmodel.CopilotViewModel
import com.aliothmoon.maameow.theme.DenseTabTypography
import com.aliothmoon.maameow.utils.Misc
import com.aliothmoon.maameow.utils.i18n.asString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private data class CopilotTabUiSpec(
    val index: Int,
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int? = null,
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
    val controlsEnabled = !state.isLoading && !isStarting
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isInFloatingWindow = LocalFloatingWindowContext.current
    val statusMessage = state.statusMessage.asString()
    val compactButtonShape = RoundedCornerShape(8.dp)
    val compactButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    val importFloatHint = stringResource(R.string.copilot_import_float_hint)

    // SAF 文件选择器（浮窗环境下不可用）
    val filePicker = if (!isInFloatingWindow) {
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            scope.launch {
                val files = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        val name = Misc.queryFileName(context, uri)
                            ?: uri.lastPathSegment
                            ?: "copilot_${System.currentTimeMillis()}.json"
                        val json = context.contentResolver.openInputStream(uri)?.use {
                            it.bufferedReader().readText()
                        } ?: return@mapNotNull null
                        name to json
                    }
                }
                if (files.isNotEmpty()) {
                    viewModel.onImportLocalFiles(files)
                }
            }
        }
    } else null
    val tabTitleTextStyle = MaterialTheme.typography.bodySmall.copy(
        lineHeight = 16.sp
    )
    val tabSubtitleTextStyle = DenseTabTypography.Subtitle
    val tabSpecs = listOf(
        CopilotTabUiSpec(
            index = 0,
            titleRes = R.string.panel_autobattle_tab_mainline,
            subtitleRes = R.string.panel_autobattle_tab_mainline_subtitle,
            supportsBattleList = true,
            supportsSetImport = true,
            supportsRegularOptions = true,
        ),
        CopilotTabUiSpec(
            index = 1,
            titleRes = R.string.panel_autobattle_tab_security,
            supportsBattleList = false,
            supportsSetImport = false,
            supportsRegularOptions = false,
        ),
        CopilotTabUiSpec(
            index = 2,
            titleRes = R.string.panel_autobattle_tab_paradox,
            supportsBattleList = true,
            supportsSetImport = true,
            supportsRegularOptions = false,
        ),
        CopilotTabUiSpec(
            index = 3,
            titleRes = R.string.panel_autobattle_tab_other,
            supportsBattleList = false,
            supportsSetImport = false,
            supportsRegularOptions = true,
        )
    )
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
                                            text = stringResource(spec.titleRes),
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
                                        spec.subtitleRes?.let { subtitleRes ->
                                            Spacer(modifier = Modifier.height(1.dp))
                                            Text(
                                                text = stringResource(subtitleRes),
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
                    label = stringResource(R.string.panel_autobattle_station_code_label),
                    placeholder = stringResource(R.string.panel_autobattle_station_code_placeholder),
                )
            }

            item {
                BuiltinCopilotPicker(
                    expanded = state.builtinPickerExpanded,
                    loaded = state.builtinLoaded,
                    tree = state.builtinTree,
                    expandedFolders = state.builtinExpandedFolders,
                    enabled = controlsEnabled,
                    onToggle = viewModel::onToggleBuiltinPicker,
                    onToggleFolder = viewModel::onToggleBuiltinFolder,
                    onSelectFile = viewModel::onSelectBuiltinFile,
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
                            text = statusMessage.ifBlank {
                                stringResource(R.string.panel_autobattle_waiting)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 读取类：实心主色
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CopilotActionButton(
                            text = if (state.isLoading) {
                                stringResource(R.string.panel_autobattle_loading)
                            } else {
                                stringResource(R.string.panel_autobattle_read_single)
                            },
                            icon = Icons.Default.Search,
                            filled = true,
                            enabled = controlsEnabled,
                            onClick = viewModel::onParseSingleInput,
                        )
                        CopilotActionButton(
                            text = stringResource(R.string.panel_autobattle_read_set),
                            icon = Icons.Default.GridView,
                            filled = true,
                            enabled = controlsEnabled && setImportSupported,
                            onClick = viewModel::onParseSetInput,
                        )
                    }
                    // 导入 / 外链：描边次级
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CopilotActionButton(
                            text = stringResource(R.string.copilot_import_file),
                            icon = Icons.Default.UploadFile,
                            filled = false,
                            enabled = controlsEnabled,
                            onClick = {
                                if (filePicker != null) {
                                    filePicker.launch(arrayOf("application/json", "application/octet-stream"))
                                } else {
                                    Toast.makeText(context, importFloatHint, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                        CopilotActionButton(
                            text = stringResource(R.string.panel_autobattle_station),
                            icon = Icons.Default.Public,
                            filled = false,
                            enabled = true,
                            onClick = { Misc.openUriSafely(context, "https://zoot.plus") },
                        )
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
                                                    text = stringResource(R.string.panel_autobattle_operator_header),
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
                                                    text = stringResource(
                                                        R.string.panel_autobattle_group_header,
                                                        groupName
                                                    ),
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
                                            text = stringResource(
                                                R.string.panel_autobattle_summary_count,
                                                summary.totalCount
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                                                alpha = 0.6f
                                            )
                                        )
                                    }
                                }
                                if (hasVideo) {
                                    Text(
                                        text = stringResource(R.string.common_video),
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
                                label = stringResource(R.string.panel_autobattle_auto_formation),
                                tipText = stringResource(R.string.panel_autobattle_auto_formation_tip)
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
                                label = stringResource(R.string.panel_autobattle_use_formation)
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
                                label = stringResource(R.string.panel_autobattle_ignore_requirements),
                                tipText = stringResource(R.string.panel_autobattle_ignore_requirements_tip)
                            )

                            CheckBoxWithExpandableTip(
                                checked = state.config.useSupportUnit,
                                onCheckedChange = {
                                    viewModel.onConfigChanged(state.config.copy(useSupportUnit = it))
                                },
                                label = stringResource(R.string.panel_autobattle_support_unit),
                                tipText = stringResource(R.string.panel_autobattle_support_unit_tip)
                            )

                            if (state.config.useSupportUnit) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        1 to stringResource(R.string.panel_autobattle_support_fill_gaps),
                                        3 to stringResource(R.string.panel_autobattle_support_random)
                                    ).forEach { (value, label) ->
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
                                label = stringResource(R.string.panel_autobattle_add_trust)
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
                            label = stringResource(R.string.panel_autobattle_battle_list),
                            tipText = stringResource(R.string.panel_autobattle_battle_list_tip)
                        )
                    }

                    if (state.useCopilotList && state.tabIndex == 0) {
                        CheckBoxWithLabel(
                            checked = state.config.useSanityPotion,
                            onCheckedChange = {
                                viewModel.onConfigChanged(state.config.copy(useSanityPotion = it))
                            },
                            label = stringResource(R.string.panel_autobattle_use_sanity_potion)
                        )
                    }

                    if (!state.useCopilotList && loopCountSupportedTab) {
                        CheckBoxWithLabel(
                            checked = state.config.loop,
                            onCheckedChange = {
                                viewModel.onConfigChanged(state.config.copy(loop = it))
                            },
                            label = stringResource(R.string.panel_autobattle_loop)
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
                                label = stringResource(R.string.panel_autobattle_loop),
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
                            stringResource(R.string.panel_autobattle_battle_list),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        var sequenceTipExpanded by remember { mutableStateOf(false) }
                        ExpandableTipIcon(
                            expanded = sequenceTipExpanded,
                            onExpandedChange = { sequenceTipExpanded = it })
                        ExpandableTipContent(
                            visible = sequenceTipExpanded,
                            tipText = stringResource(R.string.panel_autobattle_sequence_tip)
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
                                stringResource(R.string.panel_autobattle_empty_entries),
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
                                                    label = item.name + if (item.isRaid) {
                                                        stringResource(R.string.panel_autobattle_raid_suffix)
                                                    } else {
                                                        ""
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedButton(
                                                    onClick = { viewModel.onSelectListItem(index) },
                                                    shape = compactButtonShape,
                                                    contentPadding = compactButtonPadding
                                                ) { Text(stringResource(R.string.common_load)) }
                                                IconButton(
                                                    onClick = { viewModel.onRemoveFromList(index) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = stringResource(R.string.common_delete),
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
                        ) { Text(stringResource(R.string.panel_autobattle_clear_unchecked)) }
                        OutlinedButton(
                            onClick = viewModel::onClearList,
                            shape = compactButtonShape,
                            contentPadding = compactButtonPadding
                        ) { Text(stringResource(R.string.panel_autobattle_clear_list)) }
                    }
                }
            }




            item {
                var expanded by remember { mutableStateOf(true) }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.panel_autobattle_tips_title),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ExpandableTipIcon(expanded = expanded, onExpandedChange = { expanded = it })
                    }
                    ExpandableTipContent(
                        visible = expanded,
                        tipText = stringResource(R.string.panel_autobattle_tips_body)
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

/**
 * 自动战斗操作按钮：等宽网格单元（RowScope.weight(1f)），图标 + 单行文字。
 * @param filled true=实心主色（读取类主操作）；false=描边次级（导入/外链）
 */
@Composable
private fun RowScope.CopilotActionButton(
    text: String,
    icon: ImageVector,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val padding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    val content: @Composable RowScope.() -> Unit = {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (filled) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            contentPadding = padding,
            modifier = Modifier.weight(1f),
            content = content,
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            contentPadding = padding,
            modifier = Modifier.weight(1f),
            content = content,
        )
    }
}

private data class BuiltinVisibleEntry(
    val node: CopilotResourceProvider.Node,
    val depth: Int,
)

@Composable
private fun BuiltinCopilotPicker(
    expanded: Boolean,
    loaded: Boolean,
    tree: List<CopilotResourceProvider.Node>,
    expandedFolders: Set<String>,
    enabled: Boolean,
    onToggle: () -> Unit,
    onToggleFolder: (String) -> Unit,
    onSelectFile: (CopilotResourceProvider.Node) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedButton(
            onClick = onToggle,
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.copilot_builtin_picker),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val visibleNodes = remember(tree, expandedFolders) {
                flattenVisibleNodes(tree, expandedFolders)
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .heightIn(max = 320.dp)
                    .animateContentSize()
            ) {
                when {
                    !loaded -> CircularProgressIndicator(
                        modifier = Modifier
                            .padding(12.dp)
                            .size(16.dp),
                        strokeWidth = 2.dp
                    )

                    visibleNodes.isEmpty() -> Text(
                        text = stringResource(R.string.copilot_builtin_picker_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(visibleNodes, key = { it.node.relativePath }) { entry ->
                            BuiltinNodeRow(
                                entry = entry,
                                expanded = entry.node.relativePath in expandedFolders,
                                enabled = enabled,
                                onClick = {
                                    if (entry.node.isFolder) {
                                        onToggleFolder(entry.node.relativePath)
                                    } else {
                                        onSelectFile(entry.node)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun flattenVisibleNodes(
    tree: List<CopilotResourceProvider.Node>,
    expandedFolders: Set<String>,
    depth: Int = 0,
    out: MutableList<BuiltinVisibleEntry> = mutableListOf(),
): List<BuiltinVisibleEntry> {
    for (node in tree) {
        out.add(BuiltinVisibleEntry(node, depth))
        if (node.isFolder && node.relativePath in expandedFolders) {
            flattenVisibleNodes(node.children, expandedFolders, depth + 1, out)
        }
    }
    return out
}

@Composable
private fun BuiltinNodeRow(
    entry: BuiltinVisibleEntry,
    expanded: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val node = entry.node
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                start = (8 + entry.depth * 16).dp,
                end = 8.dp,
                top = 6.dp,
                bottom = 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (node.isFolder) {
            Icon(
                imageVector = if (expanded) {
                    Icons.Default.KeyboardArrowDown
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Spacer(modifier = Modifier.size(18.dp))
        }
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (node.isFolder) FontWeight.Medium else FontWeight.Normal,
            color = if (node.isFolder) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
