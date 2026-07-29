package com.aliothmoon.maameow.presentation.components

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.service.LogExportService
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 日志导出统一控制器：管理 BottomSheet 显隐、SAF 文件选择、分享 Intent、
 * 重复点击保护与 Toast 反馈。调用方只需维护 [sheetVisible] 状态。
 *
 * 必须在 Composable 顶层无条件调用，保证 launcher 注册稳定。
 */
@Composable
fun LogExportController(
    sheetVisible: Boolean,
    onSheetDismiss: () -> Unit,
    logExportService: LogExportService = koinInject(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    val chooserTitle = stringResource(R.string.settings_log_export_chooser_title)
    val inProgressText = stringResource(R.string.log_export_in_progress)

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            toastMessage = null
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) {
            isExporting = false
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            val name = logExportService.exportToUri(uri)
            isExporting = false
            toastMessage = if (name != null) {
                context.getString(R.string.log_export_save_success, name)
            } else {
                context.getString(R.string.log_export_failed)
            }
        }
    }

    if (!sheetVisible) return

    LogExportBottomSheet(
        onDismiss = onSheetDismiss,
        onSave = {
            onSheetDismiss()
            if (isExporting) return@LogExportBottomSheet
            isExporting = true
            toastMessage = inProgressText
            saveLauncher.launch(makeExportZipName())
        },
        onShare = {
            onSheetDismiss()
            if (isExporting) return@LogExportBottomSheet
            isExporting = true
            coroutineScope.launch {
                val intent = logExportService.exportAllLogs()
                isExporting = false
                if (intent != null) {
                    context.startActivity(Intent.createChooser(intent, chooserTitle))
                } else {
                    toastMessage = context.getString(R.string.log_export_failed)
                }
            }
        },
    )
}

private fun makeExportZipName(): String =
    "maa_logs_${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}.zip"
