package com.aliothmoon.maameow.presentation.view.settings

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
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

/**
 * 「锁屏解锁配置」二级设置页。
 * 仅配置解锁方式 + 凭证（全局共享），唤醒时间 / 自动熄屏等选项已移至定时任务编辑页。
 */
@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeScheduleEditorView(
    navController: NavController,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val wakeFeatureAvailable by viewModel.wakeFeatureAvailable.collectAsStateWithLifecycle()
    val wakeUnlockType by viewModel.wakeUnlockType.collectAsStateWithLifecycle()
    val wakeCredential by viewModel.wakeCredential.collectAsStateWithLifecycle()
    val wakeTestResult by viewModel.wakeTestResult.collectAsStateWithLifecycle()
    val swipeStartXPercent by viewModel.swipeStartXPercent.collectAsStateWithLifecycle()
    val swipeStartYPercent by viewModel.swipeStartYPercent.collectAsStateWithLifecycle()
    val wakePinWaitSec by viewModel.wakePinWaitSec.collectAsStateWithLifecycle()
    val wakeUnlockMaxRetries by viewModel.wakeUnlockMaxRetries.collectAsStateWithLifecycle()

    val contentColor = MaterialTheme.colorScheme.onSurface
    val scrollState = rememberScrollState()
    var showCalibrationDialog by rememberSaveable { mutableStateOf(false) }
    // 校准完成后显示 Toast 的标记（存储 "x%,y%" 字符串）
    var calibrationToastText by rememberSaveable { mutableStateOf<String?>(null) }

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
            // ─── 解锁方式 + 凭证 ───
            SettingsGroupCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SectionLabel(stringResource(R.string.settings_wake_unlock_type), contentColor)

                // 下拉选择解锁方式
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    SettingClickItem(
                        title = stringResource(R.string.settings_wake_unlock_type),
                        description = wakeUnlockTypeLabel(wakeUnlockType),
                        contentColor = contentColor,
                        onClick = { menuExpanded = true },
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        listOf("none", "swipe", "pin", "password", "keyguard").forEach { type ->
                            DropdownMenuItem(
                                text = { Text(wakeUnlockTypeLabel(type)) },
                                onClick = {
                                    viewModel.setWakeUnlockType(type)
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                }

                // PIN / 密码字段
                if (wakeUnlockType == "pin" || wakeUnlockType == "password") {
                    ListItemDivider()
                    // 本地持有输入值，避免 DataStore 异步 round-trip 导致光标跳动
                    var localCredential by rememberSaveable { mutableStateOf(wakeCredential) }
                    Box(modifier = Modifier.padding(vertical = 8.dp)) {
                        OutlinedTextField(
                            value = localCredential,
                            onValueChange = {
                                val truncated = it.take(64)
                                localCredential = truncated
                                viewModel.setWakeCredential(truncated)
                            },
                            label = { Text(stringResource(R.string.settings_wake_credential)) },
                            placeholder = { Text(stringResource(R.string.settings_wake_credential_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // ─── 说明 ───
            if (!wakeFeatureAvailable) {
                SettingsGroupCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.settings_wake_need_root_disabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f),
                    )
                }
            }

            // ─── 测试 ───
            SettingsGroupCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SettingClickItem(
                    title = stringResource(R.string.settings_wake_test_button),
                    description = stringResource(R.string.settings_wake_test_hint),
                    contentColor = contentColor,
                    onClick = {
                        viewModel.clearWakeTestResult()
                        viewModel.runWakeTest()
                    },
                )
            }

            // ─── 滑动起点校准 ───
            SettingsGroupCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SectionLabel(stringResource(R.string.settings_swipe_calibration_title), contentColor)
                SettingClickItem(
                    title = stringResource(R.string.settings_swipe_calibration_button),
                    description = if (swipeStartXPercent >= 0f && swipeStartYPercent >= 0f) {
                        stringResource(
                            R.string.settings_swipe_calibration_calibrated,
                            (swipeStartXPercent * 100).toInt(),
                            (swipeStartYPercent * 100).toInt(),
                        )
                    } else {
                        stringResource(R.string.settings_swipe_calibration_hint)
                    },
                    contentColor = contentColor,
                    onClick = { showCalibrationDialog = true },
                )
                if (swipeStartXPercent >= 0f && swipeStartYPercent >= 0f) {
                    ListItemDivider()
                    SettingClickItem(
                        title = stringResource(R.string.settings_swipe_calibration_clear),
                        description = stringResource(R.string.settings_swipe_calibration_clear_hint),
                        contentColor = contentColor,
                        onClick = {
                            viewModel.clearSwipeCalibration()
                            calibrationToastText = ""
                        },
                    )
                }
            }

            // ─── 高级：等待时间 + 重试次数 ───
            SettingsGroupCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SectionLabel(stringResource(R.string.settings_wake_advanced_title), contentColor)

                // PIN 等待时间下拉
                var waitMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    SettingClickItem(
                        title = stringResource(R.string.settings_wake_pin_wait_sec),
                        description = stringResource(
                            R.string.settings_wake_pin_wait_sec_value,
                            wakePinWaitSec,
                        ),
                        contentColor = contentColor,
                        onClick = { waitMenuExpanded = true },
                    )
                    DropdownMenu(
                        expanded = waitMenuExpanded,
                        onDismissRequest = { waitMenuExpanded = false },
                    ) {
                        listOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f).forEach { sec ->
                            DropdownMenuItem(
                                text = { Text("%.1f s".format(sec)) },
                                onClick = {
                                    viewModel.setWakePinWaitSec(sec)
                                    waitMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                ListItemDivider()

                // 重试次数下拉
                var retryMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    SettingClickItem(
                        title = stringResource(R.string.settings_wake_unlock_max_retries),
                        description = stringResource(
                            R.string.settings_wake_unlock_max_retries_value,
                            wakeUnlockMaxRetries,
                        ),
                        contentColor = contentColor,
                        onClick = { retryMenuExpanded = true },
                    )
                    DropdownMenu(
                        expanded = retryMenuExpanded,
                        onDismissRequest = { retryMenuExpanded = false },
                    ) {
                        (0..3).forEach { n ->
                            DropdownMenuItem(
                                text = { Text(wakeUnlockRetriesLabel(n)) },
                                onClick = {
                                    viewModel.setWakeUnlockMaxRetries(n)
                                    retryMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ─── 校准全屏对话框 ───
    if (showCalibrationDialog) {
        SwipeCalibrationDialog(
            onCalibrated = { xPct, yPct ->
                viewModel.setSwipeCalibration(xPct, yPct)
                calibrationToastText = "${(xPct * 100).toInt()}%,${(yPct * 100).toInt()}%"
                showCalibrationDialog = false
            },
            onDismiss = { showCalibrationDialog = false },
        )
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

    // ─── 校准 Toast ───
    calibrationToastText?.let { text ->
        val ctx = LocalContext.current
        LaunchedEffect(text) {
            val msg = if (text.isEmpty()) {
                ctx.getString(R.string.settings_swipe_calibration_cleared)
            } else {
                ctx.getString(R.string.settings_swipe_calibration_saved, text)
            }
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
            calibrationToastText = null
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 私有辅助
// ─────────────────────────────────────────────────────────────

/**
 * 滑动起点校准全屏对话框。用户触摸屏幕任意位置，记录该点相对屏幕的百分比坐标。
 * 用黑色背景全屏覆盖，确保触摸坐标准确（不受父组件 padding / 系统栏影响）。
 */
@Composable
private fun SwipeCalibrationDialog(
    onCalibrated: (xPercent: Float, yPercent: Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val size = containerSize
                    if (size.width > 0 && size.height > 0) {
                        val xPct = (offset.x / size.width).coerceIn(0f, 1f)
                        val yPct = (offset.y / size.height).coerceIn(0f, 1f)
                        onCalibrated(xPct, yPct)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = stringResource(R.string.swipe_calibration_dialog_title),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.swipe_calibration_dialog_hint),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.swipe_calibration_dialog_cancel),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable { onDismiss() },
            )
        }
    }
}

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
    "none" -> "关闭（不解锁）"
    "swipe" -> "Swipe (no lock)"
    "pin" -> "PIN"
    "password" -> "Password"
    "keyguard" -> "Force dismiss keyguard"
    else -> type
}

private fun wakeUnlockRetriesLabel(n: Int): String = when (n) {
    0 -> "不重试（仅执行一次）"
    1 -> "1 次"
    2 -> "2 次（推荐）"
    3 -> "3 次"
    else -> "$n 次"
}
