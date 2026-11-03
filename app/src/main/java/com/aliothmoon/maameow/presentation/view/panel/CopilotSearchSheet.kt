package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.copilot.CopilotListItem
import com.aliothmoon.maameow.data.resource.CharacterInfo
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.presentation.components.OperAvatar
import com.aliothmoon.maameow.presentation.viewmodel.CopilotSearchItem
import com.aliothmoon.maameow.presentation.viewmodel.CopilotTabs
import com.aliothmoon.maameow.presentation.viewmodel.CopilotSearchOrder
import com.aliothmoon.maameow.presentation.viewmodel.CopilotSearchViewModel
import com.aliothmoon.maameow.presentation.viewmodel.CopilotUiState
import com.aliothmoon.maameow.presentation.viewmodel.StageSuggestion
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility
import com.aliothmoon.maameow.utils.Misc
import com.aliothmoon.maameow.utils.i18n.asString
import org.koin.compose.koinInject

/** 盖住整个自动战斗面板；浮窗里没有 Dialog 可用 */
@Composable
internal fun CopilotSearchSheet(
    copilotState: CopilotUiState,
    onPick: (Int) -> Unit,
    onRemovePrts: (Int) -> Unit,
    onRemoveListItem: (String) -> Unit,
    onClose: () -> Unit,
    viewModel: CopilotSearchViewModel = koinInject(),
    resourceDataManager: ResourceDataManager = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nameIndex by resourceDataManager.nameIndex.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listMode = copilotState.listModeActive
    val listed = remember(copilotState.taskList) {
        copilotState.taskList.mapNotNullTo(HashSet()) { it.prtsId }
    }
    // 只在本次搜索里点过添加后才显示状态，免得露出旧消息
    var pendingId by remember { mutableStateOf<Int?>(null) }
    val busy = copilotState.isLoading

    LaunchedEffect(Unit) { viewModel.onOpened() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().padding(TabPanelPadding)) {
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
                IconButton(
                    onClick = { Misc.openUriSafely(context, "https://prts.plus") },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Public,
                        contentDescription = stringResource(R.string.panel_autobattle_station),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (listMode) {
                SelectedStrip(
                    items = copilotState.taskList,
                    showDifficulty = copilotState.tabIndex != CopilotTabs.PARADOX,
                    onRemove = onRemoveListItem,
                )
            } else {
                Text(
                    text = stringResource(R.string.copilot_search_single_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            val status = copilotState.statusMessage.asString()
            val errorText = state.error.asString()
            if (pendingId != null && status.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "keyword") {
                    ITextField(
                        value = state.keyword,
                        onValueChange = viewModel::onKeywordChange,
                        label = stringResource(R.string.copilot_search_keyword),
                        trailingIcon = clearIcon(state.keyword.isNotEmpty()) { viewModel.onKeywordChange("") },
                        onImeAction = viewModel::onSearch,
                    )
                }
                item(key = "stage") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ITextField(
                            value = state.stageText,
                            onValueChange = viewModel::onStageTextChange,
                            label = stringResource(R.string.copilot_search_stage),
                            placeholder = "1-7",
                            trailingIcon = clearIcon(state.stageText.isNotEmpty()) {
                                viewModel.onStageTextChange("")
                            },
                            onImeAction = viewModel::onSearch,
                        )
                        if (state.suggestions.isNotEmpty()) {
                            StageSuggestions(state.suggestions, onPick = viewModel::onPickStage)
                        }
                    }
                }
                item(key = "order") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CopilotSearchOrder.entries.forEach { order ->
                            OrderChip(
                                text = stringResource(order.labelRes),
                                selected = state.order == order,
                                onClick = { viewModel.onOrderChange(order) }
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = viewModel::onSearch,
                            enabled = !state.loading,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                        ) { Text(stringResource(R.string.copilot_search_action)) }
                    }
                }

                if (errorText.isNotBlank()) {
                    item(key = "error") {
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (state.searched && state.items.isNotEmpty()) {
                    item(key = "total") {
                        Text(
                            text = stringResource(R.string.copilot_search_total, state.total),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(state.items, key = { it.id }) { item ->
                    val characters = remember(item.summary, nameIndex) {
                        (item.summary.operators + item.summary.groups.flatMap { it.second })
                            .associate { it.name to resourceDataManager.getCharacterByNameOrAlias(it.name) }
                    }
                    SearchResultCard(
                        item = item,
                        characters = characters,
                        listMode = listMode,
                        added = item.id in listed,
                        enabled = !busy,
                        loading = busy && pendingId == item.id,
                        onPick = {
                            pendingId = item.id
                            onPick(item.id)
                            if (!listMode) onClose()
                        },
                        onRemove = { onRemovePrts(item.id) },
                    )
                }

                item(key = "footer") {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when {
                            state.loading -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )

                            state.hasNext -> TextButton(onClick = viewModel::onLoadMore) {
                                Text(stringResource(R.string.copilot_search_load_more))
                            }

                            state.searched && state.items.isEmpty() && errorText.isBlank() -> Text(
                                text = stringResource(R.string.copilot_search_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun clearIcon(visible: Boolean, onClear: () -> Unit): (@Composable () -> Unit)? =
    if (!visible) null else {
        {
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.common_clear),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

@Composable
private fun SelectedStrip(
    items: List<CopilotListItem>,
    showDifficulty: Boolean,
    onRemove: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(horizontal = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.copilot_search_selected, items.size),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.panel_autobattle_empty_entries),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(items, key = { it.id }) { item ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.alpha(if (item.isChecked) 1f else 0.5f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp, end = 2.dp)
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                        if (showDifficulty) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(
                                    if (item.isRaid) R.string.panel_autobattle_difficulty_raid
                                    else R.string.panel_autobattle_difficulty_normal
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.isRaid) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onRemove(item.id) }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_delete),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
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
private fun OrderChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else null,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun SearchResultCard(
    item: CopilotSearchItem,
    characters: Map<String, CharacterInfo?>,
    listMode: Boolean,
    added: Boolean,
    enabled: Boolean,
    loading: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = item.title.ifBlank { item.stageLabel },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        text = item.stageLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = item.uploader,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                StatText(Icons.Default.Visibility, item.views)
                StatText(Icons.Default.ThumbUp, item.likes)
            }
            if (!item.summary.isEmpty) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item.summary.operators.forEach { oper ->
                        OperAvatar(
                            operId = characters[oper.name]?.id.orEmpty(),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    item.summary.groups.forEach { (name, _) ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.heightIn(min = 28.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.panel_autobattle_group_header, name),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text(stringResource(if (expanded) R.string.common_collapse else R.string.common_expand))
                }
                Spacer(modifier = Modifier.weight(1f))
                val buttonPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                when {
                    loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)

                    listMode && added -> OutlinedButton(
                        onClick = onRemove,
                        enabled = enabled,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = buttonPadding
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.copilot_search_added))
                    }

                    else -> FilledTonalButton(
                        onClick = onPick,
                        enabled = enabled,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = buttonPadding
                    ) {
                        Text(stringResource(if (listMode) R.string.copilot_search_add else R.string.copilot_search_pick))
                    }
                }
            }
            MaaAnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.details.isNotBlank()) CopilotDetails(item.details)
                    if (item.summary.operators.isNotEmpty()) {
                        OperatorGrid { OperatorCells(item.summary.operators, characters) }
                    }
                    item.summary.groups.forEach { (name, opers) ->
                        OperatorGroup(name, opers, characters)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatText(icon: ImageVector, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
