package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.ProfileSequenceEntry
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.model.TaskSequenceConfig
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.presentation.components.AdaptiveTaskPromptDialog
import com.aliothmoon.maameow.presentation.components.ITextField
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem

/**
 * 任务链面板：
 * - 顶部切换的是「任务链配置」（多套命名序列），不是用户 Profile
 * - 开关控制手动开始是否按任务链跑
 * - 列表/弹窗共用同一 [sequence] 数据源，实时同步
 * - 弹窗右侧与主列表均用 [ReorderableColumn] + onSettle，避免异步 onMove 弹回
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSequencePanel(
    profiles: List<TaskProfile>,
    sequenceConfigs: List<TaskSequenceConfig>,
    activeSequenceConfigId: String,
    sequence: List<ProfileSequenceEntry>,
    sequenceEnabled: Boolean,
    onSwitchSequenceConfig: (String) -> Unit,
    onCreateSequenceConfig: () -> Unit,
    onRenameSequenceConfig: (String, String) -> Unit,
    onDeleteSequenceConfig: (String) -> Unit,
    onSequenceEnabledChange: (Boolean) -> Unit,
    onAddProfiles: (profileIds: List<String>) -> Unit,
    onRemove: (entryId: String) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    onDraggingChanged: (Boolean) -> Unit = {},
) {
    var showPicker by remember { mutableStateOf(false) }
    var configMenuExpanded by remember { mutableStateOf(false) }
    var renameTargetId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    val profileMap = remember(profiles) { profiles.associateBy { it.id } }
    val activeConfig = sequenceConfigs.find { it.id == activeSequenceConfigId }
        ?: sequenceConfigs.firstOrNull()
    val activeConfigName = activeConfig?.name.orEmpty()
    val canDeleteConfig = sequenceConfigs.size > 1

    DisposableEffect(Unit) {
        onDispose { onDraggingChanged(false) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.panel_sequence_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = stringResource(R.string.panel_sequence_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 任务链配置（非用户配置）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExposedDropdownMenuBox(
                expanded = configMenuExpanded,
                onExpandedChange = { configMenuExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = activeConfigName,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text(stringResource(R.string.panel_sequence_config)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = configMenuExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        errorBorderColor = Color.Transparent,
                    ),
                    shape = RoundedCornerShape(4.dp),
                )
                ExposedDropdownMenu(
                    expanded = configMenuExpanded,
                    onDismissRequest = { configMenuExpanded = false },
                    shape = RoundedCornerShape(4.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 6.dp,
                ) {
                    sequenceConfigs.forEach { config ->
                        val selected = config.id == activeSequenceConfigId
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = config.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            onClick = {
                                onSwitchSequenceConfig(config.id)
                                configMenuExpanded = false
                            },
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerLowest
                                ),
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.panel_sequence_config_new))
                        },
                        onClick = {
                            onCreateSequenceConfig()
                            configMenuExpanded = false
                        },
                        enabled = true
                    )
                }
            }

            if (activeConfig != null) {
                IconButton(
                    onClick = {
                        renameTargetId = activeConfig.id
                        renameText = activeConfig.name
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.panel_sequence_config_rename),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { deleteTargetId = activeConfig.id },
                    enabled = canDeleteConfig,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.panel_sequence_config_delete),
                        modifier = Modifier.size(18.dp),
                        tint = if (canDeleteConfig) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.panel_sequence_enable),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = sequenceEnabled,
                onCheckedChange = onSequenceEnabledChange
            )
        }

        if (!sequenceEnabled) {
            Text(
                text = stringResource(R.string.panel_sequence_disabled_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (sequence.isEmpty()) {
            Text(
                text = stringResource(R.string.panel_sequence_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp)
            )
        } else {
            ReorderableColumn(
                list = sequence,
                onSettle = { fromIndex, toIndex ->
                    if (fromIndex != toIndex) onReorder(fromIndex, toIndex)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { index, entry, isDragging ->
                key(entry.id) {
                    ReorderableItem {
                        val name = profileMap[entry.profileId]?.name
                            ?: stringResource(R.string.panel_sequence_missing_profile)
                        SequenceEntryCard(
                            index = index + 1,
                            name = name,
                            isDragging = isDragging,
                            modifier = Modifier.longPressDraggableHandle(
                                onDragStarted = { onDraggingChanged(true) },
                                onDragStopped = { onDraggingChanged(false) }
                            ),
                            onRemove = { onRemove(entry.id) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        // 满 20 仍可打开弹窗（可删/重排），仅无用户配置时禁用
        OutlinedButton(
            onClick = { showPicker = true },
            enabled = profiles.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Text(
                text = stringResource(R.string.panel_sequence_add),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    if (showPicker) {
        ProfileSequencePickerDialog(
            profiles = profiles,
            sequence = sequence,
            profileMap = profileMap,
            onAddProfile = { profileId -> onAddProfiles(listOf(profileId)) },
            onRemove = onRemove,
            onReorder = onReorder,
            onDismiss = { showPicker = false },
            onDraggingChanged = onDraggingChanged,
        )
    }

    // 重命名当前任务链配置
    if (renameTargetId != null) {
        Dialog(onDismissRequest = { renameTargetId = null }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 420.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.panel_sequence_config_rename_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ITextField(
                        value = renameText,
                        onValueChange = {
                            if (it.length <= TaskChainState.MAX_SEQUENCE_NAME_LENGTH) {
                                renameText = it
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { renameTargetId = null }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        TextButton(
                            onClick = {
                                val id = renameTargetId
                                val name = renameText.trim()
                                if (id != null && name.isNotEmpty()) {
                                    onRenameSequenceConfig(id, name)
                                }
                                renameTargetId = null
                            },
                            enabled = renameText.trim().isNotEmpty()
                        ) {
                            Text(stringResource(R.string.common_confirm))
                        }
                    }
                }
            }
        }
    }

    val deleteName = deleteTargetId?.let { id ->
        sequenceConfigs.find { it.id == id }?.name
    }.orEmpty()
    AdaptiveTaskPromptDialog(
        visible = deleteTargetId != null,
        title = stringResource(R.string.panel_sequence_config_delete_title),
        message = stringResource(R.string.panel_sequence_config_delete_message, deleteName),
        icon = Icons.Default.Warning,
        confirmColor = MaterialTheme.colorScheme.error,
        confirmText = stringResource(R.string.common_delete),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = {
            deleteTargetId?.let { onDeleteSequenceConfig(it) }
            deleteTargetId = null
        },
        onDismissRequest = { deleteTargetId = null }
    )
}

@Composable
private fun SequenceEntryCard(
    index: Int,
    name: String,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    onRemove: () -> Unit,
    compact: Boolean = false,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 4.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 4.dp)
                .height(if (compact) 28.dp else 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$index.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(if (compact) 22.dp else 28.dp)
            )
            Text(
                text = name,
                style = if (compact) {
                    MaterialTheme.typography.bodySmall
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(if (compact) 24.dp else 28.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * 中间弹窗：直接编辑当前任务链（与一级列表同一数据源）。
 * 右侧使用 [ReorderableColumn] + onSettle，与主列表一致。
 */
