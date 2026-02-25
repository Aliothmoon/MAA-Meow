package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.model.TaskItem
import com.aliothmoon.maameow.data.model.TaskType
import sh.calvin.reorderable.ReorderableColumn

/**
 * 左侧任务列表（支持拖拽排序）
 */
@Composable
fun TaskListPanel(
    tasks: List<TaskItem>,
    selectedTaskType: TaskType?,
    onTaskEnabledChange: (TaskType, Boolean) -> Unit,
    onTaskSelected: (TaskType) -> Unit,
    onTaskMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    ReorderableColumn(
        list = tasks,
        onSettle = { fromIndex, toIndex -> onTaskMove(fromIndex, toIndex) },
        modifier = modifier
            .width(IntrinsicSize.Max)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { _, task, _ ->
        key(task.type) {
            ReorderableItem {
                TaskItemRow(
                    task = task,
                    isSelected = selectedTaskType?.id == task.type,
                    onEnabledChange = { enabled ->
                        task.toTaskType()?.let { taskType ->
                            onTaskEnabledChange(taskType, enabled)
                        }
                    },
                    onSelected = {
                        task.toTaskType()?.let { taskType ->
                            onTaskSelected(taskType)
                        }
                    },
                    modifier = Modifier.longPressDraggableHandle()
                )
            }
        }
    }
}

/**
 * 任务项行
 */
@Composable
private fun TaskItemRow(
    task: TaskItem,
    isSelected: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val taskType = task.toTaskType()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                Color(0xfff2f3f5)
            } else Color.White
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
                checked = task.isEnabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = taskType?.displayName ?: task.type,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
