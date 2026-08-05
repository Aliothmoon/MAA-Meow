package com.aliothmoon.maameow.presentation.view.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.presentation.components.ListItemDivider
import com.aliothmoon.maameow.presentation.components.SettingRow
import com.aliothmoon.maameow.presentation.components.SettingsGroupCard
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.viewmodel.SettingsViewModel
import com.aliothmoon.maameow.utils.i18n.resolve
import org.koin.androidx.compose.koinViewModel

/**
 * 「唤醒 + 解锁」配置页。定时任务触发时先解锁，避免任务在锁屏状态下跑
 * —— 锁屏会盖在虚拟显示器上，前后台两种运行模式都会被挡住。
 *
 * 只配置解锁方式和 PIN。没有滑动坐标校准、没有等待秒数、没有重试次数：
 * 解锁走 IWindowManager.dismissKeyguard，不需要模拟上滑，时序靠状态轮询。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeScheduleEditorView(
    navController: NavController,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val wakeUnlockType by viewModel.wakeUnlockType.collectAsStateWithLifecycle()
    val wakeCredential by viewModel.wakeCredential.collectAsStateWithLifecycle()
    val wakeTestState by viewModel.wakeTestState.collectAsStateWithLifecycle()

    val contentColor = MaterialTheme.colorScheme.onSurface
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_wake_unlock_section),
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
            SettingsGroupCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    SettingClickItem(
                        title = stringResource(R.string.settings_wake_unlock_type),
                        description = stringResource(unlockTypeLabelRes(wakeUnlockType)),
                        contentColor = contentColor,
                        onClick = { menuExpanded = true },
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        UNLOCK_TYPES.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(stringResource(unlockTypeLabelRes(type))) },
                                onClick = {
                                    viewModel.setWakeUnlockType(type)
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                }

                if (wakeUnlockType == TYPE_PIN) {
                    ListItemDivider()
                    // 本地持有输入值，避免 DataStore 异步回写导致光标跳动
                    var localPin by rememberSaveable { mutableStateOf(wakeCredential) }
                    var pinVisible by rememberSaveable { mutableStateOf(false) }
                    Box(modifier = Modifier.padding(vertical = 8.dp)) {
                        OutlinedTextField(
                            value = localPin,
                            onValueChange = {
                                val digits = it.filter { c -> c.isDigit() }.take(MAX_PIN_LENGTH)
                                localPin = digits
                                viewModel.setWakeCredential(digits)
                            },
                            label = { Text(stringResource(R.string.settings_wake_credential)) },
                            placeholder = { Text(stringResource(R.string.settings_wake_credential_hint)) },
                            singleLine = true,
                            visualTransformation = if (pinVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    SettingRow(
                        title = stringResource(
                            if (pinVisible) R.string.settings_wake_credential_hide
                            else R.string.settings_wake_credential_show
                        ),
                        titleColor = contentColor.copy(alpha = 0.7f),
                        onClick = { pinVisible = !pinVisible },
                    )
                    ListItemDivider()
                    Text(
                        text = stringResource(R.string.settings_wake_credential_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            SettingsGroupCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SettingClickItem(
                    title = stringResource(R.string.settings_wake_test_button),
                    description = stringResource(R.string.settings_wake_test_hint),
                    contentColor = contentColor,
                    onClick = { viewModel.runWakeTest() },
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    wakeTestState?.let { state ->
        val ctx = LocalContext.current
        LaunchedEffect(state) {
            val done = state as? SettingsViewModel.WakeTestState.Done ?: return@LaunchedEffect
            android.widget.Toast.makeText(
                ctx, done.result.message.resolve(ctx), android.widget.Toast.LENGTH_LONG
            ).show()
            viewModel.clearWakeTestResult()
        }
    }
}

private const val TYPE_PIN = "pin"
private const val MAX_PIN_LENGTH = 16
private val UNLOCK_TYPES = listOf("none", "swipe", TYPE_PIN)

private fun unlockTypeLabelRes(type: String): Int = when (type) {
    "none" -> R.string.settings_wake_unlock_type_none
    "swipe" -> R.string.settings_wake_unlock_type_swipe
    TYPE_PIN -> R.string.settings_wake_unlock_type_pin
    else -> R.string.settings_wake_unlock_type_swipe
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