@Composable
fun ProfileSequencePickerDialog(
    profiles: List<TaskProfile>,
    sequence: List<ProfileSequenceEntry>,
    profileMap: Map<String, TaskProfile>,
    onAddProfile: (String) -> Unit,
    onRemove: (entryId: String) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onDismiss: () -> Unit,
    onDraggingChanged: (Boolean) -> Unit = {},
) {
    val canAddMore = sequence.size < TaskChainState.MAX_SEQUENCE_ENTRIES

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .widthIn(max = 560.dp)
                .padding(horizontal = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.panel_sequence_pick_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(
                        R.string.panel_sequence_pick_hint,
                        (TaskChainState.MAX_SEQUENCE_ENTRIES - sequence.size).coerceAtLeast(0)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 360.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = stringResource(R.string.panel_sequence_pick_source),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(profiles, key = { it.id }) { profile ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (canAddMore) {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = canAddMore) {
                                            onAddProfile(profile.id)
                                        }
                                ) {
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (canAddMore) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                        },
                                        modifier = Modifier.padding(
                                            horizontal = 10.dp,
                                            vertical = 10.dp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    VerticalDivider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 10.dp)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = stringResource(R.string.panel_sequence_pick_draft),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        if (sequence.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.panel_sequence_pick_draft_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            // 与主列表一致：onSettle 再写回，避免 Lazy onMove + 异步 State 弹回
                            ReorderableColumn(
                                list = sequence,
                                onSettle = { fromIndex, toIndex ->
                                    if (fromIndex != toIndex) onReorder(fromIndex, toIndex)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) { index, item, isDragging ->
                                key(item.id) {
                                    ReorderableItem {
                                        val name = profileMap[item.profileId]?.name
                                            ?: stringResource(R.string.panel_sequence_missing_profile)
                                        SequenceEntryCard(
                                            index = index + 1,
                                            name = name,
                                            isDragging = isDragging,
                                            compact = true,
                                            modifier = Modifier.longPressDraggableHandle(
                                                onDragStarted = { onDraggingChanged(true) },
                                                onDragStopped = { onDraggingChanged(false) }
                                            ),
                                            onRemove = { onRemove(item.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.panel_sequence_pick_done))
                    }
                }
            }
        }
    }
}
