package com.aliothmoon.maameow.presentation.view.panel

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.ProfileSequenceEntry
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.model.TaskSequenceConfig
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.presentation.components.AdaptiveTaskPromptDialog
import com.aliothmoon.maameow.presentation.components.ITextField
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 右侧 Profile 管理面板（page0）+ 右滑进入任务链（page1）
 */
@Composable
fun ProfileManagementPanel(
    profiles: List<TaskProfile>,
    activeProfileId: String,
    sequenceConfigs: List<TaskSequenceConfig>,
    activeSequenceConfigId: String,
    sequence: List<ProfileSequenceEntry>,
    sequenceEnabled: Boolean,
    onSwitch: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCreate: () -> Unit,
    onReorder: (Int, Int) -> Unit,
    onAddProfilesToSequence: (List<String>) -> Unit,
    onRemoveSequenceEntry: (String) -> Unit,
    onReorderSequence: (Int, Int) -> Unit,
    onSwitchSequenceConfig: (String) -> Unit,
    onCreateSequenceConfig: () -> Unit,
    onRenameSequenceConfig: (String, String) -> Unit,
    onDeleteSequenceConfig: (String) -> Unit,
    onSequenceEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    // 任务链拖排序时禁用左右滑，避免 Pager 抢手势
    var isSequenceDragging by remember { mutableStateOf(false) }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        userScrollEnabled = !isSequenceDragging,
    ) { page ->
        when (page) {
            0 -> ProfileListPage(
                profiles = profiles,
                activeProfileId = activeProfileId,
                onSwitch = onSwitch,
                onRename = onRename,
                onDuplicate = onDuplicate,
                onDelete = onDelete,
                onCreate = onCreate,
                onReorder = onReorder,
            )
            else -> ProfileSequencePanel(
                profiles = profiles,
                sequenceConfigs = sequenceConfigs,
                activeSequenceConfigId = activeSequenceConfigId,
                sequence = sequence,
                sequenceEnabled = sequenceEnabled,
                onSwitchSequenceConfig = onSwitchSequenceConfig,
                onCreateSequenceConfig = onCreateSequenceConfig,
                onRenameSequenceConfig = onRenameSequenceConfig,
                onDeleteSequenceConfig = onDeleteSequenceConfig,
                onSequenceEnabledChange = onSequenceEnabledChange,
                onAddProfiles = onAddProfilesToSequence,
                onRemove = onRemoveSequenceEntry,
                onReorder = onReorderSequence,
                onDraggingChanged = { isSequenceDragging = it },
            )
        }
    }
}

@Composable
private fun ProfileListPage(
    profiles: List<TaskProfile>,
    activeProfileId: String,
    onSwitch: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCreate: () -> Unit,
    onReorder: (Int, Int) -> Unit,
) {
    var editingProfileId by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf("") }
    var deleteConfirmProfileId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 顶部标题（EN 长文案单行省略，对齐上游 b75b75c5）
        Text(
            text = stringResource(R.string.panel_profile_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Profile 列表 (支持长按拖动排序)
        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(
            lazyListState = lazyListState,
            onMove = { from, to -> onReorder(from.index, to.index) }
        )
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(profiles, key = { _, item -> item.id }) { _, profile ->
                ReorderableItem(reorderableState, key = profile.id) { isDragging ->
                    val isActive = profile.id == activeProfileId
                    val isEditing = profile.id == editingProfileId

                    ProfileCard(
                        profile = profile,
                        isActive = isActive,
                        isEditing = isEditing,
                        isDragging = isDragging,
                        editingName = if (isEditing) editingName else profile.name,
                        canDelete = profiles.size > 1,
                        modifier = Modifier.longPressDraggableHandle(),
                        onSwitch = { onSwitch(profile.id) },
                        onStartRename = {
                            editingProfileId = profile.id
                            editingName = profile.name
                        },
                        onRenameChange = { editingName = it },
                        onRenameConfirm = {
                            val trimmed = editingName.trim()
                            if (trimmed.isNotEmpty() &&
                                trimmed.length <= TaskChainState.MAX_PROFILE_NAME_LENGTH &&
                                trimmed != profile.name
                            ) {
                                onRename(profile.id, trimmed)
                            }
                            editingProfileId = null
                        },
                        onDuplicate = { onDuplicate(profile.id) },
                        onDelete = { deleteConfirmProfileId = profile.id }
                    )
                }
            }

            // 紧贴配置列表最后一项；与配置卡片同宽同高
            item(key = "new_profile") {
                OutlinedButton(
                    onClick = onCreate,
                    enabled = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.panel_new_profile),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    // 删除确认对话框
    val deleteProfileName = deleteConfirmProfileId?.let { id ->
        profiles.find { it.id == id }?.name
    } ?: ""
    AdaptiveTaskPromptDialog(
        visible = deleteConfirmProfileId != null,
        title = stringResource(R.string.panel_profile_delete_title),
        message = stringResource(R.string.panel_profile_delete_message, deleteProfileName),
        icon = Icons.Default.Warning,
        confirmColor = MaterialTheme.colorScheme.error,
        confirmText = stringResource(R.string.common_delete),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = {
            deleteConfirmProfileId?.let { onDelete(it) }
            deleteConfirmProfileId = null
        },
        onDismissRequest = { deleteConfirmProfileId = null }
    )
}

@Composable
private fun ProfileCard(
    profile: TaskProfile,
    isActive: Boolean,
    isEditing: Boolean,
    isDragging: Boolean,
    editingName: String,
    canDelete: Boolean,
    modifier: Modifier = Modifier,
    onSwitch: () -> Unit,
    onStartRename: () -> Unit,
    onRenameChange: (String) -> Unit,
    onRenameConfirm: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val profileIdCopiedText = stringResource(R.string.panel_profile_id_copied)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSwitch() },
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isActive) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 4.dp else 0.dp
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 主行: RadioButton + 名称 + 操作按钮，长按整行拖动排序
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isActive,
                    onClick = onSwitch,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // 操作按钮
                IconButton(
                    onClick = onStartRename,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.common_rename),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDuplicate,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.common_copy),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDelete,
                    enabled = canDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        modifier = Modifier.size(16.dp),
                        tint = if (canDelete) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        }
                    )
                }
            }

            // 编辑区: 向下展开，包含重命名输入框和 Profile ID 复制
            AnimatedVisibility(
                visible = isEditing,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ITextField(
                            value = editingName,
                            onValueChange = { newText ->
                                if (newText.length <= TaskChainState.MAX_PROFILE_NAME_LENGTH) {
                                    onRenameChange(newText)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(4.dp),
                            onImeAction = onRenameConfirm
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(onClick = onRenameConfirm) {
                            Text(stringResource(R.string.common_confirm), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ID: ${profile.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(profile.id))
                                Toast.makeText(context, profileIdCopiedText, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.panel_profile_copy_id),
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
