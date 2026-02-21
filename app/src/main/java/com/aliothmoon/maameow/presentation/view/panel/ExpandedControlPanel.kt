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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.presentation.components.OverlayDialog
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
    service: MaaCompositionService = koinInject()
) {
    val uiState by viewModel.state.collectAsState()
    val maaState by service.state.collectAsState()
    val logs by viewModel.runtimeLogs.collectAsState()

    val tasks by viewModel.taskConfig.taskList.collectAsState()

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
                                    tasks = tasks,
                                    selectedTaskType = uiState.currentTaskType,
                                    onTaskEnabledChange = viewModel::onTaskEnableChange,
                                    onTaskSelected = viewModel::onSelectedTaskChange,
                                    onTaskMove = viewModel::onTaskMove,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                )

                                // 右侧配置区域
                                ConfigurationPanel(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    state = uiState,
                                    taskConfig = viewModel.taskConfig,
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
