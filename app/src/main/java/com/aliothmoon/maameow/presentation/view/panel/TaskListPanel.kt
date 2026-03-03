package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.TaskParamProvider
import com.aliothmoon.maameow.data.model.TaskTypeInfo
import sh.calvin.reorderable.ReorderableColumn

/**
 * 左侧任务列表（支持拖拽排序、勾选、编辑弹窗管理任务）
 */
@Composable
fun TaskListPanel(
    nodes: List<TaskChainNode>,
    selectedNodeId: String?,
    onNodeEnabledChange: (String, Boolean) -> Unit,
    onNodeSelected: (String) -> Unit,
    onNodeMove: (Int, Int) -> Unit,
    onAddNode: (TaskTypeInfo) -> Unit,
    onRemoveNode: (String) -> Unit,
    onRenameNode: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.width(IntrinsicSize.Max)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .clickable { showEditDialog = true },
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "编辑任务",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        ReorderableColumn(
            list = nodes,
            onSettle = { fromIndex, toIndex -> onNodeMove(fromIndex, toIndex) },
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) { _, node, _ ->
            key(node.id) {
                ReorderableItem {
                    TaskNodeRow(
                        node = node,
                        isSelected = selectedNodeId == node.id,
                        onEnabledChange = { enabled -> onNodeEnabledChange(node.id, enabled) },
                        onSelected = { onNodeSelected(node.id) },
                        modifier = Modifier.longPressDraggableHandle()
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        TaskEditDialog(
            nodes = nodes,
            onAddNode = onAddNode,
            onRemoveNode = onRemoveNode,
            onRenameNode = onRenameNode,
            onDismiss = { showEditDialog = false }
        )
    }
}

@Composable
private fun TaskNodeRow(
    node: TaskChainNode,
    isSelected: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xfff2f3f5) else Color.White
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelected() }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = node.enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun getTypeDisplayName(config: TaskParamProvider): String {
    return TaskTypeInfo.entries
        .first { it.defaultConfig()::class == config::class }
        .displayName
}

@Composable
private fun TaskEditDialog(
    nodes: List<TaskChainNode>,
    onAddNode: (TaskTypeInfo) -> Unit,
    onRemoveNode: (String) -> Unit,
    onRenameNode: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var addExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    "编辑任务列表",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF1f2937)
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFe5e7eb))
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    nodes.forEach { node ->
                        key(node.id) {
                            TaskEditRow(
                                node = node,
                                onRename = { newName -> onRenameNode(node.id, newName) },
                                onRemove = { onRemoveNode(node.id) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFe5e7eb))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { addExpanded = !addExpanded }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (addExpanded)
                            Icons.Default.KeyboardArrowUp
                        else
                            Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF6b7280)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "添加任务",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF374151)
                    )
                }

                AnimatedVisibility(visible = addExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TaskTypeInfo.entries.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { typeInfo ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFf9fafb),
                                        border = BorderStroke(1.dp, Color(0xFFe5e7eb)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                onAddNode(typeInfo)
                                                addExpanded = false
                                            }
                                    ) {
                                        Text(
                                            text = typeInfo.displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF374151),
                                            modifier = Modifier.padding(
                                                horizontal = 12.dp,
                                                vertical = 10.dp
                                            )
                                        )
                                    }
                                }
                                if (row.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("完成")
                }
            }
        }
    }
}

@Composable
private fun TaskEditRow(
    node: TaskChainNode,
    onRename: (String) -> Unit,
    onRemove: () -> Unit
) {
    val nameState = remember(node.id) { TextFieldState(node.name) }

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = getTypeDisplayName(node.config),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF6b7280),
            modifier = Modifier.width(72.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        OutlinedTextField(
            state = nameState,
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = MaterialTheme.typography.bodyMedium,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color(0xFFd1d5db),
                focusedBorderColor = Color(0xFF3b82f6)
            ),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    val current = nameState.text.toString().trim()
                    if (!focusState.isFocused
                        && current.isNotBlank()
                        && current != node.name
                    ) {
                        onRename(current)
                    }
                }
        )

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "删除",
                modifier = Modifier.size(16.dp),
                tint = Color(0xFFef4444)
            )
        }
    }
}
 