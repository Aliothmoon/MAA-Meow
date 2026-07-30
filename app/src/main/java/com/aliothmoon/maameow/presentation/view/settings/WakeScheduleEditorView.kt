package com.aliothmoon.maameow.presentation.view.settings

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.presentation.components.ListItemDivider
import com.aliothmoon.maameow.presentation.components.SettingRow
import com.aliothmoon.maameow.presentation.components.SettingsGroupCard
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.viewmodel.SettingsViewModel
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.resolve
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

/**
 * 「定时唤醒 + 解锁」二级设置页。
 * 用 [SettingsGroupCard] 把配置项分组成 form 表单风格：
 *   - 主开关卡片
 *   - 唤醒时间表卡片（输入 + chips）
 *   - 解锁配置卡片（方式 + 凭证）
 *   - 息屏延迟卡片
 *   - 测试卡片
 */
@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeScheduleEditorView(
    navController: NavController,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val wakeFeatureAvailable by viewModel.wakeFeatureAvailable.collectAsStateWithLifecycle()
    val wakeScheduleEnabled by viewModel.wakeScheduleEnabled.collectAsStateWithLifecycle()
    val wakeScheduleTimesCsv by viewModel.wakeScheduleTimesCsv.collectAsStateWithLifecycle()
    val wakeUnlockType by viewModel.wakeUnlockType.collectAsStateWithLifecycle()
    val wakeCredential by viewModel.wakeCredential.collectAsStateWithLifecycle()
    val wakeAutoSleepDelaySec by viewModel.wakeAutoSleepDelaySec.collectAsStateWithLifecycle()
    val wakeTestResult by viewModel.wakeTestResult.collectAsStateWithLifecycle()

    val contentColor = MaterialTheme.colorScheme.onSurface
    val scrollState = rememberScrollState()
    var showWakeNeedRootDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_wake_schedule_editor_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = { navController.navigateUp() },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState),
        ) {
            // ─── 1) 主开关卡片 ───
            SettingsGroupCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SettingSwitchItem(
                    title = stringResource(R.string.settings_wake_schedule_section),
                    description = if (wakeFeatureAvailable)
                        stringResource(R.string.settings_wake_schedule_section_desc)
                    else
                        stringResource(R.string.settings_wake_need_root_disabled),
                    contentColor = contentColor,
                    checked = wakeScheduleEnabled,
                    enabled = wakeFeatureAvailable,
                    onCheckedChange = { desired ->
                        if (!wakeFeatureAvailable) {
                            showWakeNeedRootDialog = true
                        } else {
                            viewModel.setWakeScheduleEnabled(desired)
                        }
                    },
                )
            }

            // ─── 详细配置（仅开关开启时展示） ───
            AnimatedVisibility(
                visible = wakeScheduleEnabled,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    // ─── 2) 唤醒时间表 ───
                    WakeTimesCard(
                        contentColor = contentColor,
                        timesCsv = wakeScheduleTimesCsv,
                        onTimesChange = viewModel::setWakeScheduleTimesCsv,
                    )

                    // ─── 3) 解锁配置 ───
                    UnlockConfigCard(
                        contentColor = contentColor,
                        unlockType = wakeUnlockType,
                        credential = wakeCredential,
                        onUnlockTypeChange = viewModel::setWakeUnlockType,
                        onCredentialChange = viewModel::setWakeCredential,
                    )

                    // ─── 4) 自动息屏 ───
                    AutoSleepCard(
                        contentColor = contentColor,
                        autoSleepSec = wakeAutoSleepDelaySec,
                        onAutoSleepChange = viewModel::setWakeAutoSleepDelaySec,
                    )

                    // ─── 5) 测试 ───
                    TestCard(
                        contentColor = contentColor,
                        onTestClick = {
                            viewModel.clearWakeTestResult()
                            viewModel.runWakeTest()
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ─── 测试 Toast ───
    wakeTestResult?.let { result ->
        val ctx = LocalContext.current
        LaunchedEffect(result) {
            if (result is UiText.Empty) return@LaunchedEffect
            val msg = when (result) {
                is UiText.Dynamic -> if (result.value == "OK") {
                    ctx.getString(R.string.settings_wake_test_ok)
                } else {
                    ctx.getString(R.string.settings_wake_test_fail, result.value)
                }
                is UiText.Resource -> result.resolve(ctx)
                else -> return@LaunchedEffect
            }
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearWakeTestResult()
        }
    }

    // ─── 非 Root 后端说明弹窗 ───
    if (showWakeNeedRootDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showWakeNeedRootDialog = false },
            title = { Text(stringResource(R.string.settings_wake_need_root_dialog_title)) },
            text = { Text(stringResource(R.string.settings_wake_need_root_dialog_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showWakeNeedRootDialog = false },
                ) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 分组卡片
// ─────────────────────────────────────────────────────────────

/** 唤醒时间表：输入框 + chips */
@Composable
private fun WakeTimesCard(
    contentColor: Color,
    timesCsv: String,
    onTimesChange: (String) -> Unit,
) {
    val parsed = remember(timesCsv) {
        timesCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
    var newTimeInput by remember { mutableStateOf("") }

    SettingsGroupCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionLabel(stringResource(R.string.settings_wake_schedule_times), contentColor)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newTimeInput,
                onValueChange = { newTimeInput = it.take(5) },
                placeholder = { Text(stringResource(R.string.settings_wake_schedule_times_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val token = newTimeInput.trim()
                    val parts = token.split(':')
                    val ok = parts.size == 2 &&
                            (parts[0].toIntOrNull() ?: -1) in 0..23 &&
                            (parts[1].toIntOrNull() ?: -1) in 0..59
                    if (ok) {
                        onTimesChange((parsed + token).joinToString(","))
                        newTimeInput = ""
                    }
                },
            ) { Text("+") }
        }

        if (parsed.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                parsed.forEach { t ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(t, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "×",
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.clickable {
                                    onTimesChange((parsed - t).joinToString(","))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 解锁方式 + 凭证 */
@Composable
private fun UnlockConfigCard(
    contentColor: Color,
    unlockType: String,
    credential: String,
    onUnlockTypeChange: (String) -> Unit,
    onCredentialChange: (String) -> Unit,
) {
    SettingsGroupCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionLabel(stringResource(R.string.settings_wake_unlock_type), contentColor)

        // 下拉选择解锁方式
        var menuExpanded by remember { mutableStateOf(false) }
        Box {
            SettingClickItem(
                title = stringResource(R.string.settings_wake_unlock_type),
                description = wakeUnlockTypeLabel(unlockType),
                contentColor = contentColor,
                onClick = { menuExpanded = true },
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                listOf("swipe", "pin", "password", "keyguard").forEach { type ->
                    DropdownMenuItem(
                        text = { Text(wakeUnlockTypeLabel(type)) },
                        onClick = {
                            onUnlockTypeChange(type)
                            menuExpanded = false
                        },
                    )
                }
            }
        }

        // PIN / 密码字段
        if (unlockType == "pin" || unlockType == "password") {
            ListItemDivider()
            Box(modifier = Modifier.padding(vertical = 8.dp)) {
                OutlinedTextField(
                    value = credential,
                    onValueChange = onCredentialChange,
                    label = { Text(stringResource(R.string.settings_wake_credential)) },
                    placeholder = { Text(stringResource(R.string.settings_wake_credential_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 自动息屏延迟 */
@Composable
private fun AutoSleepCard(
    contentColor: Color,
    autoSleepSec: Int,
    onAutoSleepChange: (Int) -> Unit,
) {
    SettingsGroupCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionLabel(stringResource(R.string.settings_wake_auto_sleep), contentColor)
        Text(
            stringResource(R.string.settings_wake_auto_sleep_desc, autoSleepSec),
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp),
        )
        Slider(
            value = autoSleepSec.toFloat(),
            onValueChange = { onAutoSleepChange(it.roundToInt()) },
            valueRange = 0f..300f,
            steps = 29,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
    }
}

/** 测试按钮 */
@Composable
private fun TestCard(
    contentColor: Color,
    onTestClick: () -> Unit,
) {
    SettingsGroupCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SettingClickItem(
            title = stringResource(R.string.settings_wake_test_button),
            description = stringResource(R.string.settings_wake_test_hint),
            contentColor = contentColor,
            onClick = onTestClick,
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 私有辅助
// ─────────────────────────────────────────────────────────────

/** Section 标题（卡片内顶部标签） */
@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color.copy(alpha = 0.75f),
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun SettingSwitchItem(
    title: String,
    description: String? = null,
    contentColor: Color,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(
        title = title,
        description = description,
        enabled = true,
        titleColor = if (enabled) contentColor else contentColor.copy(alpha = 0.6f),
        descriptionColor = if (enabled) contentColor.copy(alpha = 0.7f) else contentColor.copy(alpha = 0.4f),
        trailing = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        },
        onClick = { onCheckedChange(!checked) },
    )
}

@Composable
private fun SettingClickItem(
    title: String,
    description: String = "",
    contentColor: Color,
    onClick: () -> Unit,
) {
    SettingRow(
        title = title,
        description = description.ifEmpty { null },
        titleColor = contentColor,
        descriptionColor = contentColor.copy(alpha = 0.7f),
        onClick = onClick,
    )
}

private fun wakeUnlockTypeLabel(type: String): String = when (type) {
    "swipe" -> "Swipe (no lock)"
    "pin" -> "PIN"
    "password" -> "Password"
    "keyguard" -> "Force dismiss keyguard"
    else -> type
}
