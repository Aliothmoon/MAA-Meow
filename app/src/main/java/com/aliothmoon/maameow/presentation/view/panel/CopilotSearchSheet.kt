package com.aliothmoon.maameow.presentation.view.panel

import android.icu.text.CompactDecimalFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.LogColorRole
import com.aliothmoon.maameow.data.model.copilot.CopilotListItem
import com.aliothmoon.maameow.data.resource.CharacterInfo
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.CopilotSetInfo
import com.aliothmoon.maameow.domain.service.OperatorMatch
import com.aliothmoon.maameow.domain.service.OperatorMatchMode
import com.aliothmoon.maameow.domain.service.OperatorSummaryData
import com.aliothmoon.maameow.presentation.components.ISearchField
import com.aliothmoon.maameow.presentation.components.InlineConfirmPanel
import com.aliothmoon.maameow.presentation.components.LucideIcons
import com.aliothmoon.maameow.presentation.components.OperAvatar
import com.aliothmoon.maameow.presentation.viewmodel.CopilotSearchItem
import com.aliothmoon.maameow.presentation.viewmodel.CopilotSearchOrder
import com.aliothmoon.maameow.presentation.viewmodel.CopilotSearchTab
import com.aliothmoon.maameow.presentation.viewmodel.CopilotSearchUiState
import com.aliothmoon.maameow.presentation.viewmodel.CopilotSearchViewModel
import com.aliothmoon.maameow.presentation.viewmodel.CopilotSetSearchUiState
import com.aliothmoon.maameow.presentation.viewmodel.CopilotTabs
import com.aliothmoon.maameow.presentation.viewmodel.CopilotUiState
import com.aliothmoon.maameow.presentation.viewmodel.OperatorMatchFilter
import com.aliothmoon.maameow.presentation.viewmodel.SearchResults
import com.aliothmoon.maameow.presentation.viewmodel.StageSuggestion
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility
import com.aliothmoon.maameow.theme.themedColor
import com.aliothmoon.maameow.utils.Misc
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.asString
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.koin.compose.koinInject

