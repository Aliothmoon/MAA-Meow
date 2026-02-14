package com.aliothmoon.maameow.presentation.view.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.domain.state.ResourceInitState
import com.aliothmoon.maameow.domain.service.ResourceInitService
import com.aliothmoon.maameow.domain.service.LogExportService
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.presentation.components.InfoCard
import com.aliothmoon.maameow.presentation.components.ReInitializeConfirmDialog
import com.aliothmoon.maameow.presentation.components.ResourceInitDialog
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun SettingsView(
    navController: NavController,
    viewModel: SettingsViewModel = koinViewModel(),
    resourceInitService: ResourceInitService = koinInject(),
    permissionManager: PermissionManager = koinInject(),
    logExportService: LogExportService = koinInject()
) {
    val resourceInitState by resourceInitService.state.collectAsStateWithLifecycle()
    val debugMode by viewModel.debugMode.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    // 重新初始化确认弹窗
    var showReInitConfirm by remember { mutableStateOf(false) }

    // 调试模式确认弹窗
    var showDebugModeConfirm by remember { mutableStateOf(false) }

    if (showReInitConfirm) {
        ReInitializeConfirmDialog(
            onConfirm = {
                showReInitConfirm = false
                coroutineScope.launch {
                    resourceInitService.reInitialize()
                }
            },
            onDismiss = { showReInitConfirm = false }
        )
    }

    if (showDebugModeConfirm) {
        AlertDialog(
            onDismissRequest = { showDebugModeConfirm = false },
            title = {
                Text(
                    text = "启用调试模式",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "启用调试模式后将重启服务以记录详细日志。\n\n请在重启后重新操作以复现问题，相关日志将被完整记录。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = {
                    showDebugModeConfirm = false
                    viewModel.setDebugMode(true)
                }) {
                    Text("确认重启")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDebugModeConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 解压进度弹窗
    val context = LocalContext.current
    if (resourceInitState is ResourceInitState.Extracting) {
        ResourceInitDialog(
            state = resourceInitState,
            onRetry = {},
            onRequestPermission = {
                coroutineScope.launch {
                    // 先请求权限，再继续初始化流程
                    permissionManager.requestStorage(context)
                    resourceInitService.onPermissionChecking()
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "设置",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = { navController.navigateUp() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // 资源管理部分
            Spacer(modifier = Modifier.height(8.dp))
            InfoCard(
                title = "",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Text(
                    text = "资源管理",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showReInitConfirm = true }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "重新初始化资源",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "从内置资源包重新解压",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.1f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("log_history") }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "历史日志",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "查看任务执行日志",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.1f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("error_log") }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "错误日志",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "查看应用异常和错误记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.1f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            coroutineScope.launch {
                                val intent = logExportService.exportAllLogs()
                                if (intent != null) {
                                    context.startActivity(Intent.createChooser(intent, "导出日志"))
                                }
                            }
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "导出日志压缩包",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "打包所有日志为 ZIP 文件分享",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.1f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "调试模式",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "启用后记录详细日志信息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Switch(
                        checked = debugMode,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showDebugModeConfirm = true
                            } else {
                                viewModel.setDebugMode(false)
                            }
                        }
                    )
                }
            }

            // 关于部分
            InfoCard(
                title = "",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "版本",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "开发者",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Aliothmoon",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}