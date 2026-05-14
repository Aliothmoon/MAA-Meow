package com.aliothmoon.maameow.presentation.view.home

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import dev.jeziellago.compose.markdowntext.MarkdownText
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.Routes
import com.aliothmoon.maameow.data.datasource.ResourceDownloader
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.permission.PermissionState
import com.aliothmoon.maameow.domain.models.OverlayControlMode
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.state.ResourceInitState
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.manager.ShizukuInstallHelper
import com.aliothmoon.maameow.presentation.components.AdaptiveTaskPromptDialog
import com.aliothmoon.maameow.presentation.components.ChangelogDialog
import com.aliothmoon.maameow.presentation.components.ResourceInitDialog
import com.aliothmoon.maameow.presentation.components.UpdateCard
import com.aliothmoon.maameow.presentation.state.StatusColorType
import com.aliothmoon.maameow.presentation.viewmodel.HomeViewModel
import com.aliothmoon.maameow.presentation.viewmodel.UpdateViewModel
import com.aliothmoon.maameow.utils.Misc
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.asString
import com.aliothmoon.maameow.utils.i18n.overlayControlModeDisplayName
import com.aliothmoon.maameow.utils.i18n.remoteBackendPermissionLabel
import com.aliothmoon.maameow.utils.i18n.runModeDisplayName
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlinx.coroutines.launch
import timber.log.Timber


