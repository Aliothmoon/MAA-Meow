package com.aliothmoon.maameow.domain.service

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
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
    private val appSettingsManager: AppSettingsManager
) {
    companion object {
        private const val EXPORT_DIR = "export"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }

    /**
     * 导出所有日志为 ZIP 文件并返回分享 Intent
     * @return 分享 Intent，失败返回 null
     */
    suspend fun exportAllLogs(): Intent? = withContext(Dispatchers.IO) {
        try {
            val dir = File(pathConfig.debugDir)
            if (!dir.exists()) {
                Timber.w("Debug directory does not exist")
                return@withContext null
            }

            // 创建导出目录
            val exportDir = File(dir, EXPORT_DIR)
            exportDir.mkdirs()

            // 清理旧的导出文件
            cleanupOldExports(exportDir)

            // 生成 ZIP 文件名
            val zipFileName = "maa_logs_${ZonedDateTime.now().format(DATE_FORMAT)}.zip"
            val zipFile = File(exportDir, zipFileName)

            // 收集所有日志文件
            val logFiles = collectAllLogFiles(dir)

            if (logFiles.isEmpty()) {
                Timber.w("No log files found to export")
                return@withContext null
            }

            // 打包为 ZIP
            createZipFile(zipFile, logFiles, dir)

            Timber.i("Exported ${logFiles.size} log files to ${zipFile.absolutePath}")

            createShareIntent(zipFile)
        } catch (e: Exception) {
            Timber.e(e, "Failed to export logs")
            null
        }
    }


    private fun collectAllLogFiles(debugDir: File): List<File> {
        val exportDir = File(debugDir, EXPORT_DIR)
        return debugDir.walkTopDown()
            .filter { it.isFile && !it.startsWith(exportDir) }
            .sortedByDescending { it.lastModified() }
            .toList()
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
                // 使用相对路径作为 ZIP 中的路径
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
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "MaaMeow 日志导出")
            putExtra(
                Intent.EXTRA_TEXT,
                "MaaMeow 日志文件导出于 ${
                    ZonedDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (Z)"))
                }"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 清理旧的导出文件（只保留最近一个）
     */
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
