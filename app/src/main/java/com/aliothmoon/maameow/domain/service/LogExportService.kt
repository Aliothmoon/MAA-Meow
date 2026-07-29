package com.aliothmoon.maameow.domain.service

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.aliothmoon.maameow.constant.LogConfig
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
    private val sessionLogger: MaaSessionLogger,
) {
    companion object {
        private const val EXPORT_DIR = "export"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }

    /**
     * 导出所有日志为 ZIP 文件，返回生成的 ZIP File。
     * 失败或无日志时返回 null。
     */
    suspend fun exportZip(): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(pathConfig.debugDir)
            if (!dir.exists()) {
                Timber.w("Debug directory does not exist")
                return@withContext null
            }

            val exportDir = File(dir, EXPORT_DIR)
            exportDir.mkdirs()

            cleanupOldExports(exportDir)

            val cleaned = cleanupBeforeExport(dir)
            Timber.i("Pre-export cleanup: removed $cleaned files")

            val zipFileName = "maa_logs_${ZonedDateTime.now().format(DATE_FORMAT)}.zip"
            val zipFile = File(exportDir, zipFileName)

            val logFiles = collectAllLogFiles(dir)
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

    /**
     * 导出所有日志为 ZIP 文件并返回分享 Intent
     * @return 分享 Intent，失败返回 null
     */
    suspend fun exportAllLogs(): Intent? = exportZip()?.let { createShareIntent(it) }

    /**
     * 把导出的 ZIP 写入用户指定的 [targetUri]（通常来自 SAF CreateDocument）。
     * @return 成功时返回文件显示名（查询 [android.provider.OpenableColumns.DISPLAY_NAME]），
     *         查询失败时回退到内部生成的 ZIP 文件名；写入失败返回 null
     */
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


    private fun collectAllLogFiles(debugDir: File): List<File> {
        val exportPath = File(debugDir, EXPORT_DIR).invariantSeparatorsPath
        val screenshotCutoff = System.currentTimeMillis() -
                LogConfig.EXPORT_SCREENSHOT_DAYS * 24L * 60 * 60 * 1000
        return debugDir.walkTopDown()
            .filter { it.isFile }
            .filter { !it.invariantSeparatorsPath.startsWith(exportPath) }
            .filter { file ->
                val rel = file.invariantSeparatorsPath
                when {
                    // screenshots 目录：只打包近 N 天的，允许 PNG/JPG
                    rel.contains("/screenshots/") ->
                        file.lastModified() >= screenshotCutoff
                    // logcat：无轮转，放宽单文件大小
                    rel.contains("/logcat/") ->
                        file.length() <= LogConfig.MAX_EXPORT_LOGCAT_FILE_SIZE
                    else ->
                        file.extension.lowercase() !in setOf("png", "jpg", "jpeg") &&
                                file.length() <= LogConfig.MAX_EXPORT_SINGLE_FILE_SIZE
                }
            }
            .groupBy { it.parentFile?.invariantSeparatorsPath ?: "" }
            .flatMap { (_, fs) ->
                val sample = fs.first()
                val rel = sample.invariantSeparatorsPath
                val limit = when {
                    rel.contains("/screenshots/") ->
                        LogConfig.MAX_EXPORT_FILES_PER_SCREENSHOT_DIR

                    rel.contains("/logcat/") ||
                            rel.contains("/gui/") ||
                            rel.contains("/schedule/") ||
                            rel.contains("/error_logs/") ||
                            rel.contains("/crash_logs/") ->
                        LogConfig.MAX_EXPORT_FILES_PER_LOG_DIR

                    else ->
                        LogConfig.MAX_EXPORT_FILES_PER_OTHER_DIR
                }
                fs.sortedByDescending { it.lastModified() }.take(limit)
            }
    }


    /**
     * 按重要性给文件打分，数字越小越优先入 ZIP（总量超限时优先保留）。
     */
    private fun exportPriority(file: File): Int {
        val p = file.invariantSeparatorsPath
        return when {
            file.name.startsWith("asst") -> 0
            p.contains("/error_logs/") -> 1
            p.contains("/logcat/") -> 2
            p.contains("/gui/") -> 3
            p.contains("/crash_logs/") -> 4
            p.contains("/schedule/") -> 5
            p.contains("/screenshots/") -> 6
            else -> 7
        }
    }


    private fun createZipFile(zipFile: File, logFiles: List<File>, baseDir: File) {
        // 按重要性优先 + 最新优先排序，确保总量超限时丢的是截图而不是核心日志
        val ordered = logFiles.sortedWith(
            compareBy({ exportPriority(it) }, { -it.lastModified() })
        )

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

            var totalSize = 0L
            for ((idx, file) in ordered.withIndex()) {
                if (totalSize >= LogConfig.MAX_EXPORT_TOTAL_SIZE) {
                    Timber.w("Export zip reached MAX_EXPORT_TOTAL_SIZE, skipping remaining ${ordered.size - idx} files")
                    break
                }
                totalSize += file.length()

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

    private suspend fun cleanupBeforeExport(debugDir: File): Int {
        var deleted = sessionLogger.cleanupOldLogs(LogConfig.MAX_TASK_LOG_DAYS)
        listOf("logcat", "screenshots", "crash_logs").forEach { sub ->
            deleted += cleanupDirByAge(File(debugDir, sub), LogConfig.EXPORT_CLEANUP_DAYS)
        }
        return deleted
    }

    private fun cleanupDirByAge(dir: File, daysToKeep: Int): Int {
        if (!dir.exists()) return 0
        val cutoff = System.currentTimeMillis() - daysToKeep * 24L * 60 * 60 * 1000
        return dir.walkTopDown()
            .filter { it.isFile && it.lastModified() < cutoff }
            .onEach { it.delete() }
            .count()
    }

    /**
     * 清理所有旧的导出 ZIP 文件
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
