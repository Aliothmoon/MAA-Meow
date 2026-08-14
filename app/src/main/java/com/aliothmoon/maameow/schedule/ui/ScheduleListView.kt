package com.aliothmoon.maameow.schedule.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.Routes
import com.aliothmoon.maameow.presentation.components.InfoCard
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import com.aliothmoon.maameow.schedule.service.AutoStartHelper
import com.aliothmoon.maameow.schedule.service.ExactAlarmSettings
import com.aliothmoon.maameow.theme.MaaDesignTokens
import org.koin.androidx.compose.koinViewModel

@Composable
fun ScheduleListView(
    navController: NavController,
    viewModel: ScheduleListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }
    var showAutoStartGuide by remember { mutableStateOf(false) }

    // 设置页没有结果回调，回来时重读精确闹钟开关
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshExactAlarmPermission()
    }

    // 首次有策略时检查是否需要自启动引导
    LaunchedEffect(state.strategies.isNotEmpty()) {
        if (state.strategies.isNotEmpty() && AutoStartHelper.isKnownRestrictiveManufacturer()) {
            val prefs = context.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("autostart_guided", false)) {
                val intent = AutoStartHelper.getAutoStartIntent(context)
                if (intent != null) {
                    showAutoStartGuide = true
                    prefs.edit { putBoolean("autostart_guided", true) }
                }
            }
        }
    }

    fun openExactAlarmSettings() {
        ExactAlarmSettings.open(context)
        viewModel.refreshExactAlarmPermission()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.schedule_title),
                actions = {
                    // 与阻断卡片同一条路；允许之后卡片消失，靠这个入口回到系统开关页
                    if (state.exactAlarmConfigurable) {
                        IconButton(onClick = { openExactAlarmSettings() }) {
                            Icon(
                                imageVector = Icons.Outlined.Alarm,
                                contentDescription = stringResource(R.string.schedule_exact_alarm_settings),
                            )
                        }
                    }
                    IconButton(onClick = { navController.navigate(Routes.SCHEDULE_TRIGGER_LOG) }) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = stringResource(R.string.schedule_trigger_log_title),
                        )
                    }
                    IconButton(onClick = { navController.navigate("schedule_edit/new") }) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.schedule_create_strategy),
                        )
                    }
                }
            )
        },
        // 外层 MainScreen 的 bottomBar 已消费导航栏 inset；此处不再重复预留，
        // 否则底部会多出一条等高于导航栏的空白条
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = MaaDesignTokens.Spacing.listHorizontal,
                vertical = MaaDesignTokens.Spacing.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.exactAlarmAllowed) {
                item(key = "exact-alarm") {
                    ExactAlarmCard(onGrant = { openExactAlarmSettings() })
                }
            }
            if (state.strategies.isEmpty()) {
                item(key = "empty") {
                    ScheduleEmptyState(modifier = Modifier.fillParentMaxSize())
                }
            } else {
                items(state.strategies, key = { it.id }) { strategy ->
                    val profileName = state.profiles.find { it.id == strategy.profileId }?.name
                    StrategyCard(
                        strategy = strategy,
                        profileName = profileName,
                        nextTrigger = viewModel.getNextTriggerTime(strategy),
                        onToggleEnabled = { viewModel.onToggleEnabled(strategy.id, it) },
                        onClick = { navController.navigate("schedule_edit/${strategy.id}") },
                        onDelete = { deleteConfirmId = strategy.id }
                    )
                }
            }
        }

        if (deleteConfirmId != null) {
            AlertDialog(
                onDismissRequest = { deleteConfirmId = null },
                title = { Text(stringResource(R.string.schedule_delete_strategy_title)) },
                text = { Text(stringResource(R.string.schedule_delete_strategy_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.onDeleteStrategy(deleteConfirmId!!)
                        deleteConfirmId = null
                    }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmId = null }) { Text(stringResource(R.string.common_cancel)) }
                }
            )
        }

        if (showAutoStartGuide) {
            AlertDialog(
                onDismissRequest = { showAutoStartGuide = false },
                title = { Text(stringResource(R.string.schedule_auto_start_permission_title)) },
                text = { Text(stringResource(R.string.schedule_auto_start_permission_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        AutoStartHelper.getAutoStartIntent(context)?.let {
                            runCatching { context.startActivity(it) }
                        }
                        showAutoStartGuide = false
                    }) { Text(stringResource(R.string.schedule_go_to_settings)) }
                },
                dismissButton = {
                    TextButton(onClick = { showAutoStartGuide = false }) { Text(stringResource(R.string.schedule_later)) }
                }
            )
        }
    }
}

@Composable
private fun ExactAlarmCard(onGrant: () -> Unit) {
    InfoCard(title = stringResource(R.string.schedule_exact_alarm_blocked)) {
        Text(
            text = stringResource(R.string.schedule_exact_alarm_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onGrant) {
            Text(stringResource(R.string.schedule_exact_alarm_grant))
        }
    }
}

@Composable
private fun ScheduleEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.DateRange,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.schedule_empty_state),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.schedule_empty_hint_add),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun StrategyCard(
    strategy: ScheduleStrategy,
    profileName: String?,
    nextTrigger: String?,
    onToggleEnabled: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(strategy.name, style = MaterialTheme.typography.titleMedium)

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = localizedScheduleStrategySummary(strategy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (profileName != null) {
                    Text(
                        text = profileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (strategy.enabled && nextTrigger != null) {
                    Text(
                        text = stringResource(R.string.schedule_next_trigger, nextTrigger),
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                val lastResultText = strategy.lastResult?.let {
                    formatExecutionResult(it, strategy.lastResultMessage)
                }
                if (lastResultText != null) {
                    Text(
                        text = lastResultText,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = executionResultColor(
                            strategy.lastResult,
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.error,
                            MaterialTheme.colorScheme.tertiary,
                        ),
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = strategy.enabled,
                onCheckedChange = onToggleEnabled
            )
        }
    }
}

@Composable
private fun formatExecutionResult(result: ExecutionResult, message: String?): String {
    val label = when (result) {
        ExecutionResult.STARTED,
        ExecutionResult.FAILED_VALIDATION,
        ExecutionResult.FAILED_START,
        ExecutionResult.FAILED_UI_LAUNCH,
        ExecutionResult.SKIPPED_BUSY,
        ExecutionResult.CANCELLED -> {
            stringResource(R.string.schedule_last_result, scheduleExecutionResultLabel(result))
        }

        else -> return ""
    }
    return if (message.isNullOrBlank()) label else "$label · $message"
}

private fun executionResultColor(
    result: ExecutionResult?,
    successColor: Color,
    errorColor: Color,
    warningColor: Color,
): Color {
    return when (result) {
        ExecutionResult.STARTED -> successColor
        ExecutionResult.SKIPPED_BUSY,
        ExecutionResult.CANCELLED -> warningColor

        ExecutionResult.FAILED_VALIDATION,
        ExecutionResult.FAILED_START,
        ExecutionResult.FAILED_UI_LAUNCH -> errorColor

        else -> Color.Unspecified
    }
}
