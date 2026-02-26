package com.aliothmoon.maameow.presentation.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aliothmoon.maameow.data.model.update.UpdateCheckResult
import com.aliothmoon.maameow.data.model.update.UpdateInfo
import com.aliothmoon.maameow.data.model.update.UpdateProcessState
import com.aliothmoon.maameow.data.model.update.UpdateSource
import com.aliothmoon.maameow.presentation.viewmodel.UpdateViewModel
import dev.jeziellago.compose.markdowntext.MarkdownText

/**
 * 更新管理卡片
 * 整合应用更新和资源更新功能
 */

@Composable
fun UpdateCard(
    viewModel: UpdateViewModel = viewModel()
) {
    val resourceUpdateState by viewModel.resourceUpdateState.collectAsState()
    val appUpdateState by viewModel.appUpdateState.collectAsState()
    val resIsChecking by viewModel.resourceChecking.collectAsState()
    val appIsChecking by viewModel.appChecking.collectAsState()
    val resourceCheckResult by viewModel.resourceCheckResult.collectAsState()
    val appCheckResult by viewModel.appCheckResult.collectAsState()
    val updateSource by viewModel.updateSource.collectAsState()
    val mirrorChyanCdk by viewModel.mirrorChyanCdk.collectAsState()
    val context = LocalContext.current

    var resourceErrorMessage by remember { mutableStateOf<String?>(null) }
    var appErrorMessage by remember { mutableStateOf<String?>(null) }

    // ==================== 资源检查结果处理 ====================

    LaunchedEffect(resourceCheckResult) {
        when (val result = resourceCheckResult) {
            is UpdateCheckResult.UpToDate -> {
                Toast.makeText(context, "资源已是最新版本", Toast.LENGTH_SHORT).show()
                viewModel.dismissResourceCheckResult()
            }

            is UpdateCheckResult.Error -> {
                resourceErrorMessage = "检查资源更新失败: ${result.error.message}"
                viewModel.dismissResourceCheckResult()
            }

            else -> {}
        }
    }

    // ==================== 应用检查结果处理 ====================

    LaunchedEffect(appCheckResult) {
        when (val result = appCheckResult) {
            is UpdateCheckResult.UpToDate -> {
                Toast.makeText(context, "应用已是最新版本", Toast.LENGTH_SHORT).show()
                viewModel.dismissAppCheckResult()
            }

            is UpdateCheckResult.Error -> {
                appErrorMessage = "检查应用更新失败: ${result.error.message}"
                viewModel.dismissAppCheckResult()
            }

            else -> {}
        }
    }

    // ==================== 下载过程状态处理 ====================

    LaunchedEffect(resourceUpdateState) {
        when (val state = resourceUpdateState) {
            is UpdateProcessState.Failed -> {
                resourceErrorMessage = "资源更新失败: ${state.error.message}"
            }

            is UpdateProcessState.Success -> {
                Toast.makeText(context, "资源更新完成", Toast.LENGTH_SHORT).show()
                viewModel.reset()
            }

            else -> {}
        }
    }

    LaunchedEffect(appUpdateState) {
        when (val state = appUpdateState) {
            is UpdateProcessState.Failed -> {
                appErrorMessage = "${state.error.message}"
            }

            is UpdateProcessState.Success -> {
                Toast.makeText(context, "APK 下载完成，请完成安装", Toast.LENGTH_SHORT).show()
                viewModel.resetAppUpdate()
            }

            else -> {}
        }
    }

    // ==================== 弹窗 ====================

    // 资源更新确认弹窗
    (resourceCheckResult as? UpdateCheckResult.Available)?.info?.let { updateInfo ->
        UpdateConfirmDialog(
            updateInfo = updateInfo,
            onConfirm = {
                viewModel.dismissResourceCheckResult()
                viewModel.confirmResourceDownload()
            },
            onDismiss = {
                viewModel.dismissResourceCheckResult()
            }
        )
    }

    // 应用更新确认弹窗
    (appCheckResult as? UpdateCheckResult.Available)?.info?.let { updateInfo ->
        AppUpdateConfirmDialog(
            updateInfo = updateInfo,
            currentVersion = viewModel.currentAppVersion,
            onConfirm = {
                viewModel.dismissAppCheckResult()
                viewModel.confirmAppDownload()
            },
            onDismiss = {
                viewModel.dismissAppCheckResult()
            }
        )
    }

    // 资源更新错误弹窗
    resourceErrorMessage?.let { message ->
        ErrorDialog(
            message = message,
            onDismiss = {
                resourceErrorMessage = null
                viewModel.reset()
            }
        )
    }

    // 应用更新错误弹窗
    appErrorMessage?.let { message ->
        ErrorDialog(
            message = message,
            onDismiss = {
                appErrorMessage = null
                viewModel.resetAppUpdate()
            }
        )
    }

    // ==================== 状态标记 ====================

    val resIsDownloading = resourceUpdateState is UpdateProcessState.Downloading
    val resIsExtracting = resourceUpdateState is UpdateProcessState.Extracting
    val resIsInstalling = resourceUpdateState is UpdateProcessState.Installing
    val resIsUpdating = resIsDownloading || resIsExtracting || resIsInstalling

    val appIsDownloading = appUpdateState is UpdateProcessState.Downloading
    val appIsInstalling = appUpdateState is UpdateProcessState.Installing
    val appIsUpdating = appIsDownloading || appIsInstalling

    val anyUpdating = resIsUpdating || appIsUpdating

    // ==================== UI ====================

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "更新管理",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            // ========== 更新项列表 ==========
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // ---- 应用更新行 ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "App本体",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )

                    if (!appIsUpdating) {
                        OutlinedButton(
                            onClick = { viewModel.checkAppUpdate() },
                            enabled = !appIsChecking,
                            modifier = Modifier.defaultMinSize(minHeight = 1.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            if (appIsChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("检查更新", fontSize = 14.sp)
                            }
                        }
                    }
                }

                // 应用下载进度（动画展开/收起）
                AnimatedVisibility(
                    visible = appIsUpdating,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                ) {
                    AppUpdateProgress(appUpdateState)
                }

                // ---- 资源更新行 ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MAA资源",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )

                    if (!resIsUpdating) {
                        OutlinedButton(
                            onClick = { viewModel.checkResourceUpdate() },
                            enabled = !resIsChecking,
                            modifier = Modifier.defaultMinSize(minHeight = 1.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            if (resIsChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("检查更新", fontSize = 14.sp)
                            }
                        }
                    }
                }

                // 资源下载/解压进度（动画展开/收起）
                AnimatedVisibility(
                    visible = resIsUpdating,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                ) {
                    ResourceUpdateProgress(resourceUpdateState)
                }
            }

            // ========== 更新源选择（非更新中时显示） ==========
            if (!anyUpdating) {

                var showInfoSource by remember { mutableStateOf<UpdateSource?>(null) }
                val uriHandler = LocalUriHandler.current

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "下载源",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )

                    UpdateSourceButtonGroup(
                        selectedSource = updateSource,
                        onSourceSelected = { viewModel.setUpdateSource(it) },
                        onInfoClick = { showInfoSource = it }
                    )
                }

                // 更新源说明弹窗
                showInfoSource?.let { source ->
                    AlertDialog(
                        onDismissRequest = { showInfoSource = null },
                        title = {
                            Text(
                                text = "关于 ${source.displayName}",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                when (source) {
                                    UpdateSource.GITHUB -> Text("从 GitHub 官方仓库下载资源。适合网络环境良好的用户，可直接获取最新版本。")
                                    UpdateSource.MIRROR_CHYAN -> Text(
                                        buildAnnotatedString {
                                            withStyle(
                                                SpanStyle(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            ) {
                                                append("Mirror酱")
                                            }
                                            append("是独立的第三方加速下载服务，需要付费使用，并非「MAA」收费。\n\n")
                                            append("其运营成本由订阅收入支撑，部分收益将回馈项目开发者。欢迎订阅 CDK 享受高速下载，同时支持项目持续开发。\n\n")
                                            append("选择 Mirror酱 作为下载源时需要填写 CDK。")
                                        }
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        uriHandler.openUri(
                                            when (source) {
                                                UpdateSource.GITHUB -> "https://github.com/MaaAssistantArknights/MaaResource"
                                                UpdateSource.MIRROR_CHYAN -> "https://mirrorchyan.com"
                                            }
                                        )
                                        showInfoSource = null
                                    }
                                ) {
                                    Text("前往官网")
                                }
                            }
                        },
                        confirmButton = {}
                    )
                }

                // CDK 输入框（仅 Mirror酱 时显示）
                AnimatedVisibility(visible = updateSource == UpdateSource.MIRROR_CHYAN) {
                    CdkInputField(
                        cdk = mirrorChyanCdk,
                        onCdkChange = { viewModel.setMirrorChyanCdk(it) }
                    )
                }
            }
        }
    }
}


