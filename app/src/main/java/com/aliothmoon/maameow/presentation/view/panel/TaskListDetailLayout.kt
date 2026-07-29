package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.TaskParamProvider
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.model.TaskTypeInfo


@Composable
fun TaskListDetailLayout(
    nodes: List<TaskChainNode>,
    selectedNode: TaskChainNode?,
    selectedNodeId: String?,
    isEditMode: Boolean,
    isAddingTask: Boolean,
    isProfileMode: Boolean,
    profiles: List<TaskProfile>,
    activeProfileId: String,
    clientType: String,
    onNodeEnabledChange: (String, Boolean) -> Unit,
    onNodeSelected: (String) -> Unit,
    onNodeMove: (Int, Int) -> Unit,
    onToggleEditMode: () -> Unit,
    onToggleAddingTask: () -> Unit,
    onToggleProfileMode: () -> Unit,
    onConfigChange: (TaskParamProvider) -> Unit,
    onAddNode: (TaskTypeInfo) -> Unit,
    onRemoveNode: (String) -> Unit,
    onDuplicateNode: (String) -> Unit,
    onRenameNode: (String, String) -> Unit,
    onSwitchProfile: (String) -> Unit,
    onRenameProfile: (String, String) -> Unit,
    onDuplicateProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onCreateProfile: () -> Unit,
    onReorderProfile: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    /** 后台任务页右侧配置区包一层 Card；悬浮窗已有外层 Card 时关闭 */
    wrapDetailInCard: Boolean = false,
) {
    val configuration = LocalConfiguration.current
    // 与 OverlayController.calculatePanelLayout 一致：浮窗约 0.85 屏宽
    val floatMaxWidth = (configuration.screenWidthDp * 0.85f).dp

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val contentModifier = if (constraints.hasBoundedWidth) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .widthIn(max = floatMaxWidth)
                .fillMaxHeight()
                .fillMaxWidth()
        }

        Row(modifier = contentModifier) {
            TaskListPanel(
                nodes = nodes,
                selectedNodeId = selectedNodeId,
                isEditMode = isEditMode,
                isAddingTask = isAddingTask,
                isProfileMode = isProfileMode,
                onNodeEnabledChange = onNodeEnabledChange,
                onNodeSelected = onNodeSelected,
                onNodeMove = onNodeMove,
                onToggleEditMode = onToggleEditMode,
                onToggleAddingTask = onToggleAddingTask,
                onToggleProfileMode = onToggleProfileMode,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(IntrinsicSize.Max),
            )
            Spacer(modifier = Modifier.width(8.dp))
            DetailHost(
                wrapInCard = wrapDetailInCard,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                TaskConfigPanel(
                    selectedNode = selectedNode,
                    isEditMode = isEditMode,
                    isAddingTask = isAddingTask,
                    isProfileMode = isProfileMode,
                    profiles = profiles,
                    activeProfileId = activeProfileId,
                    clientType = clientType,
                    onConfigChange = onConfigChange,
                    onAddNode = onAddNode,
                    onRemoveNode = onRemoveNode,
                    onDuplicateNode = onDuplicateNode,
                    onRenameNode = onRenameNode,
                    onSwitchProfile = onSwitchProfile,
                    onRenameProfile = onRenameProfile,
                    onDuplicateProfile = onDuplicateProfile,
                    onDeleteProfile = onDeleteProfile,
                    onCreateProfile = onCreateProfile,
                    onReorderProfile = onReorderProfile,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun DetailHost(
    wrapInCard: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (wrapInCard) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 10.dp),
            ) {
                content()
            }
        }
    } else {
        Box(modifier = modifier) {
            content()
        }
    }
}
