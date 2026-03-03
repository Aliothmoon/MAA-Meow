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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.presentation.LocalFloatingWindowContext
import com.aliothmoon.maameow.presentation.components.OverlayDialog
import com.aliothmoon.maameow.presentation.components.ResourceLoadingOverlay
import com.aliothmoon.maameow.presentation.components.PlaceholderContent
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogType.ERROR
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogType.SUCCESS
import com.aliothmoon.maameow.presentation.viewmodel.ExpandedControlPanelViewModel
import org.koin.compose.koinInject


@Composable
fun ExpandedControlPanel(
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    onHome: () -> Unit = {},
    isLocked: Boolean = false,
    onLockToggle: (Boolean) -> Unit = {},
    viewModel: ExpandedControlPanelViewModel = viewModel(),
    service: MaaCompositionService = koinInject(),
    appSettings: AppSettingsManager = koinInject()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val maaState by service.state.collectAsStateWithLifecycle()
    val runMode by appSettings.runMode.collectAsStateWithLifecycle()
    val logs by viewModel.runtimeLogs.collectAsStateWithLifecycle()

    val nodes by viewModel.chainState.chain.collectAsStateWithLifecycle()
    val selectedNode = nodes.find { it.id == uiState.selectedNodeId }

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
                containerColor = Color.White
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
                    beyondViewportPageCount = PanelTab.entries.size - 1
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
                                    onNodeEnabledChange = viewModel::onNodeEnabledChange,
                                    onNodeSelected = viewModel::onNodeSelected,
                                    onNodeMove = viewModel::onNodeMove,
                                    onAddNode = viewModel::onAddNode,
                                    onRemoveNode = viewModel::onRemoveNode,
                                    onRenameNode = viewModel::onRenameNode,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                )

                                // 右侧配置区域
                                TaskConfigPanel(
                                    selectedNode = selectedNode,
                                    onConfigChange = { config ->
                                        val nodeId = uiState.selectedNodeId ?: return@TaskConfigPanel
                                        viewModel.onNodeConfigChange(nodeId, config)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }

                        1 -> { // PanelTab.AUTO_BATTLE
                            PlaceholderContent(
                                title = "自动战斗",
                                description = "功能开发中...",
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        2 -> { // PanelTab.TOOLS
                            PlaceholderContent(
                                title = "小工具",
                                description = "功能开发中...",
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        3 -> { // PanelTab.LOG
                            LogPanel(
                                logs = logs,
                                onClearLogs = viewModel::onClearLogs,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    thickness = 1.dp,
                    color = Color.LightGray
                )
                // 底部按钮
                BottomButtons(
                    onClose = { onClose() },
                    onStart = {
                        viewModel.onStartTasks()
                    },
                    isStarting = maaState == MaaExecutionState.STARTING
                )
            }
        }

        if (LocalFloatingWindowContext.current && runMode == RunMode.FOREGROUND) {
            ResourceLoadingOverlay()
        }

        val dialog = uiState.dialog
        OverlayDialog(
            visible = dialog != null,
            onDismissRequest = viewModel::onDialogDismiss,
            title = dialog?.title ?: "",
            message = dialog?.message ?: "",
            icon = when (dialog?.type) {
                SUCCESS -> Icons.Filled.CheckCircle
                else -> Icons.Filled.Warning
            },
            iconTint = when (dialog?.type) {
                SUCCESS -> Color(0xFF4CAF50)
                ERROR -> Color(0xFFE53935)
                else -> Color(0xFFFFA000)
            },
            confirmText = dialog?.confirmText ?: "确认",
            dismissText = dialog?.dismissText ?: "关闭",
            onConfirm = {
                viewModel.onDialogConfirm()
            }
        )
    }
}