@Composable
private fun AppUpdateProgress(appUpdateState: UpdateProcessState) {
    when (appUpdateState) {
        is UpdateProcessState.Downloading -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "应用下载中 ${appUpdateState.progress}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = appUpdateState.speed,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
                LinearProgressIndicator(
                    progress = { appUpdateState.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        is UpdateProcessState.Installing -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "正在启动安装...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        else -> {}
    }
}

/**
 * 资源更新进度显示
 */
@Composable
private fun ResourceUpdateProgress(resourceUpdateState: UpdateProcessState) {
    when (resourceUpdateState) {
        is UpdateProcessState.Downloading -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "资源下载中 ${resourceUpdateState.progress}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = resourceUpdateState.speed,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
                LinearProgressIndicator(
                    progress = { resourceUpdateState.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        is UpdateProcessState.Extracting -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "解压中 ${resourceUpdateState.progress}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "${resourceUpdateState.current}/${resourceUpdateState.total}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
                LinearProgressIndicator(
                    progress = { resourceUpdateState.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        else -> {}
    }
}

/**
 * 更新源选择按钮组
 */
@Composable
private fun UpdateSourceButtonGroup(
    selectedSource: UpdateSource,
    onSourceSelected: (UpdateSource) -> Unit,
    onInfoClick: (UpdateSource) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UpdateSource.entries.forEach { source ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .selectable(
                        selected = source == selectedSource,
                        onClick = { onSourceSelected(source) },
                        role = Role.RadioButton
                    )
            ) {
                RadioButton(
                    selected = source == selectedSource,
                    onClick = null
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = source.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "关于${source.displayName}",
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(16.dp)
                        .clickable { onInfoClick(source) },
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 应用更新确认弹窗
 */
@Composable
private fun AppUpdateConfirmDialog(
    updateInfo: UpdateInfo,
    currentVersion: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "发现新版本",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                Text(
                    text = "$currentVersion → ${updateInfo.version}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!updateInfo.releaseNote.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    MarkdownText(
                        markdown = updateInfo.releaseNote,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text("立即更新")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("稍后再说")
            }
        }
    )
}

/**
 * CDK 输入框
 */
@Composable
private fun CdkInputField(
    cdk: String,
    onCdkChange: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var passwordVisible by remember { mutableStateOf(false) }

    var localCdk by remember { mutableStateOf(cdk) }
    LaunchedEffect(cdk) {
        if (cdk != localCdk) {
            localCdk = cdk
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = localCdk,
            onValueChange = { newValue ->
                localCdk = newValue
                onCdkChange(newValue)
            },
            label = { Text("Mirror酱 CDK") },
            placeholder = { Text("请输入 CDK") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                Row {
                    if (localCdk.isNotEmpty()) {
                        IconButton(onClick = {
                            localCdk = ""
                            onCdkChange("")
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "清空"
                            )
                        }
                    }
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Outlined.Lock else Icons.Filled.Lock,
                            contentDescription = if (passwordVisible) "隐藏" else "显示"
                        )
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(
            onClick = { uriHandler.openUri("https://mirrorchyan.com/") }
        ) {
            Text(
                text = "没有CDK? 立即订阅",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * 错误提示弹窗
 */
@Composable
private fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "更新失败",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}
