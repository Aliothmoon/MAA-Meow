package com.aliothmoon.maameow.domain.service

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.aliothmoon.maameow.data.achievement.AchievementEvents
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LogExportService(
    private val context: Context,
    private val pathConfig: MaaPathConfig,
    private val appSettingsManager: AppSettingsManager,
    private val achievementRepository: AchievementRepository,
) {
    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }

    /** 导出日志 ZIP；失败或无日志返回 null。不删除 debug 源日志。 */
    suspend fun exportZip(): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(pathConfig.debugDir)
            if (!dir.exists()) {
                Timber.w("Debug directory does not exist")
                return@withContext null
            }

            val exportDir = File(dir, LogExportCollector.EXPORT_DIR_NAME)
            exportDir.mkdirs()
            cleanupOldExports(exportDir)

            val zipFileName = "maa_logs_${ZonedDateTime.now().format(DATE_FORMAT)}.zip"
            val zipFile = File(exportDir, zipFileName)

            val logFiles = LogExportCollector.collect(dir)
            if (logFiles.isEmpty()) {
                Timber.w("No log files found to export")
                return@withContext null
            }

            createZipFile(zipFile, logFiles, dir)

            Timber.i("Exported ${logFiles.size} log files to ${zipFile.absolutePath}")
            achievementRepository.report {
                event = AchievementEvents.LOG_EXPORTED
            }

            zipFile
        } catch (e: Exception) {
            Timber.e(e, "Failed to export logs")
            null
        }
    }

    suspend fun exportAllLogs(): Intent? = exportZip()?.let { createShareIntent(it) }

    /** 写入 [targetUri]；成功返回显示名，失败返回 null。 */
    suspend fun exportToUri(targetUri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        val zip = exportZip() ?: return@withContext null
        try {
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                zip.inputStream().use { it.copyTo(out) }
            } ?: return@withContext null
            queryDisplayName(targetUri) ?: zip.name
        } catch (e: Exception) {
            Timber.e(e, "Failed to export to uri: $targetUri")
            null
        }
    }

    private fun queryDisplayName(uri: android.net.Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to query DISPLAY_NAME for $uri")
            null
        }
    }

    private fun createZipFile(zipFile: File, logFiles: List<File>, baseDir: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            if (appSettingsManager.debugMode.value) {
                try {
                    val process = Runtime.getRuntime().exec("getprop")
                    zos.putNextEntry(ZipEntry("properties.txt"))
                    process.inputStream.use { input ->
                        input.copyTo(zos, bufferSize = 8192)
                    }
                    zos.closeEntry()
                    process.waitFor()
                } catch (e: Exception) {
                    Timber.w(e, "Failed to collect device properties")
                }
            }

            for (file in logFiles) {
                val relativePath = file.relativeTo(baseDir).path
                val entry = ZipEntry(relativePath)
                entry.time = file.lastModified()
                zos.putNextEntry(entry)

                FileInputStream(file).use { fis ->
                    fis.copyTo(zos, bufferSize = 8192)
                }
                zos.closeEntry()
            }
        }
    }

    private fun createShareIntent(zipFile: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, zipFile)

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "MaaMeow 日志导出")
            putExtra(
                Intent.EXTRA_TEXT,
                "MaaMeow 日志文件导出于 ${
                    ZonedDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (Z)"))
                }"
            )
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private fun cleanupOldExports(dir: File) {
        try {
            dir.listFiles { file ->
                file.isFile && file.name.startsWith("maa_logs_") && file.name.endsWith(".zip")
            }?.forEach { it.delete() }
        } catch (e: Exception) {
            Timber.w(e, "Failed to cleanup old exports")
        }
    }
}