@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeView(
    navController: NavController,
    viewModel: HomeViewModel = koinViewModel(),
    updateViewModel: UpdateViewModel = koinViewModel(),
    permissionManager: PermissionManager = koinInject(),
    appSettingsManager: AppSettingsManager = koinInject()
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionState by permissionManager.state.collectAsStateWithLifecycle()
    val resourceVersion by updateViewModel.currentResourceVersion.collectAsStateWithLifecycle()
    val state by RemoteServiceManager.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val (width, height) = Misc.getScreenSize(context)

    val startupDialog by updateViewModel.startupUpdateDialog.collectAsStateWithLifecycle()

    // 启动时检查资源初始化
    LaunchedEffect(Unit) {
        viewModel.checkAndInitResource()
    }

    // 自动下载失败时弹 Toast
    LaunchedEffect(Unit) {
        updateViewModel.toastMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // 资源初始化完成后刷新版本号
    LaunchedEffect(uiState.resourceInitState) {
        if (uiState.resourceInitState is ResourceInitState.Ready) {
            updateViewModel.refreshResourceVersion()
            updateViewModel.checkPendingChangelog()
            updateViewModel.checkUpdatesOnStartup()
        }
    }

    // 资源初始化弹窗
    ResourceInitDialog(
        state = uiState.resourceInitState,
        onRetry = { viewModel.onTryResourceInit() }
    )

    // 更新公告弹窗
    val changelogDialog by updateViewModel.changelogDialog.collectAsStateWithLifecycle()
    changelogDialog?.let {
        ChangelogDialog(
            content = it,
            onDismiss = { updateViewModel.dismissChangelog() }
        )
    }

    // 发现更新弹窗
    startupDialog?.let { result ->
        val appVersionLine = result.appUpdate?.let {
            stringResource(R.string.dialog_update_app_version_line, it.version)
        }.orEmpty()
        val resourceMessage = result.resourceUpdate?.let {
            val display = ResourceDownloader.formatVersionForDisplay(it.version)
            stringResource(R.string.update_confirm_message_resource, display)
        }.orEmpty()
        val releaseNote = result.appUpdate?.releaseNote
        AdaptiveTaskPromptDialog(
            visible = true,
            title = stringResource(R.string.dialog_update_found_title),
            icon = Icons.Rounded.Info,
            confirmText = stringResource(R.string.dialog_update_confirm),
            confirmColor = Color(0xFF4CAF50),
            dismissText = stringResource(R.string.dialog_update_dismiss),
            onConfirm = {
                if (result.appUpdate != null) {
                    updateViewModel.confirmAppDownload(result.appUpdate.version)
                } else {
                    updateViewModel.confirmResourceDownload()
                }
                updateViewModel.dismissStartupDialog()
            },
            onDismissRequest = { updateViewModel.dismissStartupDialog() },
            content = {
                Column {
                    Text(
                        text = appVersionLine + resourceMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!releaseNote.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        MarkdownText(
                            markdown = releaseNote,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        )
    }

    if (uiState.showRunModeUnsupportedDialog) {
        AdaptiveTaskPromptDialog(
            visible = true,
            title = stringResource(R.string.dialog_run_mode_unsupported_title),
            message = uiState.runModeUnsupportedMessage.asString(),
            confirmText = stringResource(R.string.common_i_got_it),
            dismissText = null,
            onConfirm = { viewModel.onDismissRunModeUnsupportedDialog() },
            onDismissRequest = { viewModel.onDismissRunModeUnsupportedDialog() }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.home_app_title),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(Routes.SETTINGS)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.home_cd_open_settings)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 8.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ScreenInfoCard(
                        screenWidth = width,
                        screenHeight = height,
                        resourceVersion = resourceVersion,
                        serviceStatusColor = uiState.serviceStatusColor,
                        serviceStatusText = uiState.serviceStatusText,
                        serviceStatusLoading = uiState.serviceStatusLoading
                    )
                }

                item {
                    RunModeCard(
                        runMode = uiState.runMode,
                        onRunModeChange = { viewModel.onRunModeChange(it) },
                        changeEnabled = viewModel.checkRunModeChangeEnabled()
                    )
                }

                item {
                    UpdateCard(viewModel = updateViewModel)
                }

                item {
                    PermissionCard(
                        permissionState = permissionState,
                        isGranting = uiState.isGranting,
                        onRequestRemoteAccess = { viewModel.onRequestRemoteAccess(context) },
                        onRequestOverlay = { viewModel.onRequestOverlay(context) },
                        onRequestStorage = { viewModel.onRequestStorage(context) },
                        onRequestBatteryWhitelist = { viewModel.onRequestBatteryWhitelist(context) },
                        onRequestAccessibility = { viewModel.onRequestAccessibility(context) },
                        onRequestNotification = { viewModel.onRequestNotification(context) }
                    )
                }

                if (uiState.runMode == RunMode.FOREGROUND) {
                    item {
                        ForegroundModeSection(
                            overlayControlMode = uiState.overlayControlMode,
                            isShowControlOverlay = uiState.isShowControlOverlay,
                            isLoading = uiState.isLoading,
                            onChangeTo16x9Resolution = { viewModel.onChangeTo16x9Resolution(context) },
                            onResetResolution = { viewModel.onResetResolution(context) },
                            onControlOverlayModeChanged = { viewModel.onControlOverlayModeChanged(it) },
                            onToggleOverlay = {
                                if (uiState.isShowControlOverlay) {
                                    Timber.d("关闭悬浮窗")
                                    viewModel.onStopControlOverlay()
                                } else {
                                    Timber.d("开启悬浮窗模式")
                                    viewModel.onStartControlOverlay()
                                }
                            }
                        )
                    }
                }

                if (
                    state !is RemoteServiceManager.ServiceState.Connected
                    && state !is RemoteServiceManager.ServiceState.Connecting
                    && permissionState.remoteAccessGranted
                ) {
                    item {
                        OutlinedButton(
                            onClick = { viewModel.onReloadServices() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = MaterialTheme.shapes.large,
                            enabled = !uiState.isLoading
                        ) {
                            Text(
                                text = stringResource(R.string.home_btn_reload_services),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                item {
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
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        ),
                        shape = MaterialTheme.shapes.large,
                        enabled = !uiState.isLoading
                    ) {
                        Text(
                            text = stringResource(R.string.home_btn_stop_all_services),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Shizuku/Sui 检测
        val skipShizukuCheck by appSettingsManager.skipShizukuCheck.collectAsStateWithLifecycle()
        var shizukuStatus by remember {
            mutableStateOf(ShizukuInstallHelper.checkStatus(context))
        }
        LifecycleResumeEffect(Unit) {
            shizukuStatus = ShizukuInstallHelper.checkStatus(context)
            onPauseOrDispose {}
        }
        if (permissionState.startupBackend == RemoteBackend.SHIZUKU && !skipShizukuCheck) {
            val skipScope = rememberCoroutineScope()
            when (shizukuStatus) {
                ShizukuInstallHelper.ShizukuStatus.NOT_INSTALLED -> {
                    AdaptiveTaskPromptDialog(
                        visible = true,
                        title = stringResource(R.string.dialog_shizuku_not_installed_title),
                        message = stringResource(R.string.dialog_shizuku_not_installed_message),
                        icon = Icons.Rounded.Warning,
                        confirmText = stringResource(R.string.dialog_shizuku_install_confirm),
                        onConfirm = { ShizukuInstallHelper.installShizuku(context) },
                        neutralText = if (permissionState.rootAvailable)
                            stringResource(R.string.dialog_shizuku_switch_to_root)
                        else null,
                        onNeutralClick = {
                            skipScope.launch { permissionManager.setStartupBackend(RemoteBackend.ROOT) }
                        },
                        dismissText = stringResource(R.string.dialog_shizuku_skip_check),
                        onDismissRequest = {
                            skipScope.launch { appSettingsManager.setSkipShizukuCheck(true) }
                        },
                        dismissOnOutsideClick = false
                    )
                }

                ShizukuInstallHelper.ShizukuStatus.APP_NOT_RUNNING -> {
                    AdaptiveTaskPromptDialog(
                        visible = true,
                        title = stringResource(R.string.dialog_shizuku_not_running_title),
                        message = stringResource(R.string.dialog_shizuku_not_running_message),
                        icon = Icons.Rounded.Build,
                        confirmText = stringResource(R.string.dialog_shizuku_open_app),
                        onConfirm = {
                            runCatching {
                                val intent =
                                    context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                if (intent != null) context.startActivity(intent)
                            }
                        },
                        neutralText = if (permissionState.rootAvailable)
                            stringResource(R.string.dialog_shizuku_switch_to_root)
                        else null,
                        onNeutralClick = {
                            skipScope.launch { permissionManager.setStartupBackend(RemoteBackend.ROOT) }
                        },
                        dismissText = stringResource(R.string.dialog_shizuku_skip_check),
                        onDismissRequest = {
                            skipScope.launch { appSettingsManager.setSkipShizukuCheck(true) }
                        },
                        dismissOnOutsideClick = false
                    )
                }

                ShizukuInstallHelper.ShizukuStatus.SUI_AVAILABLE -> {
                    AdaptiveTaskPromptDialog(
                        visible = true,
                        title = stringResource(R.string.dialog_sui_detected_title),
                        message = stringResource(R.string.dialog_sui_detected_message),
                        icon = Icons.Rounded.Info,
                        confirmText = stringResource(R.string.common_got_it),
                        onConfirm = {
                            skipScope.launch { appSettingsManager.setSkipShizukuCheck(true) }
                        },
                        dismissText = null,
                        onDismissRequest = {},
                        dismissOnOutsideClick = false
                    )
                }

                ShizukuInstallHelper.ShizukuStatus.READY -> {}
            }
        }
    }
}

@Composable
private fun ScreenInfoCard(
    screenWidth: Int,
    screenHeight: Int,
    resourceVersion: String,
    serviceStatusColor: StatusColorType,
    serviceStatusText: UiText,
    serviceStatusLoading: Boolean
) {
    val serviceStatusLabel = serviceStatusText.asString()
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
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
                    text = stringResource(R.string.home_screen_resolution),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$screenWidth × $screenHeight",
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
                    text = stringResource(R.string.home_resource_version_label),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val notInstalled = stringResource(R.string.home_resource_not_installed)
                Text(
                    text = resourceVersion.ifBlank { notInstalled },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (resourceVersion.isBlank())
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_service_status),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val statusColor = when (serviceStatusColor) {
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
                        text = serviceStatusLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor
                    )
                    if (serviceStatusLoading) {
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
}

@Composable
private fun RunModeCard(
    runMode: RunMode,
    onRunModeChange: (Boolean) -> Unit,
    changeEnabled: Boolean
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
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
                    text = stringResource(R.string.home_run_mode_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = context.runModeDisplayName(runMode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = runMode == RunMode.BACKGROUND,
                    enabled = changeEnabled,
                    onCheckedChange = onRunModeChange
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    grantedText: String = stringResource(R.string.home_permission_granted),
    ungrantedText: String = stringResource(R.string.home_permission_request),
    contentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor
        )
        TextButton(
            onClick = onClick,
            enabled = !granted && !isLoading,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(text = if (granted) grantedText else ungrantedText)
            }
        }
    }
}

@Composable
private fun PermissionCard(
    permissionState: PermissionState,
    isGranting: Boolean,
    onRequestRemoteAccess: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestStorage: () -> Unit,
    onRequestBatteryWhitelist: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestNotification: () -> Unit
) {
    val context = LocalContext.current
    var expandedPermissions by remember { mutableStateOf(false) }
    val contentColor = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                text = stringResource(R.string.home_permission_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            PermissionRow(
                title = context.remoteBackendPermissionLabel(permissionState.startupBackend),
                granted = permissionState.remoteAccessGranted,
                onClick = onRequestRemoteAccess,
                isLoading = isGranting,
                contentColor = contentColor
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedPermissions = !expandedPermissions }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expandedPermissions)
                        stringResource(R.string.home_permission_collapse)
                    else
                        stringResource(R.string.home_permission_expand),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
                Icon(
                    imageVector = if (expandedPermissions) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor.copy(alpha = 0.7f)
                )
            }

            AnimatedVisibility(visible = expandedPermissions) {
                Column {
                    PermissionRow(
                        title = stringResource(R.string.home_permission_overlay),
                        granted = permissionState.overlay,
                        onClick = onRequestOverlay,
                        contentColor = contentColor
                    )
                    PermissionRow(
                        title = stringResource(R.string.home_permission_storage),
                        granted = permissionState.storage,
                        onClick = onRequestStorage,
                        contentColor = contentColor
                    )
                    PermissionRow(
                        title = stringResource(R.string.home_permission_battery),
                        granted = permissionState.batteryWhitelist,
                        onClick = onRequestBatteryWhitelist,
                        grantedText = stringResource(R.string.home_permission_battery_added),
                        contentColor = contentColor
                    )
                    PermissionRow(
                        title = stringResource(R.string.home_permission_accessibility),
                        granted = permissionState.accessibility,
                        onClick = onRequestAccessibility,
                        ungrantedText = if (permissionState.remoteAccessGranted)
                            stringResource(R.string.home_permission_quick_grant)
                        else
                            stringResource(R.string.home_permission_request),
                        contentColor = contentColor
                    )
                    PermissionRow(
                        title = stringResource(R.string.home_permission_notification),
                        granted = permissionState.notification,
                        onClick = onRequestNotification,
                        contentColor = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ForegroundModeSection(
    overlayControlMode: OverlayControlMode,
    isShowControlOverlay: Boolean,
    isLoading: Boolean,
    onChangeTo16x9Resolution: () -> Unit,
    onResetResolution: () -> Unit,
    onControlOverlayModeChanged: (OverlayControlMode) -> Unit,
    onToggleOverlay: () -> Unit
) {
    val context = LocalContext.current
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = stringResource(R.string.home_resolution_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 2
                ) {
                    Button(
                        onClick = onChangeTo16x9Resolution,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.home_resolution_apply_16_9),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = onResetResolution,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.home_resolution_reset),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
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
                        text = stringResource(R.string.home_overlay_mode_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (overlayControlMode == OverlayControlMode.ACCESSIBILITY)
                            stringResource(R.string.home_overlay_mode_accessibility_desc)
                        else
                            stringResource(R.string.home_overlay_mode_floatball_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = context.overlayControlModeDisplayName(overlayControlMode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = overlayControlMode == OverlayControlMode.ACCESSIBILITY,
                        onCheckedChange = { isAccessibility ->
                            onControlOverlayModeChanged(
                                if (isAccessibility) OverlayControlMode.ACCESSIBILITY
                                else OverlayControlMode.FLOAT_BALL
                            )
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onToggleOverlay,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isShowControlOverlay)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = if (isShowControlOverlay)
                        stringResource(R.string.home_overlay_close)
                    else
                        stringResource(R.string.home_overlay_open),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