@Composable
internal fun CopilotSearchSheet(
    copilotState: CopilotUiState,
    onPick: (Int, (UiText) -> Unit) -> Unit,
    onAddSet: (Int, (UiText) -> Unit) -> Unit,
    onRemovePrts: (Int) -> Unit,
    onRemoveListItem: (String) -> Unit,
    onClearList: () -> Unit,
    onClose: () -> Unit,
    viewModel: CopilotSearchViewModel = koinInject(),
    resourceDataManager: ResourceDataManager = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val setState by viewModel.setState.collectAsStateWithLifecycle()
    val nameIndex by resourceDataManager.nameIndex.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val countFormat = remember(locale) {
        CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT)
    }
    val listMode = copilotState.listModeActive
    val listed = remember(copilotState.taskList) {
        copilotState.taskList.mapNotNullTo(HashSet()) { it.prtsId }
    }
    val busy = copilotState.isLoading
    var addingCopilotId by remember { mutableStateOf<Int?>(null) }
    var addingSetId by remember { mutableStateOf<Int?>(null) }
    var failure by remember { mutableStateOf<UiText>(UiText.Empty) }

    LaunchedEffect(Unit) { viewModel.onOpened() }

    val copilotListState = rememberLazyListState()
    val setListState = rememberLazyListState()
    val tab = state.tab
    val listState = if (tab == CopilotSearchTab.COPILOT) copilotListState else setListState
    // 带页码，每页都是新值，distinct 才不吞翻页
    LaunchedEffect(listState, tab) {
        snapshotFlow {
            val info = listState.layoutInfo
            val atEnd = info.totalItemsCount > 0 &&
                    (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 1
            val results = if (tab == CopilotSearchTab.COPILOT) state.results else setState.results
            if (atEnd && results.canLoadMore) results.page else -1
        }.distinctUntilChanged().filter { it >= 0 }.collect {
            when (tab) {
                CopilotSearchTab.COPILOT -> viewModel.onLoadMore()
                CopilotSearchTab.SET -> viewModel.onSetLoadMore()
            }
        }
    }

    val copilotRow: @Composable (CopilotSearchItem, Modifier) -> Unit = { item, modifier ->
        val characters = remember(item.summary, nameIndex) { resourceDataManager.charactersOf(item.summary) }
        CopilotSearchRow(
            item = item,
            match = item.match.takeIf { state.matchFilter.active },
            characters = characters,
            countFormat = countFormat,
            listMode = listMode,
            added = listMode && item.id in listed,
            loading = busy && addingCopilotId == item.id,
            onPick = {
                // 忙时不置灰整列，点了直接忽略
                if (!busy) {
                    failure = UiText.Empty
                    addingCopilotId = item.id
                    addingSetId = null
                    onPick(item.id) { failure = it }
                    if (!listMode) onClose()
                }
            },
            onRemove = { onRemovePrts(item.id) },
            modifier = modifier,
        )
    }

    // 紧凑布局不要 48dp 最小触控区把行撑高
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.fillMaxSize().padding(TabPanelPadding),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_close),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.copilot_search_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { Misc.openUriSafely(context, PRTS_PLUS_URL) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.panel_autobattle_station))
                    }
                }

                if (listMode) {
                    AddedSection(
                        items = copilotState.taskList,
                        showDifficulty = copilotState.tabIndex != CopilotTabs.PARADOX,
                        onRemove = onRemoveListItem,
                        onClear = onClearList,
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.copilot_search_single_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (failure != UiText.Empty) {
                    FailureLine(failure.asString(), onDismiss = { failure = UiText.Empty })
                }

                SearchTabs(
                    selected = tab,
                    refreshing = if (tab == CopilotSearchTab.COPILOT) state.results.refreshing else setState.results.refreshing,
                    onSelect = viewModel::onTabChange,
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when (tab) {
                        CopilotSearchTab.COPILOT -> copilotResults(state, viewModel, copilotRow)
                        CopilotSearchTab.SET -> setResults(
                            state = setState,
                            viewModel = viewModel,
                            addingSetId = addingSetId.takeIf { busy },
                            onAddSet = { id ->
                                if (!busy) {
                                    failure = UiText.Empty
                                    addingSetId = id
                                    addingCopilotId = null
                                    onAddSet(id) { failure = it }
                                }
                            },
                            copilotRow = copilotRow,
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.copilotResults(
    state: CopilotSearchUiState,
    viewModel: CopilotSearchViewModel,
    copilotRow: @Composable (CopilotSearchItem, Modifier) -> Unit,
) {
    item(key = "query") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KeywordBar(
                value = state.keyword,
                onValueChange = viewModel::onKeywordChange,
                placeholder = stringResource(R.string.copilot_search_keyword),
                onSearch = viewModel::onSearch,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ISearchField(
                    value = state.stageText,
                    onValueChange = viewModel::onStageTextChange,
                    placeholder = stringResource(R.string.copilot_search_stage),
                    leadingIcon = Icons.Outlined.Flag,
                    onSearch = viewModel::onSearch,
                    modifier = Modifier.weight(1f),
                )
                OrderSegments(selected = state.order, onSelect = viewModel::onOrderChange)
            }
            if (state.suggestions.isNotEmpty()) {
                StageSuggestions(state.suggestions, onPick = viewModel::onPickStage)
            }
            if (state.matchFilter.available) {
                MatchFilterBar(
                    filter = state.matchFilter,
                    onToggle = viewModel::onToggleMatch,
                    onToggleMode = viewModel::onToggleMatchMode,
                )
            }
        }
    }
    val results = state.results
    val shown = state.shownItems
    errorItem(results.error)
    items(shown, key = { it.id }) { item -> copilotRow(item, staleAlpha(results.refreshing)) }
    item(key = "footer") {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            val hidden = results.items.size - shown.size
            if (hidden > 0) {
                Text(
                    text = stringResource(R.string.copilot_match_hidden, hidden),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ListFooter(results, stringResource(R.string.copilot_search_empty), viewModel::onLoadMore)
        }
    }
}

private fun LazyListScope.setResults(
    state: CopilotSetSearchUiState,
    viewModel: CopilotSearchViewModel,
    addingSetId: Int?,
    onAddSet: (Int) -> Unit,
    copilotRow: @Composable (CopilotSearchItem, Modifier) -> Unit,
) {
    val results = state.results
    item(key = "set-query") {
        KeywordBar(
            value = state.keyword,
            onValueChange = viewModel::onSetKeywordChange,
            placeholder = stringResource(R.string.copilot_search_set_keyword),
            onSearch = viewModel::onSetSearch,
        )
    }
    errorItem(results.error)
    results.items.forEach { set ->
        val expanded = set.id in state.expanded
        item(key = "set-${set.id}") {
            SetRow(
                set = set,
                expanded = expanded,
                loading = addingSetId == set.id,
                onToggle = { viewModel.onToggleSet(set) },
                onAddAll = { onAddSet(set.id) },
                modifier = staleAlpha(results.refreshing),
            )
        }
        if (!expanded) return@forEach
        val contents = state.contents[set.id]
        val error = state.contentErrors[set.id]
        when {
            set.id in state.contentLoading -> item(key = "set-${set.id}-loading") {
                Box(modifier = Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }

            error != null -> item(key = "set-${set.id}-error") {
                NestedHint(error.asString(), MaterialTheme.colorScheme.error)
            }

            contents.isNullOrEmpty() -> item(key = "set-${set.id}-empty") {
                NestedHint(stringResource(R.string.copilot_search_set_unavailable))
            }

            else -> items(contents, key = { "set-${set.id}-${it.id}" }) { item ->
                copilotRow(item, staleAlpha(results.refreshing).padding(start = 12.dp))
            }
        }
    }
    item(key = "set-footer") {
        ListFooter(results, stringResource(R.string.copilot_search_set_empty), viewModel::onSetLoadMore)
    }
}

private fun staleAlpha(refreshing: Boolean): Modifier = Modifier.alpha(if (refreshing) 0.4f else 1f)

private fun LazyListScope.errorItem(error: UiText) {
    if (error == UiText.Empty) return
    item(key = "error") {
        Text(
            text = error.asString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun NestedHint(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(start = 12.dp)
    )
}

@Composable
private fun KeywordBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ISearchField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            leadingIcon = Icons.Default.Search,
            onSearch = onSearch,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onSearch,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            modifier = Modifier.height(40.dp)
        ) { Text(stringResource(R.string.copilot_search_action)) }
    }
}

@Composable
private fun ListFooter(results: SearchResults<*>, emptyText: String, onLoadMore: () -> Unit) {
    val failed = results.error != UiText.Empty
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            results.hasNext && (failed || results.paused) -> TextButton(onClick = onLoadMore) {
                Text(stringResource(R.string.copilot_search_load_more))
            }

            // 重查时旧结果还在，进度看页签下那条
            results.refreshing && results.items.isNotEmpty() -> Unit

            // 页脚一露出就会触发加载，先显示转圈
            results.loading || results.hasNext ->
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)

            results.searched && results.items.isEmpty() && !failed -> Text(
                text = emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MatchFilterBar(
    filter: OperatorMatchFilter,
    onToggle: (Boolean) -> Unit,
    onToggleMode: (OperatorMatchMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            FilterChip(
                selected = filter.enabled,
                onClick = { onToggle(!filter.enabled) },
                label = { Text(stringResource(R.string.copilot_match_toggle), style = MaterialTheme.typography.bodySmall) },
                leadingIcon = checkIconIf(filter.enabled),
            )
            OperatorMatchMode.entries.forEach { mode ->
                val selected = mode in filter.modes
                FilterChip(
                    selected = selected,
                    enabled = filter.enabled,
                    onClick = { onToggleMode(mode) },
                    label = { Text(stringResource(mode.labelRes), style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = checkIconIf(selected),
                )
            }
        }
        if (filter.enabled && filter.partial) {
            Text(
                text = stringResource(R.string.copilot_match_partial),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun checkIconIf(checked: Boolean): (@Composable () -> Unit)? = if (checked) {
    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
} else null

private val OperatorMatchMode.labelRes: Int
    get() = when (this) {
        OperatorMatchMode.READY -> R.string.copilot_match_ready
        OperatorMatchMode.BORROW -> R.string.copilot_match_borrow
        OperatorMatchMode.TRAIN -> R.string.copilot_match_train
        OperatorMatchMode.BLOCKED -> R.string.copilot_match_blocked
    }

@Composable
private fun MatchBadge(mode: OperatorMatchMode) {
    val color = when (mode) {
        OperatorMatchMode.READY -> LogColorRole.SUCCESS
        OperatorMatchMode.BORROW -> LogColorRole.INFO
        OperatorMatchMode.TRAIN -> LogColorRole.WARNING
        OperatorMatchMode.BLOCKED -> LogColorRole.ERROR
    }.themedColor()
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.14f), contentColor = color) {
        Text(
            text = stringResource(mode.labelRes),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun SearchTabs(selected: CopilotSearchTab, refreshing: Boolean, onSelect: (CopilotSearchTab) -> Unit) {
    Box {
        PrimaryTabRow(selectedTabIndex = selected.ordinal, containerColor = Color.Transparent) {
            CopilotSearchTab.entries.forEach { tab ->
                val (labelRes, icon) = when (tab) {
                    CopilotSearchTab.COPILOT -> R.string.copilot_search_tab_copilot to LucideIcons.Swords
                    CopilotSearchTab.SET -> R.string.copilot_search_tab_set to LucideIcons.ListTodo
                }
                LeadingIconTab(
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    text = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge) },
                    icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.height(40.dp),
                )
            }
        }
        if (refreshing) {
            LinearProgressIndicator(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp)
            )
        }
    }
}

@Composable
private fun OrderSegments(selected: CopilotSearchOrder, onSelect: (CopilotSearchOrder) -> Unit) {
    val entries = CopilotSearchOrder.entries
    SingleChoiceSegmentedButtonRow {
        entries.forEachIndexed { index, order ->
            SegmentedButton(
                selected = order == selected,
                onClick = { onSelect(order) },
                shape = SegmentedButtonDefaults.itemShape(index, entries.size, RoundedCornerShape(8.dp)),
                icon = {},
                label = { Text(stringResource(order.labelRes), maxLines = 1) },
            )
        }
    }
}

@Composable
private fun FailureLine(text: String, onDismiss: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.common_close),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** 展开后最多露 3 行半，提示还能滚 */
@Composable
private fun AddedSection(
    items: List<CopilotListItem>,
    showDifficulty: Boolean,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = stringResource(if (expanded) R.string.common_collapse else R.string.common_expand)
                ) { expanded = !expanded }
                .heightIn(min = 32.dp)
                .padding(start = 10.dp, end = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.copilot_search_added_list),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            CountBadge(items.size)
            Spacer(modifier = Modifier.weight(1f))
            if (items.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = stringResource(R.string.panel_autobattle_clear_list),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        InlineConfirmPanel(
            visible = confirmClear && items.isNotEmpty(),
            message = stringResource(R.string.panel_autobattle_clear_list_confirm, items.size),
            onConfirm = {
                confirmClear = false
                onClear()
            },
            onDismiss = { confirmClear = false },
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
        )
        MaaAnimatedVisibility(visible = expanded) {
            if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.panel_autobattle_empty_entries),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 112.dp).padding(bottom = 2.dp)) {
                    items(items, key = { it.id }) { item ->
                        AddedRow(item, showDifficulty, onRemove = { onRemove(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AddedRow(item: CopilotListItem, showDifficulty: Boolean, onRemove: () -> Unit) {
    val deleteLabel = stringResource(R.string.common_delete)
    val difficulty = stringResource(
        if (item.isRaid) R.string.panel_autobattle_difficulty_raid
        else R.string.panel_autobattle_difficulty_normal
    )
    val currentOnRemove by rememberUpdatedState(onRemove)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .alpha(if (item.isChecked) 1f else 0.5f)
            .clearAndSetSemantics {
                contentDescription = "${item.name} ${item.title}".trim()
                if (showDifficulty) stateDescription = difficulty
                customActions = listOf(CustomAccessibilityAction(deleteLabel) { currentOnRemove(); true })
            }
            .padding(start = 10.dp, end = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            if (item.title.isNotBlank()) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showDifficulty) {
            Text(
                text = difficulty,
                style = MaterialTheme.typography.labelSmall,
                color = if (item.isRaid) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun StageSuggestions(suggestions: List<StageSuggestion>, onPick: (StageSuggestion) -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            suggestions.forEach { suggestion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(suggestion) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = suggestion.code,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = suggestion.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // 主线与磨难同编号，靠 id 区分
                    Text(
                        text = suggestion.stageId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SetRow(
    set: CopilotSetInfo,
    expanded: Boolean,
    loading: Boolean,
    onToggle: () -> Unit,
    onAddAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggleLabel = stringResource(if (expanded) R.string.common_collapse else R.string.common_expand)
    val addAllLabel = stringResource(R.string.copilot_search_set_add_all)
    val countText = stringResource(R.string.copilot_search_set_count, set.copilotIds.size)
    val canAdd = set.copilotIds.isNotEmpty()
    val currentOnToggle by rememberUpdatedState(onToggle)
    val currentOnAddAll by rememberUpdatedState(onAddAll)
    val subtitle = remember(set) {
        listOf(set.creator, set.description.replace('\n', ' ').trim())
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = toggleLabel, onClick = onToggle)
            .clearAndSetSemantics {
                contentDescription = "${set.name}, $countText"
                onClick(label = toggleLabel) { currentOnToggle(); true }
                if (canAdd) {
                    customActions = listOf(CustomAccessibilityAction(addAllLabel) { currentOnAddAll(); true })
                }
            }
            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = set.name.ifBlank { "#${set.id}" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = countText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        if (loading) {
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = onAddAll, enabled = canAdd, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = null,
                    tint = if (canAdd) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun CopilotSearchRow(
    item: CopilotSearchItem,
    match: OperatorMatch?,
    characters: Map<String, CharacterInfo?>,
    countFormat: CompactDecimalFormat,
    listMode: Boolean,
    added: Boolean,
    loading: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val toggleLabel = stringResource(if (expanded) R.string.common_collapse else R.string.common_expand)
    val pickLabel = stringResource(if (listMode) R.string.copilot_search_add else R.string.copilot_search_pick)
    val removeLabel = stringResource(R.string.common_delete)
    val addedLabel = stringResource(R.string.copilot_search_added)
    val stageLabel = item.stageLabel.asString()
    val matchLabel = match?.let { stringResource(it.mode.labelRes) }.orEmpty()
    val currentOnPick by rememberUpdatedState(onPick)
    val currentOnRemove by rememberUpdatedState(onRemove)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = toggleLabel) { expanded = !expanded }
                .clearAndSetSemantics {
                    contentDescription = "$stageLabel ${item.title} $matchLabel".trim()
                    if (added) stateDescription = addedLabel
                    onClick(label = toggleLabel) { expanded = !expanded; true }
                    customActions = listOf(
                        if (added) CustomAccessibilityAction(removeLabel) { currentOnRemove(); true }
                        else CustomAccessibilityAction(pickLabel) { currentOnPick(); true }
                    )
                }
                .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            text = stageLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    if (item.title.isNotBlank()) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // 有标签时占满，把标签推到行尾
                            modifier = Modifier.weight(1f, fill = match != null)
                        )
                    }
                    if (match != null) {
                        if (item.title.isBlank()) Spacer(modifier = Modifier.weight(1f))
                        MatchBadge(match.mode)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OperatorStrip(item.summary, characters, modifier = Modifier.weight(1f))
                    Text(
                        text = item.uploader,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 72.dp)
                    )
                    StatText(Icons.Default.Visibility, countFormat.format(item.views.toLong()))
                    StatText(Icons.Default.ThumbUp, countFormat.format(item.likes.toLong()))
                }
            }
            when {
                loading -> Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }

                !listMode -> FilledTonalButton(
                    onClick = onPick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(pickLabel, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }

                else -> IconButton(
                    onClick = if (added) onRemove else onPick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (added) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null,
                        tint = if (added) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        MaaAnimatedVisibility(visible = expanded) {
            CopilotRowDetails(item, match, characters)
        }
    }
}

@Composable
private fun CopilotRowDetails(
    item: CopilotSearchItem,
    match: OperatorMatch?,
    characters: Map<String, CharacterInfo?>,
) {
    Column(
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (match != null) {
            if (match.missing.isNotEmpty()) {
                MatchGap(stringResource(R.string.copilot_match_missing, match.missing.joinToString(" / ")))
            }
            if (match.training.isNotEmpty()) {
                MatchGap(stringResource(R.string.copilot_match_training, match.training.joinToString(" / ")))
            }
        }
        if (item.details.isNotBlank()) CopilotDetails(item.details)
        if (item.summary.operators.isNotEmpty()) {
            OperatorGrid { OperatorCells(item.summary.operators, characters) }
        }
        item.summary.groups.forEach { (name, opers) ->
            OperatorGroup(name, opers, characters)
        }
    }
}

@Composable
private fun MatchGap(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

/** 一行放不下就截断，末尾补 +N */
@Composable
internal fun OperatorStrip(
    summary: OperatorSummaryData,
    characters: Map<String, CharacterInfo?>,
    modifier: Modifier = Modifier,
) {
    val total = summary.operators.size + summary.groups.size
    if (total == 0) {
        Spacer(modifier = modifier)
        return
    }
    SubcomposeLayout(modifier = modifier) { constraints ->
        val gap = 2.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        // 放不下的头像不组合，省掉解码和逐个收集
        val fit = constraints.maxWidth / (20.dp.roundToPx() + gap) + 1
        val cells = subcompose("cells") {
            summary.operators.take(fit).forEach { oper ->
                OperAvatar(operId = characters[oper.name]?.id.orEmpty(), modifier = Modifier.size(20.dp))
            }
            if (summary.operators.size <= fit) summary.groups.forEach { (name, _) -> GroupChip(name) }
        }.map { it.measure(loose) }

        fun widthOf(n: Int) = cells.take(n).sumOf { it.width } + gap * (n - 1).coerceAtLeast(0)

        var shown = cells.size
        val more = if (cells.size == total && widthOf(total) <= constraints.maxWidth) null else {
            // 先按最长的 +N 预留位置
            val reserve = subcompose("reserve") { MoreCount(total) }.first().measure(loose).width + gap
            shown = 0
            while (shown < cells.size && widthOf(shown + 1) + reserve <= constraints.maxWidth) shown++
            subcompose("more") { MoreCount(total - shown) }.first().measure(loose)
        }
        val height = maxOf(cells.maxOf { it.height }, more?.height ?: 0)
        val used = widthOf(shown) + (more?.let { it.width + gap } ?: 0)
        layout(used.coerceIn(constraints.minWidth, constraints.maxWidth), height) {
            var x = 0
            cells.take(shown).forEach {
                it.place(x, (height - it.height) / 2)
                x += it.width + gap
            }
            more?.place(x, (height - more.height) / 2)
        }
    }
}

internal fun ResourceDataManager.charactersOf(summary: OperatorSummaryData): Map<String, CharacterInfo?> =
    (summary.operators + summary.groups.flatMap { it.second })
        .associate { it.name to getCharacterByNameOrAlias(it.name) }

@Composable
internal fun CountBadge(count: Int) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun GroupChip(name: String) {
    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surface) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 64.dp).padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MoreCount(count: Int) {
    Text(
        text = "+$count",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun StatText(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
