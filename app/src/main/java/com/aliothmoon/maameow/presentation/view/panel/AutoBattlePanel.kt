package com.aliothmoon.maameow.presentation.view.panel

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.UserAdditionalOper
import com.aliothmoon.maameow.data.model.copilot.CopilotDocumentation
import com.aliothmoon.maameow.data.model.copilot.CopilotListItem
import com.aliothmoon.maameow.data.resource.CharacterInfo
import com.aliothmoon.maameow.data.resource.CopilotResourceProvider
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.OperatorDisplayItem
import com.aliothmoon.maameow.domain.service.OperatorSummaryData
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.presentation.LocalFloatingWindowContext
import com.aliothmoon.maameow.presentation.components.CheckBoxWithExpandableTip
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.InlineConfirmPanel
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.presentation.components.MasteryBadge
import com.aliothmoon.maameow.presentation.components.OperAvatar
import com.aliothmoon.maameow.presentation.components.PageBackHandler
import com.aliothmoon.maameow.presentation.components.operRarityColor
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon
import com.aliothmoon.maameow.presentation.viewmodel.CopilotTabs
import com.aliothmoon.maameow.presentation.viewmodel.CopilotViewModel
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility
import com.aliothmoon.maameow.utils.Misc
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.asString
import com.aliothmoon.maameow.utils.i18n.formatToolboxSyncTime
import com.aliothmoon.maameow.utils.readTextAtMost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import timber.log.Timber

private data class CopilotTabUiSpec(
    val index: Int,
    @param:StringRes val titleRes: Int,
)

