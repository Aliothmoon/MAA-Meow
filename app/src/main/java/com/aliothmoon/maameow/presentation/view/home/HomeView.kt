package com.aliothmoon.maameow.presentation.view.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.constant.Routes
import com.aliothmoon.maameow.domain.models.OverlayControlMode
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.state.ResourceInitState
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.manager.ShizukuInstallHelper
import com.aliothmoon.maameow.presentation.components.ResourceInitDialog
import com.aliothmoon.maameow.presentation.components.UpdateCard
import com.aliothmoon.maameow.presentation.state.StatusColorType
import com.aliothmoon.maameow.presentation.viewmodel.HomeViewModel
import com.aliothmoon.maameow.presentation.viewmodel.UpdateViewModel
import com.aliothmoon.maameow.utils.Misc
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import timber.log.Timber


@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeView(
    navController: NavController,
    viewModel: HomeViewModel = koinViewModel(),
    updateViewModel: UpdateViewModel = koinViewModel(),
    permissionManager: PermissionManager = koinInject()
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionState by permissionManager.state.collectAsStateWithLifecycle()
    val resourceVersion by updateViewModel.currentResourceVersion.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val (width, height) = Misc.getScreenSize(context)

    // 启动时检查资源初始化
    LaunchedEffect(Unit) {
        viewModel.checkAndInitResource()
    }
    // 资源初始化完成后刷新版本号
    LaunchedEffect(uiState.resourceInitState) {
        if (uiState.resourceInitState is ResourceInitState.Ready) {
            updateViewModel.refreshResourceVersion()
        }
    }

    // 资源初始化弹窗
    ResourceInitDialog(
        state = uiState.resourceInitState,
        onRetry = { viewModel.onTryResourceInit() }
    )

    if (uiState.showRunModeUnsupportedDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissRunModeUnsupportedDialog() },
            title = { Text("模式不支持") },
            text = { Text(uiState.runModeUnsupportedMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.onDismissRunModeUnsupportedDialog() }) {
                    Text("我知道了")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = "MAA",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(Routes.SETTINGS)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 屏幕分辨率显示
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "屏幕分辨率",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$width × $height",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "资源版本",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = resourceVersion.ifBlank { "未安装" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (resourceVersion.isBlank())
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // 服务状态（组合显示）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "服务状态",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val statusColor = when (uiState.serviceStatusColor) {
                                StatusColorType.PRIMARY -> MaterialTheme.colorScheme.primary
                                StatusColorType.WARNING -> Color(0xFFFF9800)
                                StatusColorType.ERROR -> MaterialTheme.colorScheme.error
                                StatusColorType.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Text(
                                    text = uiState.serviceStatusText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = statusColor
                                )
                                if (uiState.serviceStatusLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = statusColor
                                    )
                                }
                            }
                        }
                    }
                }

                // 运行模式切换
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "运行模式",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = when (uiState.runMode) {
                                    RunMode.FOREGROUND -> "前台模式：悬浮窗控制"
                                    RunMode.BACKGROUND -> "后台模式：应用内运行"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = uiState.runMode.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Switch(
                                checked = uiState.runMode == RunMode.BACKGROUND,
                                enabled = viewModel.checkRunModeChangeEnabled(),
                                onCheckedChange = { isBackground ->
                                    viewModel.onRunModeChange(isBackground)
                                }
                            )
                        }
                    }
                }

                // 更新管理卡片（应用更新 + 资源更新）
                UpdateCard()

                // 权限管理卡片
                var expandedPermissions by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Text(
                            text = "权限管理",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        // Shizuku权限 - 始终显示
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Shizuku权限",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            TextButton(
                                onClick = { viewModel.onRequestShizuku(context) },
                                enabled = !permissionState.shizuku && !uiState.isGranting,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                if (uiState.isGranting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(text = if (permissionState.shizuku) "已授权" else "请求权限")
                                }
                            }
                        }

                        // 展开/折叠按钮
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedPermissions = !expandedPermissions }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (expandedPermissions) "收起其他权限" else "展开其他权限",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Icon(
                                imageVector = if (expandedPermissions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }

                        // 折叠区域 - 其他权限
                        AnimatedVisibility(visible = expandedPermissions) {
                            Column {
                                // 悬浮窗权限
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "悬浮窗权限",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    TextButton(
                                        onClick = { viewModel.onRequestOverlay(context) },
                                        enabled = !permissionState.overlay,
                                        contentPadding = PaddingValues(
                                            horizontal = 8.dp,
                                            vertical = 0.dp
                                        )
                                    ) {
                                        Text(text = if (permissionState.overlay) "已授权" else "请求权限")
                                    }
                                }

                                // 存储权限
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "存储权限",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    TextButton(
                                        onClick = { viewModel.onRequestStorage(context) },
                                        enabled = !permissionState.storage,
                                        contentPadding = PaddingValues(
                                            horizontal = 8.dp,
                                            vertical = 0.dp
                                        )
                                    ) {
                                        Text(text = if (permissionState.storage) "已授权" else "请求权限")
                                    }
                                }

                                // 电源管理白名单
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "电源管理白名单",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    TextButton(
                                        onClick = { viewModel.onRequestBatteryWhitelist(context) },
                                        enabled = !permissionState.batteryWhitelist,
                                        contentPadding = PaddingValues(
                                            horizontal = 8.dp,
                                            vertical = 0.dp
                                        )
                                    ) {
                                        Text(text = if (permissionState.batteryWhitelist) "已添加" else "请求权限")
                                    }
                                }

                                // 无障碍权限（音量键快捷操作）
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "无障碍权限",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    TextButton(
                                        onClick = {
                                            viewModel.onRequestAccessibility(context)
                                        },
                                        enabled = !permissionState.accessibility,
                                        contentPadding = PaddingValues(
                                            horizontal = 8.dp,
                                            vertical = 0.dp
                                        )
                                    ) {
                                        Text(text = if (permissionState.accessibility) "已授权" else "请求权限")
                                    }
                                }

                                // 通知权限
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "通知权限",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    TextButton(
                                        onClick = { viewModel.onRequestNotification(context) },
                                        enabled = !permissionState.notification,
                                        contentPadding = PaddingValues(
                                            horizontal = 8.dp,
                                            vertical = 0.dp
                                        )
                                    ) {
                                        Text(text = if (permissionState.notification) "已授权" else "请求权限")
                                    }
                                }
                            }
                        }
                    }
                }


                // 悬浮窗相关配置
                if (uiState.runMode == RunMode.FOREGROUND) {
                    // 分辨率控制按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.onChangeTo16x9Resolution(context)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "调整为适配分辨率",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.onResetResolution(context)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "重置分辨率",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "悬浮窗模式",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = if (uiState.overlayControlMode == OverlayControlMode.ACCESSIBILITY)
                                        "音量上下键同时按切换面板"
                                    else
                                        "点击悬浮球切换面板",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = uiState.overlayControlMode.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Switch(
                                    checked = uiState.overlayControlMode == OverlayControlMode.ACCESSIBILITY,
                                    onCheckedChange = { isAccessibility ->
                                        viewModel.onControlOverlayModeChanged(
                                            if (isAccessibility) OverlayControlMode.ACCESSIBILITY
                                            else OverlayControlMode.FLOAT_BALL
                                        )
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            if (uiState.isShowControlOverlay) {
                                Timber.d("关闭悬浮窗")
                                viewModel.onStopControlOverlay()
                            } else {
                                Timber.d("开启悬浮窗模式")
                                viewModel.onStartControlOverlay()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isShowControlOverlay)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                        ),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (uiState.isShowControlOverlay) "关闭操作面板" else "打开操作面板",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 关闭所有服务按钮
                OutlinedButton(
                    onClick = {
                        Timber.d("关闭所有服务")
                        viewModel.onStopAllServices()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = MaterialTheme.shapes.large,
                    enabled = !uiState.isLoading
                ) {
                    Text(
                        text = "关闭所有服务",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Shizuku/Sui 检测
        var shizukuStatus by remember {
            mutableStateOf(ShizukuInstallHelper.checkStatus(context))
        }
        var suiWarningDismissed by remember { mutableStateOf(false) }
        LifecycleResumeEffect(Unit) {
            shizukuStatus = ShizukuInstallHelper.checkStatus(context)
            onPauseOrDispose {}
        }
        when (shizukuStatus) {
            ShizukuInstallHelper.ShizukuStatus.NOT_INSTALLED -> {
                AlertDialog(
                    onDismissRequest = {},
                    properties = DialogProperties(
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false,
                    ),
                    title = { Text("未检测到 Shizuku") },
                    text = {
                        Text("本应用依赖 Shizuku 服务运行，检测到设备未安装 Shizuku，请先安装。")
                    },
                    confirmButton = {
                        Button(
                            onClick = { ShizukuInstallHelper.installShizuku(context) }
                        ) {
                            Text("安装 Shizuku")
                        }
                    }
                )
            }

            ShizukuInstallHelper.ShizukuStatus.SUI_DETECTED -> {
                if (!suiWarningDismissed) {
                    AlertDialog(
                        onDismissRequest = { suiWarningDismissed = true },
                        title = { Text("检测到 Sui") },
                        text = {
                            Text("当前使用 Sui 提供 Shizuku 服务，Sui 以 Root 权限运行，MaaMeow 可能无法正常工作，请以实际测试为主")
                        },
                        confirmButton = {
                            Button(onClick = { suiWarningDismissed = true }) {
                                Text("知道了")
                            }
                        }
                    )
                }
            }

            ShizukuInstallHelper.ShizukuStatus.INSTALLED -> {}
        }
    }
}
