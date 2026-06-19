package com.aliothmoon.maameow.presentation.view.panel

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.service.ToolboxExportFileType
import com.aliothmoon.maameow.domain.service.ToolboxExportService
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 识别结果文件导出器。由宿主注入不同实现，面板不感知宿主：
 * - 全屏页面：[rememberSafToolboxFileExporter]（SAF）
 * - 悬浮窗：[rememberShareToolboxFileExporter]（ACTION_SEND）
 *
 * 为 null 时面板不显示「导出文件」入口，兜底避免在未提供宿主时崩溃。
 */
fun interface ToolboxFileExporter {
    fun export(
        fileNamePrefix: String,
        content: String,
        fileType: ToolboxExportFileType,
    )
}

val LocalToolboxFileExporter = staticCompositionLocalOf<ToolboxFileExporter?> { null }

/**
 * 悬浮窗用：chooser 需追加 FLAG_ACTIVITY_NEW_TASK（持悬浮窗权限，豁免后台 Activity 启动限制）。
 */
@Composable
fun rememberShareToolboxFileExporter(
    exportService: ToolboxExportService = koinInject(),
): ToolboxFileExporter {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chooserTitle = stringResource(R.string.toolbox_export_chooser_title)
    val failedMsg = stringResource(R.string.toolbox_export_file_failed)
    return remember(context, scope, exportService, chooserTitle, failedMsg) {
        ToolboxFileExporter { prefix, content, fileType ->
            scope.launch {
                val intent = exportService.buildShareIntent(prefix, content, fileType)
                if (intent != null) {
                    context.startActivity(
                        Intent.createChooser(intent, chooserTitle)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } else {
                    Toast.makeText(context, failedMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/**
 * 全屏页面用：SAF（CreateDocument）选保存位置后写入。须在 Activity 内调用。
 */
@Composable
fun rememberSafToolboxFileExporter(
    exportService: ToolboxExportService = koinInject(),
): ToolboxFileExporter {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val successMsg = stringResource(R.string.toolbox_export_file_success)
    val failedMsg = stringResource(R.string.toolbox_export_file_failed)
    var pendingContent by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val content = pendingContent
        pendingContent = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = exportService.writeToUri(uri, content)
            Toast.makeText(context, if (ok) successMsg else failedMsg, Toast.LENGTH_SHORT).show()
        }
    }
    return remember(launcher, exportService) {
        ToolboxFileExporter { prefix, content, fileType ->
            pendingContent = content
            launcher.launch(exportService.makeFileName(prefix, fileType))
        }
    }
}