@Composable
fun AutoBattlePanel(
    modifier: Modifier = Modifier,
    onSearchOpenChange: (Boolean) -> Unit = {},
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
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(searchOpen) { onSearchOpenChange(searchOpen) }

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
                        val json = try {
                            context.contentResolver.openInputStream(uri)?.use {
                                it.readTextAtMost(MAX_COPILOT_FILE_BYTES)
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "read copilot file failed: $name")
                            null
                        } ?: return@mapNotNull null
                        name to json
                    }
                }
                // 全部读失败也要交给 ViewModel 提示导入失败
                viewModel.onImportLocalFiles(files)
            }
        }
    } else null
    val tabSpecs = listOf(
        CopilotTabUiSpec(
            index = CopilotTabs.MAIN,
            titleRes = R.string.panel_autobattle_tab_mainline,
        ),
        CopilotTabUiSpec(
            index = CopilotTabs.SSS,
            titleRes = R.string.panel_autobattle_tab_security,
        ),
        CopilotTabUiSpec(
            index = CopilotTabs.PARADOX,
            titleRes = R.string.panel_autobattle_tab_paradox,
        ),
        CopilotTabUiSpec(
            index = CopilotTabs.OTHER_ACTIVITY,
            titleRes = R.string.panel_autobattle_tab_other,
        )
    )
    val regularCopilotTab = CopilotTabs.supportsRegularCopilotOptions(state.tabIndex)
    val loopCountSupportedTab = CopilotTabs.supportsLoopCount(state.tabIndex)
    val battleListSupportedTab = CopilotTabs.supportsBattleList(state.tabIndex)

    // 列表平铺进外层 LazyColumn，拖拽状态绑外层：避免嵌套滚动
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        // 非列表项的 key 在 VM 里查不到 id，直接忽略
        val fromId = from.key as? String ?: return@rememberReorderableLazyListState
        val toId = to.key as? String ?: return@rememberReorderableLazyListState
        if (fromId.startsWith(USER_ADDITIONAL_KEY_PREFIX)) {
            viewModel.onReorderUserAdditional(
                fromId.removePrefix(USER_ADDITIONAL_KEY_PREFIX),
                toId.removePrefix(USER_ADDITIONAL_KEY_PREFIX)
            )
        } else {
            viewModel.onReorderList(fromId, toId)
        }
    }
    // 拖一组时另一组退出换位，跨组 onMove 落空会让拖拽项跳位
    var draggingUserAdditional by remember { mutableStateOf(false) }
    val resourceDataManager: ResourceDataManager = koinInject()
    val metadata by resourceDataManager.snapshot.collectAsStateWithLifecycle()
    val operDisplayName: (CharacterInfo) -> String = { info ->
        resourceDataManager.getLocalizedCharacterName(info, metadata.key?.language ?: "zh-cn") ?: info.name
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(TabPanelPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "copilot_tabs") {
                // 加载作业时多数会自动切页签，默认收起
                var expanded by remember { mutableStateOf(false) }
                val currentSpec = tabSpecs.firstOrNull { it.index == state.tabIndex } ?: tabSpecs.first()
                Column {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { expanded = !expanded }
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(currentSpec.titleRes),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (expanded) {
                                    Icons.Default.ExpandLess
                                } else {
                                    Icons.Default.ExpandMore
                                },
                                contentDescription = stringResource(
                                    if (expanded) R.string.common_collapse else R.string.common_expand
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    MaaAnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        CopilotTabGrid(
                            tabSpecs = tabSpecs,
                            selectedIndex = state.tabIndex,
                            onSelect = { index ->
                                viewModel.onTabChanged(index)
                                expanded = false
                            },
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
            item {
                val inputText by viewModel.inputText.collectAsStateWithLifecycle()
                ITextField(
                    value = inputText,
                    onValueChange = viewModel::onInputChanged,
                    label = stringResource(R.string.panel_autobattle_station_code_label),
                    placeholder = stringResource(R.string.panel_autobattle_station_code_placeholder),
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.offset(x = (-4).dp)
                        ) {
                            IconButton(
                                onClick = viewModel::onPasteAndParse,
                                enabled = controlsEnabled,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = stringResource(R.string.copilot_paste_parse),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = viewModel::onToggleBuiltinPicker,
                                enabled = controlsEnabled,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (state.builtinPickerExpanded) {
                                        Icons.Default.ExpandLess
                                    } else {
                                        Icons.Default.ExpandMore
                                    },
                                    contentDescription = stringResource(R.string.copilot_builtin_picker),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                )
            }

            item {
                BuiltinCopilotTree(
                    expanded = state.builtinPickerExpanded,
                    loaded = state.builtinLoaded,
                    tree = state.builtinTree,
                    expandedFolders = state.builtinExpandedFolders,
                    enabled = controlsEnabled,
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
                            onClick = viewModel::onParseInput,
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
                                    filePicker.launch(
                                        arrayOf(
                                            "application/json",
                                            "application/octet-stream"
                                        )
                                    )
                                } else {
                                    Toast.makeText(context, importFloatHint, Toast.LENGTH_SHORT)
                                        .show()
                                }
                            },
                        )
                        // 浮窗不做站内搜索
                        if (isInFloatingWindow) {
                            CopilotActionButton(
                                text = stringResource(R.string.panel_autobattle_station),
                                icon = Icons.Default.Public,
                                filled = false,
                                enabled = true,
                                onClick = { Misc.openUriSafely(context, PRTS_PLUS_URL) },
                            )
                        } else {
                            CopilotActionButton(
                                text = stringResource(R.string.copilot_search_title),
                                icon = Icons.AutoMirrored.Filled.ManageSearch,
                                filled = false,
                                enabled = true,
                                onClick = { searchOpen = true },
                            )
                        }
                    }
                }
            }

            val currentCopilot = state.currentCopilot
            if (currentCopilot != null) {
                // 按载入实例区分，换作业不沿用上一份的展开状态
                item(key = "copilot_detail_${System.identityHashCode(currentCopilot)}") {
                    CopilotDetailCard(
                        stageLabel = state.copilotTaskName,
                        doc = currentCopilot.doc,
                        copilotId = state.copilotId,
                        fromWeb = state.isDataFromWeb,
                        videoUrl = state.videoUrl,
                        warnings = state.requirementWarnings,
                        summary = state.operatorSummary,
                        onOpenVideo = { Misc.openUriSafely(context, state.videoUrl) },
                    )
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
                                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
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
                                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
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

                            CheckBoxWithExpandableTip(
                                checked = state.config.addUserAdditional,
                                onCheckedChange = {
                                    viewModel.onConfigChanged(state.config.copy(addUserAdditional = it))
                                },
                                label = stringResource(R.string.panel_autobattle_user_additional),
                                tipText = stringResource(R.string.panel_autobattle_user_additional_tip)
                            )
                        }
                    }
                }

                if (state.config.formation && state.config.addUserAdditional) {
                    // 手改配置可能重名，key 重复会崩；后续编辑顺带写回去重结果
                    val opers = state.config.userAdditionalOpers.distinctBy { it.name }
                    items(opers, key = { USER_ADDITIONAL_KEY_PREFIX + it.name }) { oper ->
                        val info = remember(oper.name, metadata) {
                            resourceDataManager.getCharacterByNameOrAlias(oper.name)
                        }
                        ReorderableItem(
                            reorderableState,
                            key = USER_ADDITIONAL_KEY_PREFIX + oper.name,
                            enabled = !reorderableState.isAnyItemDragging || draggingUserAdditional
                        ) { isDragging ->
                            UserAdditionalRow(
                                operId = info?.id.orEmpty(),
                                displayName = info?.let(operDisplayName) ?: oper.name,
                                skill = oper.skill,
                                isDragging = isDragging,
                                onDragStarted = {
                                    draggingUserAdditional = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = viewModel::onUserAdditionalReorderSettled,
                                onSkillChange = { skill ->
                                    viewModel.onConfigChanged(
                                        state.config.copy(
                                            userAdditionalOpers = opers.map {
                                                if (it.name == oper.name) it.copy(skill = skill) else it
                                            }
                                        )
                                    )
                                },
                                onRemove = {
                                    viewModel.onConfigChanged(
                                        state.config.copy(userAdditionalOpers = opers - oper)
                                    )
                                },
                            )
                        }
                    }
                    item(key = "user_additional_search") {
                        UserAdditionalSearch(
                            added = remember(opers) { opers.mapTo(HashSet()) { it.name } },
                            clientType = metadata.key?.clientType ?: "Official",
                            displayName = operDisplayName,
                            onAdd = { info ->
                                viewModel.onConfigChanged(
                                    state.config.copy(userAdditionalOpers = opers + UserAdditionalOper(info.name))
                                )
                            },
                            resourceDataManager = resourceDataManager,
                        )
                    }
                }

                if (state.config.formation) {
                    item(key = "operbox_assist") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val assist = state.operBoxAssist
                            CheckBoxWithExpandableTip(
                                checked = assist.active,
                                onCheckedChange = viewModel::onToggleOperBoxAssist,
                                label = stringResource(R.string.panel_autobattle_operbox_assist),
                                tipText = stringResource(R.string.panel_autobattle_operbox_assist_tip),
                                enabled = assist.available,
                            )
                            // 数据不可用时也露出同步入口，免得勾不上又找不到去哪拉数据
                            if (assist.yituliuEnabled && (assist.enabled || !assist.dataUsable)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (assist.syncTimeMillis > 0L) {
                                            stringResource(
                                                R.string.panel_toolbox_last_sync,
                                                formatToolboxSyncTime(assist.syncTimeMillis),
                                            )
                                        } else {
                                            stringResource(R.string.panel_toolbox_never_synced)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedButton(
                                        onClick = viewModel::onSyncOperBox,
                                        enabled = !state.syncingOperBox,
                                        shape = compactButtonShape,
                                        contentPadding = compactButtonPadding,
                                    ) { Text(stringResource(R.string.panel_autobattle_operbox_sync)) }
                                }
                            }
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

                    if (state.listModeActive && state.tabIndex == CopilotTabs.MAIN) {
                        CheckBoxWithLabel(
                            checked = state.config.useSanityPotion,
                            onCheckedChange = {
                                viewModel.onConfigChanged(state.config.copy(useSanityPotion = it))
                            },
                            label = stringResource(R.string.panel_autobattle_use_sanity_potion)
                        )
                    }

                    if (!state.listModeActive && loopCountSupportedTab) {
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


            if (battleListSupportedTab && (state.listModeActive || state.taskList.isNotEmpty())) {
                // 列表长了也能随手清理
                stickyHeader(key = "battle_list_header") {
                    val showDifficulty = state.tabIndex != CopilotTabs.PARADOX
                    val selected = state.taskList.firstOrNull { it.id == state.selectedListItemId }
                    BattleListHeader(
                        checked = state.taskList.count { it.isChecked },
                        total = state.taskList.size,
                        listModeActive = state.listModeActive,
                        current = selected?.let {
                            if (!showDifficulty) it.name else stringResource(
                                R.string.panel_autobattle_current_item,
                                it.name,
                                stringResource(
                                    if (it.isRaid) R.string.panel_autobattle_difficulty_raid
                                    else R.string.panel_autobattle_difficulty_normal
                                )
                            )
                        },
                        enabled = controlsEnabled,
                        onCleanUnchecked = viewModel::onCleanUnchecked,
                        onClearList = viewModel::onClearList,
                    )
                }
                if (state.taskList.isEmpty()) {
                    item(key = "battle_list_empty") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.panel_autobattle_empty_entries),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
                itemsIndexed(
                    state.taskList,
                    key = { _, item -> item.id }
                ) { index, item ->
                    ReorderableItem(
                        reorderableState,
                        key = item.id,
                        enabled = !reorderableState.isAnyItemDragging || !draggingUserAdditional
                    ) { isDragging ->
                        BattleListRow(
                            item = item,
                            isDragging = isDragging,
                            onDragStarted = {
                                draggingUserAdditional = false
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragStopped = viewModel::onReorderSettled,
                            showDifficulty = state.tabIndex != CopilotTabs.PARADOX,
                            onToggle = { viewModel.onToggleListItem(index) },
                            onLoad = { viewModel.onSelectListItem(index) },
                            onRaidChange = { viewModel.onSetListItemRaid(item.id, it) },
                            onDuplicate = { viewModel.onDuplicateListItem(item.id) },
                            onRemove = { viewModel.onRemoveFromList(index) },
                        )
                    }
                }
                // 吸顶要等下一个吸顶项才会被顶走，零高度占位免得压住下面的提示
                stickyHeader(key = "battle_list_end") {
                    Spacer(modifier = Modifier.fillMaxWidth())
                }
            }

            item(key = "tips") {
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

        if (searchOpen) {
            PageBackHandler { searchOpen = false }
            CopilotSearchSheet(
                copilotState = state,
                onPick = viewModel::onPickFromSearch,
                onAddSet = viewModel::onAddSetFromSearch,
                onRemovePrts = viewModel::onRemovePrtsCopilot,
                onRemoveListItem = viewModel::onRemoveListItem,
                onClearList = viewModel::onClearList,
                onClose = { searchOpen = false },
            )
        }
    }
}

@Composable
private fun CopilotTabGrid(
    tabSpecs: List<CopilotTabUiSpec>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelMedium
    val titles = tabSpecs.map { stringResource(it.titleRes) }
    // 最宽标签（按选中态粗体）放不进半宽就退化成单列
    val widestLabel = remember(titles, labelStyle) {
        val boldStyle = labelStyle.copy(fontWeight = FontWeight.Bold)
        with(density) {
            titles.maxOf { title ->
                textMeasurer.measure(title, boldStyle, softWrap = false).size.width
            }.toDp()
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val spacing = 6.dp
        val cellInset = 8.dp * 2 + 2.dp
        val columns = if (widestLabel <= (maxWidth - spacing) / 2 - cellInset) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            tabSpecs.chunked(columns).forEach { rowSpecs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    rowSpecs.forEach { spec ->
                        val selected = selectedIndex == spec.index
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
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(spec.index) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(spec.titleRes),
                                    style = labelStyle,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (rowSpecs.size < columns) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 吸顶时底下的行会从它下面滚过，底色必须不透明（玻璃主题的 surface 带透明度） */
@Composable
private fun BattleListHeader(
    checked: Int,
    total: Int,
    listModeActive: Boolean,
    /** 详情卡当前载入的那一行，列表离详情卡远，靠这里认 */
    current: String?,
    enabled: Boolean,
    onCleanUnchecked: () -> Unit,
    onClearList: () -> Unit,
) {
    var tipExpanded by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 1f),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 当前关卡只占剩余宽度，长了截断，不挤标题和按钮
                Text(
                    stringResource(R.string.panel_autobattle_copilot_list),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
                if (total > 0) {
                    Text(
                        text = "$checked/$total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                ExpandableTipIcon(expanded = tipExpanded, onExpandedChange = { tipExpanded = it })
                Box(modifier = Modifier.weight(1f)) {
                    if (current != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text(
                                text = current,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onCleanUnchecked,
                    enabled = enabled && checked < total,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.PlaylistRemove,
                        contentDescription = stringResource(R.string.panel_autobattle_clear_unchecked),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = { confirmClear = true },
                    enabled = enabled && total > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = stringResource(R.string.panel_autobattle_clear_list),
                        tint = if (enabled && total > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (!listModeActive) {
                Text(
                    stringResource(R.string.panel_autobattle_battle_list_inactive_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            ExpandableTipContent(
                visible = tipExpanded,
                tipText = stringResource(R.string.panel_autobattle_sequence_tip)
            )
            InlineConfirmPanel(
                visible = confirmClear,
                message = stringResource(R.string.panel_autobattle_clear_list_confirm, total),
                onConfirm = {
                    confirmClear = false
                    onClearList()
                },
                onDismiss = { confirmClear = false },
                modifier = Modifier.padding(end = 8.dp, bottom = 6.dp)
            )
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.BattleListRow(
    item: CopilotListItem,
    isDragging: Boolean,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit,
    showDifficulty: Boolean,
    onToggle: () -> Unit,
    onLoad: () -> Unit,
    onRaidChange: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    val loadLabel = stringResource(R.string.common_load)
    val toggleLabel = stringResource(R.string.panel_autobattle_toggle_checked)
    val duplicateLabel = stringResource(R.string.panel_autobattle_duplicate_item)
    val deleteLabel = stringResource(R.string.common_delete)
    val normalLabel = stringResource(R.string.panel_autobattle_difficulty_normal)
    val raidLabel = stringResource(R.string.panel_autobattle_difficulty_raid)
    val switchLabel = stringResource(
        R.string.panel_autobattle_switch_difficulty,
        if (item.isRaid) normalLabel else raidLabel
    )
    val currentOnToggle by rememberUpdatedState(onToggle)
    val currentOnLoad by rememberUpdatedState(onLoad)
    val currentOnRaidChange by rememberUpdatedState(onRaidChange)
    val currentOnDuplicate by rememberUpdatedState(onDuplicate)
    val currentOnRemove by rememberUpdatedState(onRemove)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isDragging) Modifier.shadow(4.dp, shape) else Modifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // 整行点击载入详情，行内控件各自消费点击
            .clickable(onClickLabel = loadLabel, onClick = onLoad)
            // 开着任一无障碍服务时，每滚一帧都要遍历整屏未合并语义树（merge 无效）；
            // 整行清成一个节点，子树不再参与遍历，其余操作走自定义动作
            .clearAndSetSemantics {
                contentDescription = item.name
                toggleableState = ToggleableState(item.isChecked)
                if (showDifficulty) stateDescription = if (item.isRaid) raidLabel else normalLabel
                onClick(label = loadLabel) {
                    currentOnLoad()
                    true
                }
                customActions = buildList {
                    add(CustomAccessibilityAction(toggleLabel) { currentOnToggle(); true })
                    if (showDifficulty) {
                        add(CustomAccessibilityAction(switchLabel) { currentOnRaidChange(!item.isRaid); true })
                        add(CustomAccessibilityAction(duplicateLabel) { currentOnDuplicate(); true })
                    }
                    add(CustomAccessibilityAction(deleteLabel) { currentOnRemove(); true })
                }
            }
            .padding(start = 2.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 只有把手能拖，行的其余区域保持滚动 / 点击语义
        Icon(
            Icons.Default.DragIndicator,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(32.dp)
                .draggableHandle(
                    onDragStarted = { onDragStarted() },
                    onDragStopped = onDragStopped,
                )
                .padding(6.dp)
        )
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp, vertical = 6.dp)
        )
        if (showDifficulty) {
            DifficultyToggle(isRaid = item.isRaid, onChange = onRaidChange)
            IconButton(
                onClick = onDuplicate,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun DifficultyToggle(isRaid: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        listOf(
            false to R.string.panel_autobattle_difficulty_normal,
            true to R.string.panel_autobattle_difficulty_raid,
        ).forEach { (raid, labelRes) ->
            val selected = raid == isRaid
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                modifier = Modifier
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton) { onChange(raid) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun CopilotDetailCard(
    stageLabel: String,
    doc: CopilotDocumentation,
    copilotId: Int,
    fromWeb: Boolean,
    videoUrl: String,
    warnings: List<UiText>,
    summary: OperatorSummaryData?,
    onOpenVideo: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val operators = summary?.takeUnless { it.isEmpty }
    val resourceDataManager: ResourceDataManager = koinInject()
    // 整张卡查一次干员表，不让每格各自订阅
    val nameIndex by resourceDataManager.nameIndex.collectAsStateWithLifecycle()
    val characters = remember(operators, nameIndex) {
        operators?.let { resourceDataManager.charactersOf(it) }.orEmpty()
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 点击区贴卡片边缘，波纹才铺满
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = stringResource(
                            if (expanded) R.string.common_collapse else R.string.common_expand
                        )
                    ) { expanded = !expanded }
                    .padding(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        Text(
                            text = doc.title.ifBlank { stageLabel },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (expanded) 2 else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                CopilotMetaRow(
                    // 无标题时标题位已是关卡名
                    stageLabel = stageLabel.takeIf { doc.title.isNotBlank() }.orEmpty(),
                    source = when {
                        !fromWeb -> stringResource(R.string.panel_autobattle_source_local)
                        copilotId > 0 -> stringResource(R.string.panel_autobattle_source_web, copilotId)
                        // 列表里被校正过的作业站条目不带 id
                        else -> stringResource(R.string.panel_autobattle_source_web_unnumbered)
                    },
                    fromWeb = fromWeb,
                    hasVideo = videoUrl.isNotBlank(),
                    onOpenVideo = onOpenVideo,
                )
                if (operators != null && !expanded) {
                    OperatorStrip(operators, characters, modifier = Modifier.fillMaxWidth())
                }
            }

            if (warnings.isNotEmpty()) {
                Box(modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
                    CopilotWarnings(warnings)
                }
            }

            MaaAnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (doc.details.isNotBlank()) {
                        CopilotDetails(doc.details)
                    }
                    operators?.let { CopilotOperatorSection(it, characters) }
                }
            }
        }
    }
}

@Composable
private fun CopilotOperatorSection(summary: OperatorSummaryData, characters: Map<String, CharacterInfo?>) {
    Column(
        modifier = Modifier.padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.panel_autobattle_operator_header),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            CountBadge(summary.totalCount)
        }
        if (summary.operators.isNotEmpty()) {
            OperatorGrid { OperatorCells(summary.operators, characters) }
        }
        summary.groups.forEach { (groupName, opers) ->
            OperatorGroup(groupName, opers, characters)
        }
    }
}

/** 组内按序取首个可用干员，默认只露首选 */
@Composable
internal fun OperatorGroup(
    name: String,
    opers: List<OperatorDisplayItem>,
    characters: Map<String, CharacterInfo?>,
) {
    var expanded by rememberSaveable(opers) { mutableStateOf(false) }
    val hidden = opers.size - 1
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        // outlineVariant 贴近卡片底色看不见
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.panel_autobattle_group_header, name),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            OperatorGrid {
                OperatorCells(if (expanded) opers else opers.take(1), characters)
                if (hidden > 0) {
                    GroupToggleTile(expanded = expanded, hidden = hidden) { expanded = !expanded }
                }
            }
        }
    }
}

@Composable
private fun GroupToggleTile(expanded: Boolean, hidden: Int, onClick: () -> Unit) {
    val tint = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!expanded) {
                Text(
                    text = "+$hidden",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = tint
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(if (expanded) R.string.common_collapse else R.string.common_expand),
                style = MaterialTheme.typography.labelSmall,
                color = tint
            )
        }
    }
}

internal const val PRTS_PLUS_URL = "https://prts.plus"

private const val DETAIL_COLLAPSED_LINES = 2

/** 作业 JSON 正常只有几十 KB，超限多半是选错了文件（ANDROID-13W） */
private const val MAX_COPILOT_FILE_BYTES = 4 * 1024 * 1024

@Composable
private fun CopilotMetaRow(
    stageLabel: String,
    source: String,
    fromWeb: Boolean,
    hasVideo: Boolean,
    onOpenVideo: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (stageLabel.isNotBlank()) {
                MetaChip(
                    text = stageLabel,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    bold = true,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            MetaChip(
                text = source,
                icon = if (fromWeb) Icons.Default.Public else Icons.Default.Description,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        if (hasVideo) {
            // 免得最小触控尺寸把整行撑高
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Surface(
                    onClick = onOpenVideo,
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.common_video),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    bold: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (bold) FontWeight.Bold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun CopilotDetails(details: String) {
    var expanded by rememberSaveable(details) { mutableStateOf(false) }
    var overflowed by remember(details) { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val bar = 3.dp.toPx()
                drawRoundRect(
                    color = accent,
                    size = Size(bar, size.height),
                    cornerRadius = CornerRadius(bar / 2)
                )
            }
            .padding(start = 10.dp)
    ) {
        SelectionContainer {
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else DETAIL_COLLAPSED_LINES,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) overflowed = it.hasVisualOverflow }
            )
        }
        if (overflowed || expanded) {
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { expanded = !expanded }
                    .padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(if (expanded) R.string.common_collapse else R.string.common_expand),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun CopilotWarnings(warnings: List<UiText>) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(16.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                warnings.forEach { warning ->
                    Text(text = warning.asString(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private val OPER_CELL_MIN_WIDTH = 140.dp
private val OPER_GRID_GAP = 6.dp
private val OPER_RARITY_BAR = 3.dp

/** 下标为 module - 1 */
private val MODULE_NAMES = arrayOf("χ", "γ", "α", "Δ", "β")

@Composable
internal fun OperatorCells(items: List<OperatorDisplayItem>, characters: Map<String, CharacterInfo?>) {
    items.forEach { item ->
        val info = characters[item.name]
        OperatorCell(item, operId = info?.id.orEmpty(), rarity = info?.rarity ?: 0)
    }
}

/** 放得下两格就排双列，同行取较高者对齐；不用 BoxWithConstraints 免子组合 */
@Composable
internal fun OperatorGrid(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = OPER_GRID_GAP.roundToPx()
        val minCell = OPER_CELL_MIN_WIDTH.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else minCell
        val columns = if (width >= minCell * 2 + gap) 2 else 1
        val cellWidth = (width - gap * (columns - 1)) / columns
        val rows = measurables.chunked(columns).map { row ->
            val height = row.maxOf { it.minIntrinsicHeight(cellWidth) }
            row.map { it.measure(Constraints.fixed(cellWidth, height)) }
        }
        val totalHeight = rows.sumOf { it.first().height } + gap * (rows.size - 1).coerceAtLeast(0)
        layout(width, totalHeight) {
            var y = 0
            rows.forEach { row ->
                row.forEachIndexed { column, placeable ->
                    placeable.placeRelative(column * (cellWidth + gap), y)
                }
                y += row.first().height + gap
            }
        }
    }
}

@Composable
private fun OperatorCell(item: OperatorDisplayItem, operId: String, rarity: Int) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val rarityColor = if (rarity > 0) operRarityColor(rarity) else Color.Transparent
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    // 查不到头像时留底色占位
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .drawWithContent {
                        drawContent()
                        val bar = OPER_RARITY_BAR.toPx()
                        drawRect(
                            color = rarityColor,
                            topLeft = Offset(0f, size.height - bar),
                            size = Size(size.width, bar)
                        )
                    }
            ) {
                OperAvatar(operId = operId, modifier = Modifier.fillMaxSize())
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                OperatorAttributes(item)
            }
        }
    }
}

/** 固定两行单行文本：网格按固有高度定格高，折行会被裁掉 */
@Composable
private fun OperatorAttributes(item: OperatorDisplayItem) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val promotionText = item.promotion?.let { promotion ->
        val elite = stringResource(
            when (promotion.elite) {
                0 -> R.string.panel_autobattle_oper_elite_0
                1 -> R.string.panel_autobattle_oper_elite_1
                else -> R.string.panel_autobattle_oper_elite_2
            }
        )
        // 校正器只补精英化不补等级，0 级不显示
        if (promotion.level > 0) {
            stringResource(R.string.panel_autobattle_oper_promotion, elite, promotion.level)
        } else {
            elite
        }
    }
    val moduleText = item.module?.let { module ->
        if (module == 0) {
            stringResource(R.string.panel_autobattle_oper_no_module)
        } else {
            stringResource(R.string.panel_autobattle_oper_module, MODULE_NAMES[module - 1])
        }
    }
    if (promotionText != null || moduleText != null) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            promotionText?.let { OperAttrText(it, muted) }
            moduleText?.let { OperAttrText(it, muted) }
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.skill > 0) {
            OperAttrText(
                text = stringResource(R.string.panel_autobattle_oper_skill, item.skill),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            val skillLevel = item.skillLevel
            if (skillLevel != null && skillLevel > 7) {
                val mastery = skillLevel - 7
                MasteryBadge(level = mastery, modifier = Modifier.size(10.dp))
                val res = when (mastery) {
                    1 -> R.string.panel_autobattle_oper_mastery_1
                    2 -> R.string.panel_autobattle_oper_mastery_2
                    else -> R.string.panel_autobattle_oper_mastery_3
                }
                OperAttrText(stringResource(res), muted)
            } else if (skillLevel != null) {
                OperAttrText(stringResource(R.string.panel_autobattle_oper_skill_level, skillLevel), muted)
            }
        } else {
            OperAttrText(
                stringResource(R.string.panel_autobattle_oper_no_skill),
                MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun OperAttrText(
    text: String,
    color: Color,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = fontWeight,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
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
private fun BuiltinCopilotTree(
    expanded: Boolean,
    loaded: Boolean,
    tree: List<CopilotResourceProvider.Node>,
    expandedFolders: Set<String>,
    enabled: Boolean,
    onToggleFolder: (String) -> Unit,
    onSelectFile: (CopilotResourceProvider.Node) -> Unit,
    modifier: Modifier = Modifier,
) {
    MaaAnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
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
