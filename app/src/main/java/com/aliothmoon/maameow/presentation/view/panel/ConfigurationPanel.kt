package com.aliothmoon.maameow.presentation.view.panel

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.AwardConfig
import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.data.model.InfrastConfig
import com.aliothmoon.maameow.data.model.MallConfig
import com.aliothmoon.maameow.data.model.ReclamationConfig
import com.aliothmoon.maameow.data.model.RecruitConfig
import com.aliothmoon.maameow.data.model.RoguelikeConfig
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.TaskParamProvider
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.model.TaskTypeInfo
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.presentation.view.panel.fight.FightConfigPanel
import com.aliothmoon.maameow.presentation.view.panel.mall.MallConfigPanel
import com.aliothmoon.maameow.presentation.view.panel.roguelike.RoguelikeConfigPanel

@Composable
fun TaskConfigPanel(
    selectedNode: TaskChainNode?,
    isEditMode: Boolean,
    isAddingTask: Boolean,
    isProfileMode: Boolean,
    profiles: List<TaskProfile>,
    activeProfileId: String,
    onConfigChange: (TaskParamProvider) -> Unit,
    onAddNode: (TaskTypeInfo) -> Unit,
    onRemoveNode: (String) -> Unit,
    onRenameNode: (String, String) -> Unit,
    onSwitchProfile: (String) -> Unit,
    onRenameProfile: (String, String) -> Unit,
    onDuplicateProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onCreateProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when {
            // 配置管理模式
            isProfileMode -> {
                ProfileManagementPanel(
                    profiles = profiles,
                    activeProfileId = activeProfileId,
                    onSwitch = onSwitchProfile,
                    onRename = onRenameProfile,
                    onDuplicate = onDuplicateProfile,
                    onDelete = onDeleteProfile,
                    onCreate = onCreateProfile
                )
            }
            // 编辑模式：正在新增任务
            isEditMode && isAddingTask -> {
                TaskGalleryView(onAddNode = onAddNode)
            }

            // 编辑模式：已选中任务
            isEditMode && selectedNode != null -> {
                TaskManagementView(
                    node = selectedNode,
                    onRename = { onRenameNode(selectedNode.id, it) },
                    onRemove = { onRemoveNode(selectedNode.id) }
                )
            }

            // 编辑模式：未选中
            isEditMode -> {
                EmptyStateHint(
                    title = stringResource(R.string.edit_mode_title),
                    descriptions = listOf(
                        stringResource(R.string.edit_mode_hint_1),
                        stringResource(R.string.edit_mode_hint_2)
                    )
                )
            }

            // 普通模式：已选中任务
            !isEditMode && selectedNode != null -> {
                val cfg = selectedNode.config
                Box(modifier = Modifier.fillMaxSize()) {
                    when (cfg) {
                        is WakeUpConfig -> WakeUpConfigPanel(
                            config = cfg,
                            onConfigChange = onConfigChange
                        )

                        is RecruitConfig -> RecruitConfigPanel(
                            config = cfg,
                            onConfigChange = onConfigChange
                        )

                        is InfrastConfig -> InfrastConfigPanel(
                            config = cfg,
                            onConfigChange = onConfigChange
                        )

                        is FightConfig -> FightConfigPanel(
                            config = cfg,
                            onConfigChange = onConfigChange
                        )

                        is MallConfig -> MallConfigPanel(
                            config = cfg,
                            onConfigChange = onConfigChange
                        )

                        is AwardConfig -> AwardConfigPanel(
                            config = cfg,
                            onConfigChange = onConfigChange
                        )

                        is RoguelikeConfig -> RoguelikeConfigPanel(
                            config = cfg,
                            onConfigChange = onConfigChange
                        )

                        is ReclamationConfig -> ReclamationConfigPanel(
                            config = cfg,
                            onConfigChange = onConfigChange
                        )
                    }
                }
            }

            // 普通模式：未选中
            else -> {
                EmptyStateHint(
                    title = stringResource(R.string.configure_task_title),
                    descriptions = listOf(stringResource(R.string.configure_task_hint))
                )
            }
        }
    }
}

@Composable
private fun EmptyStateHint(
    title: String,
    descriptions: List<String>,
    showReorderHint: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp)) // 固定顶部高度，防止切换标题时跳变

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        descriptions.forEach { desc ->
            HintItem(Icons.Default.Info, desc)
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (showReorderHint) {
            HintItem(Icons.Default.Info, stringResource(R.string.task_reorder_hint))
        }
    }
}

@Composable
private fun HintItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun TaskGalleryView(onAddNode: (TaskTypeInfo) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text(
            stringResource(R.string.select_task_type),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(TaskTypeInfo.entries) { typeInfo ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.clickable { onAddNode(typeInfo) }
                ) {
                    Box(
                        modifier = Modifier.padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = typeInfo.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskManagementView(
    node: TaskChainNode,
    onRename: (String) -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    var text by remember(node.id) { mutableStateOf(node.name) }
    val typeDisplayName = remember(node.config, context) { getTypeDisplayName(node.config, context) }

    val trimmedText = text.trim()
    val isError = trimmedText.isEmpty()
    val isTooLong = text.length > 20

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.editing),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = typeDisplayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        ITextField(
            value = text,
            onValueChange = { newText ->
                text = newText
                val name = newText.trim()
                if (name.isNotEmpty() && name.length <= 20 && name != node.name) {
                    onRename(name)
                }
            },
            label = stringResource(R.string.task_name),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                if (isError) {
                    Text(stringResource(R.string.name_cannot_be_empty))
                } else if (isTooLong) {
                    Text(stringResource(R.string.name_too_long))
                }
            },
            outlineColor = if (isError || isTooLong) MaterialTheme.colorScheme.error else null
        )

        // 字数统计在 ITextField 下方展示，因为 ITextField 内部目前可能不支持 trailingIcon（取决于具体实现）
        Text(
            text = "${text.length}/20",
            style = MaterialTheme.typography.labelSmall,
            color = if (isTooLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            textAlign = TextAlign.End
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onRemove,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(4.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.delete_task))
        }
    }
}

private fun getTypeDisplayName(config: TaskParamProvider, context: Context): String {
    return TaskTypeInfo.entries
        .firstOrNull { it.defaultConfig()::class == config::class }
        ?.displayName ?: context.getString(R.string.unknown_task)
}
