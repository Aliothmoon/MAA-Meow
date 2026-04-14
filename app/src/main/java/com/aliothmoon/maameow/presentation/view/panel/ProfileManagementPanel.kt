package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.presentation.components.AdaptiveTaskPromptDialog
import com.aliothmoon.maameow.presentation.components.ITextField

/**
 * 右侧 Profile 管理面板
 */
@Composable
fun ProfileManagementPanel(
    profiles: List<TaskProfile>,
    activeProfileId: String,
    onSwitch: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingProfileId by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf("") }
    var deleteConfirmProfileId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 顶部标题 + 新建按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.panel_profile_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedButton(
                onClick = onCreate,
                enabled = profiles.size < 10,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.panel_new_profile), color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Profile 列表
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(profiles, key = { it.id }) { profile ->
                val isActive = profile.id == activeProfileId
                val isEditing = profile.id == editingProfileId

                ProfileCard(
                    profile = profile,
                    isActive = isActive,
                    isEditing = isEditing,
                    editingName = if (isEditing) editingName else profile.name,
                    canDelete = profiles.size > 1,
                    onSwitch = { onSwitch(profile.id) },
                    onStartRename = {
                        editingProfileId = profile.id
                        editingName = profile.name
                    },
                    onRenameChange = { editingName = it },
                    onRenameConfirm = {
                        val trimmed = editingName.trim()
                        if (trimmed.isNotEmpty() && trimmed.length <= 20 && trimmed != profile.name) {
                            onRename(profile.id, trimmed)
                        }
                        editingProfileId = null
                    },
                    onDuplicate = { onDuplicate(profile.id) },
                    onDelete = { deleteConfirmProfileId = profile.id }
                )
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
    editingName: String,
    canDelete: Boolean,
    onSwitch: () -> Unit,
    onStartRename: () -> Unit,
    onRenameChange: (String) -> Unit,
    onRenameConfirm: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
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
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 主行: RadioButton + 名称 + 操作按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
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

            // 编辑行: 向下展开的重命名输入框
            AnimatedVisibility(
                visible = isEditing,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ITextField(
                        value = editingName,
                        onValueChange = { newText ->
                            if (newText.length <= 20) onRenameChange(newText)
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
            }
        }
    }
}
