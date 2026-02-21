package com.aliothmoon.maameow.presentation.view.background

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.data.model.TaskType
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.UnifiedStateDispatcher
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.presentation.components.PlaceholderContent
import com.aliothmoon.maameow.presentation.components.ShizukuPermissionDialog
import com.aliothmoon.maameow.presentation.state.BackgroundTaskState
import com.aliothmoon.maameow.presentation.state.MonitorSurfaceSource
import com.aliothmoon.maameow.presentation.view.panel.AwardConfigPanel
import com.aliothmoon.maameow.presentation.view.panel.InfrastConfigPanel
import com.aliothmoon.maameow.presentation.view.panel.LogPanel
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogType
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogUiState
import com.aliothmoon.maameow.presentation.view.panel.PanelTab
import com.aliothmoon.maameow.presentation.view.panel.ReclamationConfigPanel
import com.aliothmoon.maameow.presentation.view.panel.RecruitConfigPanel
import com.aliothmoon.maameow.presentation.view.panel.TaskListPanel
import com.aliothmoon.maameow.presentation.view.panel.WakeUpConfigPanel
import com.aliothmoon.maameow.presentation.view.panel.fight.FightConfigPanel
import com.aliothmoon.maameow.presentation.view.panel.mall.MallConfigPanel
import com.aliothmoon.maameow.presentation.view.panel.roguelike.RoguelikeConfigPanel
import com.aliothmoon.maameow.presentation.viewmodel.BackgroundTaskViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun BackgroundTaskView(
    viewModel: BackgroundTaskViewModel = koinViewModel(),
    compositionService: MaaCompositionService = koinInject(),
    dispatcher: UnifiedStateDispatcher = koinInject(),
    permissionManager: PermissionManager = koinInject()
) {
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val maaState by compositionService.state.collectAsStateWithLifecycle()
    val logs by viewModel.runtimeLogs.collectAsStateWithLifecycle()
    val permissions by permissionManager.state.collectAsStateWithLifecycle()
    var isRequestingShizuku by remember { mutableStateOf(false) }

    val tasks by viewModel.taskConfig.taskList.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(
        initialPage = state.currentTab.ordinal,
        pageCount = { PanelTab.entries.size }
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val newTab = PanelTab.entries[page]
            if (newTab != state.currentTab) {
                viewModel.onTabChange(newTab)
            }
        }
    }

    LaunchedEffect(state.currentTab) {
        if (pagerState.currentPage != state.currentTab.ordinal) {
            pagerState.scrollToPage(state.currentTab.ordinal)
        }
    }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.onPageEnter()
    }
    LaunchedEffect(Unit) {
        dispatcher.serviceDiedEvent.collect {
            Toast.makeText(context, "MaaService 异常关闭，请尝试重新启动", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onPageExit()
        }
    }

    if (!permissions.shizuku) {
        ShizukuPermissionDialog(
            isRequesting = isRequestingShizuku,
            onConfirm = {
                if (isRequestingShizuku) return@ShizukuPermissionDialog
                coroutineScope.launch {
                    isRequestingShizuku = true
                    val granted = permissionManager.requestShizuku()
                    isRequestingShizuku = false
                    if (granted) {
                        viewModel.onShizukuGranted()
                    } else {
                        Toast.makeText(context, "Shizuku 权限未获取，请重试", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        )
    }

    if (state.isFullscreenMonitor) {
        FullscreenPreviewDialog(
            onSurfaceAvailable = { surface ->
                viewModel.onSurfaceAvailable(MonitorSurfaceSource.FULLSCREEN, surface)
            },
            onSurfaceDestroyed = {
                viewModel.onSurfaceDestroyed(MonitorSurfaceSource.FULLSCREEN)
            },
            onDismiss = { viewModel.onToggleFullscreenMonitor() }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp)
        ) {
            if (!state.isFullscreenMonitor) {
                VirtualDisplayPreview(
                    isRunning = state.isMonitorRunning,
                    isLoading = state.isMonitorLoading,
                    errorMessage = state.monitorErrorMessage,
                    onSurfaceAvailable = { surface ->
                        viewModel.onSurfaceAvailable(MonitorSurfaceSource.EMBEDDED, surface)
                    },
                    onSurfaceDestroyed = {
                        viewModel.onSurfaceDestroyed(MonitorSurfaceSource.EMBEDDED)
                    },
                    onRetry = viewModel::retryMonitorBinding,
                    onClick = { viewModel.onToggleFullscreenMonitor() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.3f)
                )
            } else {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.3f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            BackgroundPanelHeader(
                selectedTab = state.currentTab,
                onTabSelected = { tab ->
                    viewModel.onTabChange(tab)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f),
                userScrollEnabled = true,
                beyondViewportPageCount = PanelTab.entries.size - 1
            ) { page ->
                when (page) {
                    0 -> {
                        Row(modifier = Modifier.fillMaxSize()) {
                            TaskListPanel(
                                tasks = tasks,
                                selectedTaskType = state.currentTaskType,
                                onTaskEnabledChange = { taskType, enabled ->
                                    viewModel.onTaskEnableChange(taskType, enabled)
                                },
                                onTaskSelected = { taskType ->
                                    viewModel.onSelectedTaskChange(taskType)
                                },
                                onTaskMove = { fromIndex, toIndex ->
                                    viewModel.onTaskMove(fromIndex, toIndex)
                                },
                                modifier = Modifier
                                    .fillMaxHeight()
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            BackgroundConfigurationPanel(
                                state = state,
                                viewModel = viewModel,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }

                    1 -> {
                        PlaceholderContent(
                            title = "自动战斗",
                            description = "功能开发中...",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    2 -> {
                        PlaceholderContent(
                            title = "小工具",
                            description = "功能开发中...",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    3 -> {
                        LogPanel(
                            logs = logs,
                            onClearLogs = viewModel::onClearLogs,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.onStartTasks() },
                    enabled = maaState != MaaExecutionState.RUNNING
                            && maaState != MaaExecutionState.STARTING,
                    modifier = Modifier.weight(1f)
                ) {
                    if (maaState == MaaExecutionState.STARTING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("开始任务")
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.onStopTasks() },
                    enabled = maaState == MaaExecutionState.RUNNING,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("停止任务")
                }
            }
        }

        // 错误/提示 Dialog
        state.dialog?.let { dialog ->
            TaskResultDialog(
                dialog = dialog,
                onDismiss = viewModel::onDialogDismiss,
                onConfirm = viewModel::onDialogConfirm
            )
        }
    }
}

@Composable
private fun BackgroundPanelHeader(
    selectedTab: PanelTab,
    onTabSelected: (PanelTab) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PanelTab.entries.forEach { tab ->
            Text(
                text = tab.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selectedTab == tab)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selectedTab == tab)
                    FontWeight.Bold
                else
                    FontWeight.Normal,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .then(
                        Modifier.clickable { onTabSelected(tab) }
                    )
            )
        }
    }
}

@Composable
private fun BackgroundConfigurationPanel(
    state: BackgroundTaskState,
    viewModel: BackgroundTaskViewModel,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    val wakeUpConfig by viewModel.taskConfig.wakeUpConfig.collectAsStateWithLifecycle()
    val recruitConfig by viewModel.taskConfig.recruitConfig.collectAsStateWithLifecycle()
    val infrastConfig by viewModel.taskConfig.infrastConfig.collectAsStateWithLifecycle()
    val fightConfig by viewModel.taskConfig.fightConfig.collectAsStateWithLifecycle()
    val mallConfig by viewModel.taskConfig.mallConfig.collectAsStateWithLifecycle()
    val awardConfig by viewModel.taskConfig.awardConfig.collectAsStateWithLifecycle()
    val roguelikeConfig by viewModel.taskConfig.roguelikeConfig.collectAsStateWithLifecycle()
    val reclamationConfig by viewModel.taskConfig.reclamationConfig.collectAsStateWithLifecycle()

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(top = 10.dp)) {
            when (state.currentTaskType) {
                TaskType.WAKE_UP -> WakeUpConfigPanel(
                    config = wakeUpConfig,
                    onConfigChange = {
                        coroutineScope.launch {
                            viewModel.taskConfig.setWakeUpConfig(it)
                        }
                    }
                )

                TaskType.RECRUITING -> RecruitConfigPanel(
                    config = recruitConfig,
                    onConfigChange = {
                        coroutineScope.launch {
                            viewModel.taskConfig.setRecruitConfig(it)
                        }
                    }
                )

                TaskType.BASE -> InfrastConfigPanel(
                    config = infrastConfig,
                    onConfigChange = {
                        coroutineScope.launch {
                            viewModel.taskConfig.setInfrastConfig(it)
                        }
                    }
                )

                TaskType.COMBAT -> FightConfigPanel(
                    config = fightConfig,
                    onConfigChange = {
                        coroutineScope.launch {
                            viewModel.taskConfig.setFightConfig(it)
                        }
                    }
                )

                TaskType.MALL -> MallConfigPanel(
                    config = mallConfig,
                    onConfigChange = {
                        coroutineScope.launch {
                            viewModel.taskConfig.setMallConfig(it)
                        }
                    }
                )

                TaskType.MISSION -> AwardConfigPanel(
                    config = awardConfig,
                    onConfigChange = {
                        coroutineScope.launch {
                            viewModel.taskConfig.setAwardConfig(it)
                        }
                    }
                )

                TaskType.AUTO_ROGUELIKE -> RoguelikeConfigPanel(
                    config = roguelikeConfig,
                    onConfigChange = {
                        coroutineScope.launch {
                            viewModel.taskConfig.setRoguelikeConfig(it)
                        }
                    },
                )

                TaskType.RECLAMATION -> ReclamationConfigPanel(
                    config = reclamationConfig,
                    onConfigChange = {
                        coroutineScope.launch {
                            viewModel.taskConfig.setReclamationConfig(it)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TaskResultDialog(
    dialog: PanelDialogUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val confirmColor = when (dialog.type) {
        PanelDialogType.SUCCESS -> MaterialTheme.colorScheme.primary
        PanelDialogType.WARNING -> MaterialTheme.colorScheme.tertiary
        PanelDialogType.ERROR -> MaterialTheme.colorScheme.error
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = dialog.title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Text(
                text = dialog.message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = confirmColor
                )
            ) {
                Text(dialog.confirmText)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(dialog.dismissText)
            }
        }
    )
}
