package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.presentation.LocalFloatingWindowContext
import com.aliothmoon.maameow.presentation.components.AdaptiveTaskPromptDialog
import com.aliothmoon.maameow.presentation.components.ResourceLoadingOverlay
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogType.ERROR
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogType.SUCCESS
import com.aliothmoon.maameow.presentation.viewmodel.CopilotViewModel
import com.aliothmoon.maameow.presentation.viewmodel.ExpandedControlPanelViewModel
import com.aliothmoon.maameow.presentation.viewmodel.ToolboxViewModel
import com.aliothmoon.maameow.utils.i18n.asString
import org.koin.compose.koinInject


@Composable
fun ExpandedControlPanel(
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    onHome: () -> Unit = {},
    isLocked: Boolean = false,
    onLockToggle: (Boolean) -> Unit = {},
    viewModel: ExpandedControlPanelViewModel = viewModel(),
    copilotViewModel: CopilotViewModel = viewModel(),
    toolboxViewModel: ToolboxViewModel = koinInject(),
    service: MaaCompositionService = koinInject(),
    appSettings: AppSettingsManager = koinInject()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val maaState by service.state.collectAsStateWithLifecycle()
    val runMode by appSettings.runMode.collectAsStateWithLifecycle()

    val nodes by viewModel.chainState.chain.collectAsStateWithLifecycle()
    val profiles by viewModel.chainState.profiles.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.chainState.activeProfileId.collectAsStateWithLifecycle()
    val selectedNode = nodes.find { it.id == uiState.selectedNodeId }
    val focusManager = LocalFocusManager.current

    val pagerState = rememberPagerState(
        initialPage = uiState.currentTab.ordinal,
        pageCount = { PanelTab.entries.size }
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val newTab = PanelTab.entries[page]
            if (newTab != uiState.currentTab) {
                viewModel.onTabChange(newTab)
            }
        }
    }

    LaunchedEffect(uiState.currentTab) {
        if (pagerState.currentPage != uiState.currentTab.ordinal) {
            pagerState.scrollToPage(uiState.currentTab.ordinal)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(4.dp)
                ),
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // 标题栏
                PanelHeader(
                    selectedTab = uiState.currentTab,
                    onTabSelected = viewModel::onTabChange,
                    isLocked = isLocked,
                    onLockToggle = onLockToggle,
                    onHome = onHome
                )

                // 中间内容区域 - 使用 HorizontalPager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    userScrollEnabled = false,
                    beyondViewportPageCount = 1
                ) { page ->
                    when (page) {
                        0 -> { // PanelTab.ONE_KEY_TASKS
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                            ) {
                                // 左侧任务列表
                                TaskListPanel(
                                    nodes = nodes,
                                    selectedNodeId = uiState.selectedNodeId,
                                    isEditMode = uiState.isEditMode,
                                    isAddingTask = uiState.isAddingTask,
                                    isProfileMode = uiState.isProfileMode,
                                    onNodeEnabledChange = viewModel::onNodeEnabledChange,
                                    onNodeSelected = viewModel::onNodeSelected,
                                    onNodeMove = viewModel::onNodeMove,
                                    onToggleEditMode = viewModel::onToggleEditMode,
                                    onToggleAddingTask = viewModel::onToggleAddingTask,
                                    onToggleProfileMode = viewModel::onToggleProfileMode,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                )

                                // 右侧配置区域
                                TaskConfigPanel(
                                    selectedNode = selectedNode,
                                    isEditMode = uiState.isEditMode,
                                    isAddingTask = uiState.isAddingTask,
                                    isProfileMode = uiState.isProfileMode,
                                    profiles = profiles,
                                    activeProfileId = activeProfileId,
                                    onConfigChange = { config ->
                                        val nodeId = selectedNode?.id ?: return@TaskConfigPanel
                                        viewModel.onNodeConfigChange(nodeId, config)
                                    },
                                    onAddNode = viewModel::onAddNode,
                                    onRemoveNode = viewModel::onRemoveNode,
                                    onRenameNode = viewModel::onRenameNode,
                                    onSwitchProfile = viewModel::onSwitchProfile,
                                    onRenameProfile = viewModel::onRenameProfile,
                                    onDuplicateProfile = viewModel::onDuplicateProfile,
                                    onDeleteProfile = viewModel::onDeleteProfile,
                                    onCreateProfile = viewModel::onCreateProfile,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }

                        1 -> { // PanelTab.AUTO_BATTLE
                            AutoBattlePanel(modifier = Modifier.fillMaxSize())
                        }

                        2 -> { // PanelTab.TOOLS
                            ToolboxPanel(modifier = Modifier.fillMaxSize())
                        }

                        3 -> { // PanelTab.LOG
                            val runtimeLogs by viewModel.runtimeLogs.collectAsStateWithLifecycle()
                            LogPanel(
                                logs = runtimeLogs,
                                onClearLogs = { viewModel.onClearLogs() },
                                onClose = { viewModel.onTabChange(PanelTab.TASKS) }
                            )
                        }
                    }
                }

                if (uiState.currentTab == PanelTab.TASKS || uiState.currentTab == PanelTab.AUTO_BATTLE || uiState.currentTab == PanelTab.TOOLS) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    BottomButtons(
                        onClose = { onClose() },
                        onStart = {
                            focusManager.clearFocus()
                            when (uiState.currentTab) {
                                PanelTab.AUTO_BATTLE -> copilotViewModel.onStart()
                                PanelTab.TOOLS -> toolboxViewModel.onStart()
                                else -> viewModel.onStartTasks()
                            }
                        },
                        isStarting = maaState == MaaExecutionState.STARTING
                    )
                }
            }
        }

        if (LocalFloatingWindowContext.current && runMode == RunMode.FOREGROUND) {
            ResourceLoadingOverlay()
        }

        val dialog = uiState.dialog
        val dialogTitle = dialog?.title.asString()
        val dialogMessage = dialog?.message.asString()
        val dialogConfirmText = dialog?.confirmText.asString()
        val dialogDismissText = dialog?.dismissText.asString()
        val confirmColor = when (dialog?.type) {
            SUCCESS -> MaterialTheme.colorScheme.primary
            ERROR -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.tertiary
        }
        AdaptiveTaskPromptDialog(
            visible = dialog != null,
            onDismissRequest = viewModel::onDialogDismiss,
            title = dialogTitle,
            message = AnnotatedString(dialogMessage),
            icon = when (dialog?.type) {
                SUCCESS -> Icons.Filled.CheckCircle
                else -> Icons.Filled.Warning
            },
            iconTint = confirmColor,
            confirmColor = confirmColor,
            confirmText = dialogConfirmText.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.common_confirm),
            dismissText = dialogDismissText.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.common_close),
            onConfirm = viewModel::onDialogConfirm,
        )
    }
}
