package com.aliothmoon.maameow.domain.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager
import androidx.core.content.FileProvider
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.achievement.AchievementEvents
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.MaaCoreVersion
import com.aliothmoon.maameow.manager.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LogExportService(
    private val context: Context,
    private val pathConfig: MaaPathConfig,
    private val appSettingsManager: AppSettingsManager,
    private val taskChainState: TaskChainState,
    private val achievementRepository: AchievementRepository,
) {
    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        private val INFO_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS (Z)")
    }

    /** 导出日志 ZIP；失败返回 null。无日志时仍生成仅含 properties/device_info 的包。不删除 debug 源日志。 */
    suspend fun exportZip(): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(pathConfig.debugDir)
            val exportDir = File(dir, LogExportCollector.EXPORT_DIR_NAME)
            exportDir.mkdirs()
            cleanupOldExports(exportDir)

            val zipFileName = "maa_logs_${ZonedDateTime.now().format(DATE_FORMAT)}.zip"
            val zipFile = File(exportDir, zipFileName)

            val logFiles = LogExportCollector.collect(dir)
            if (logFiles.isEmpty()) {
                Timber.w("No log files found, exporting device info only")
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

            try {
                zos.putNextEntry(ZipEntry("device_info.txt"))
                zos.write(buildDeviceInfo().toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            } catch (e: Exception) {
                Timber.w(e, "Failed to collect device info")
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

    /** 导出时采集的设备与运行环境快照，用于 issue 排障 */
    private fun buildDeviceInfo(): String = buildString {
        val line = "=".repeat(60)
        append(line).append("\n")
        append("=== MaaMeow Device & App Info ===\n")
        append("Export Time : ${ZonedDateTime.now().format(INFO_TIME_FORMAT)}\n")
        append("App         : ${BuildConfig.APPLICATION_ID}\n")
        append("Version     : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
        append("Build Type  : ${BuildConfig.BUILD_TYPE}\n")
        append("Device      : ${Build.MANUFACTURER} ${Build.MODEL}\n")
        append("Android     : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        append("Security    : ${Build.VERSION.SECURITY_PATCH}\n")
        append("ABI         : ${Build.SUPPORTED_ABIS.joinToString()}\n")
        append("--- MAA ---\n")
        append("Core        : ${MaaCoreVersion.current.ifBlank { "unknown" }}\n")
        append("Resource    : ${pathConfig.readDiskResourceVersion() ?: "none"}\n")
        append("Client      : ${taskChainState.clientType}\n")
        append("Game        : $gameVersionInfo\n")
        append(
            "Run Mode    : ${appSettingsManager.runMode.value} / " +
                    "${appSettingsManager.backgroundResolution.value} / " +
                    "forceFullscreen=${appSettingsManager.forceFullscreenOnVirtualDisplay.value}\n"
        )
        append("--- Backend ---\n")
        append("Startup     : ${appSettingsManager.startupBackend.value.display}\n")
        append("Shizuku     : $shizukuStatus\n")
        append("--- Device ---\n")
        append("Screen      : $screenInfo (density ${context.resources.displayMetrics.densityDpi}dpi)\n")
        append("RAM         : ${memoryInfo}\n")
        append("Storage     : $storageInfo\n")
        append("Battery Opt : $batteryOptimized\n")
        append("SELinux     : $selinuxMode\n")
        append(line).append("\n")
    }

    private val gameVersionInfo: String
        get() = runCatching {
            val pkg = Packages[taskChainState.clientType] ?: return@runCatching "unknown package"
            val info = context.packageManager.getPackageInfo(pkg, 0)
            "$pkg ${info.versionName ?: "?"}"
        }.getOrElse { "${Packages[taskChainState.clientType] ?: "?"} not installed" }

    private val shizukuStatus: String
        get() = runCatching {
            if (!ShizukuManager.isShizukuAvailable()) return@runCatching "unavailable"
            val granted = if (ShizukuManager.isGranted()) "granted" else "not granted"
            val api = runCatching { Shizuku.getVersion() }.getOrNull()
            val uid = runCatching { Shizuku.getUid() }.getOrNull()
            val identity = if (uid == 0) "root" else "adb"
            "available, $granted, API $api, uid $uid ($identity)"
        }.getOrElse { "unknown" }

    private val screenInfo: String
        get() = runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
            } else {
                val size = Point().also(wm.defaultDisplay::getRealSize)
                size.x to size.y
            }
            @Suppress("DEPRECATION")
            val refresh = wm.defaultDisplay.refreshRate
            "$w x $h @ ${"%.0f".format(Locale.US, refresh)}Hz"
        }.getOrElse { "unknown" }

    private val memoryInfo: String
        get() = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            "${formatGb(mi.totalMem)} total, ${formatGb(mi.availMem)} free"
        }.getOrElse { "unknown" }

    private val storageInfo: String
        get() = runCatching {
            val dir = File(pathConfig.rootDir)
            "${formatGb(dir.usableSpace)} usable / ${formatGb(dir.totalSpace)} total"
        }.getOrElse { "unknown" }

    private val batteryOptimized: String
        get() = runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
                "ignored (app exempt)"
            } else {
                "NOT ignored (schedule may be killed)"
            }
        }.getOrElse { "unknown" }

    private val selinuxMode: String
        get() = runCatching {
            when (File("/sys/fs/selinux/enforce").readText().trim()) {
                "1" -> "enforcing"
                "0" -> "permissive"
                else -> "unknown"
            }
        }.getOrDefault("unknown")

    private fun formatGb(bytes: Long): String =
        "%.1f GB".format(Locale.US, bytes / 1024f / 1024f / 1024f)

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
