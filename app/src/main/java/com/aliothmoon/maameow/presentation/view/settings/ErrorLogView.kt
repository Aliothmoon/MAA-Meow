package com.aliothmoon.maameow.presentation.view.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.viewmodel.ErrorLogViewModel
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ErrorLogView(
    navController: NavController,
    viewModel: ErrorLogViewModel = koinViewModel()
) {
    val logFiles by viewModel.logFiles.collectAsStateWithLifecycle()
    val selectedContent by viewModel.selectedContent.collectAsStateWithLifecycle()
    val selectedFileName by viewModel.selectedFileName.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val exportIntent by viewModel.exportIntent.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // 处理导出 Intent
    LaunchedEffect(exportIntent) {
        exportIntent?.let { intent ->
            context.startActivity(Intent.createChooser(intent, "导出日志"))
            viewModel.clearExportIntent()
        }
    }

    // 拦截系统返回键：详情页时先回到列表
    BackHandler(enabled = selectedContent != null) {
        viewModel.clearSelectedLog()
    }

    // 根据是否选中日志显示不同页面
    if (selectedContent != null) {
        ErrorLogDetailView(
            fileName = selectedFileName ?: "",
            content = selectedContent!!,
            onBack = { viewModel.clearSelectedLog() }
        )
    } else {
        ErrorLogFileListView(
            logFiles = logFiles,
            isLoading = isLoading,
            onFileClick = { viewModel.loadLogContent(it) },
            onCleanup = { viewModel.cleanupAll() },
            onExport = { viewModel.exportLogs() },
            onBack = { navController.navigateUp() }
        )
    }
}

@Composable
private fun ErrorLogFileListView(
    logFiles: List<ErrorLogViewModel.ErrorLogFile>,
    isLoading: Boolean,
    onFileClick: (ErrorLogViewModel.ErrorLogFile) -> Unit,
    onCleanup: () -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit
) {
    var showCleanupConfirm by remember { mutableStateOf(false) }

    // 清空确认弹窗
    if (showCleanupConfirm) {
        AlertDialog(
            onDismissRequest = { showCleanupConfirm = false },
            title = { Text("确认清空") },
            text = { Text("确定要清空所有错误日志吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    onCleanup()
                    showCleanupConfirm = false
                }) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanupConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "错误日志",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
                actions = {
                    IconButton(onClick = onExport) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "导出",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(onClick = { showCleanupConfirm = true }) {
                        Text("清空", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && logFiles.isEmpty()) {
                // 仅在列表为空且加载中时显示全屏 loading
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (logFiles.isEmpty()) {
                Text(
                    text = "暂无错误日志",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = logFiles,
                        key = { it.name }
                    ) { logFile ->
                        ErrorLogFileItem(
                            logFile = logFile,
                            onClick = { onFileClick(logFile) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorLogFileItem(
    logFile: ErrorLogViewModel.ErrorLogFile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = logFile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatFileSize(logFile.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTime(logFile.lastModified),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorLogDetailView(
    fileName: String,
    content: String,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = "日志详情",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack
            )
        }
    ) { paddingValues ->
        val lines = remember(content) { content.lines() }
        val horizontalScrollState = rememberScrollState()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .horizontalScroll(horizontalScrollState),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            itemsIndexed(
                items = lines,
            ) { index, line ->
                val color = getErrorLogLineColor(line)

                Text(
                    text = line,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    softWrap = false
                )
            }
        }
    }
}

private fun getErrorLogLineColor(line: String): Color {
    return when {
        line.contains("[ERROR]") -> Color(0xFFF44336)
        line.contains("[WARN]") -> Color(0xFFFF9800)
        line.contains("[ASSERT]") -> Color(0xFFB71C1C)
        else -> Color.Unspecified
    }
}

private fun formatTime(timestamp: Long): String {
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (Z)"))
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> "${size / (1024 * 1024)} MB"
    }
}
