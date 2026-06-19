package com.aliothmoon.maameow.domain.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class ToolboxExportFileType(val extension: String, val mimeType: String) {
    JSON("json", "application/json"),
    MARKDOWN("md", "text/markdown"),
    CSV("csv", "text/csv"),
}


class ToolboxExportService(
    private val context: Context,
) {
    companion object {
        private const val EXPORT_DIR = "export"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }

    fun makeFileName(prefix: String, fileType: ToolboxExportFileType): String =
        "${prefix}_${ZonedDateTime.now().format(DATE_FORMAT)}.${fileType.extension}"

    /** 写入缓存并返回分享 Intent；失败返回 null。悬浮窗宿主需自行追加 FLAG_ACTIVITY_NEW_TASK。 */
    suspend fun buildShareIntent(prefix: String, content: String, fileType: ToolboxExportFileType): Intent? =
        withContext(Dispatchers.IO) {
            try {
                val exportDir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
                cleanupOldExports(exportDir, prefix)
                val file = File(exportDir, makeFileName(prefix, fileType))
                file.writeText(content)
                createShareIntent(file, fileType)
            } catch (e: Exception) {
                Timber.e(e, "Failed to build share intent for %s", prefix)
                null
            }
        }

    suspend fun writeToUri(uri: Uri, content: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val out = context.contentResolver.openOutputStream(uri)
                if (out == null) {
                    Timber.e("openOutputStream returned null for %s", uri)
                    return@withContext false
                }
                out.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                true
            } catch (e: Exception) {
                Timber.e(e, "Failed to write export to uri")
                false
            }
        }

    private fun createShareIntent(file: File, fileType: ToolboxExportFileType): Intent {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = fileType.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun cleanupOldExports(dir: File, prefix: String) {
        try {
            dir.listFiles { f ->
                f.isFile && f.name.startsWith("${prefix}_")
            }?.forEach { it.delete() }
        } catch (e: Exception) {
            Timber.w(e, "Failed to cleanup old exports")
        }
    }
}
