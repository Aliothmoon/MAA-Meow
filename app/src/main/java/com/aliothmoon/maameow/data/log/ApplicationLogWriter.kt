package com.aliothmoon.maameow.data.log

import android.os.Build
import android.util.Log
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class ApplicationLogWriter(
    private val pathConfig: MaaPathConfig,
    appSettings: AppSettingsManager
) {

    private val isDebug = appSettings.debugMode.value

    companion object {
        private const val LOG_DIR_NAME = "error_logs"
        private const val CURRENT_LOG_FILE = "error.log"
        private const val MAX_FILE_SIZE = 2 * 1024 * 1024L // 2MB
        private const val MAX_LOG_FILES = 5
        private const val BUFFER_SIZE = 8 * 1024 // 8KB
    }

    private val logDir: File by lazy {
        File(pathConfig.debugDir, LOG_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private val currentLogFile: File
        get() = File(logDir, CURRENT_LOG_FILE)

    private var fileSize: Long = -1L

    private var outputStream: BufferedOutputStream? = null

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS (Z)")

    // 异步写入通道
    private val writeChannel = Channel<String>(Channel.BUFFERED)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    init {
        scope.launch {
            Timber.d("Starting log writer isDebug=$isDebug")
            if (isDebug) {
                writeAppInfoHeader()
            }
            for (entry in writeChannel) {
                writeToFile(entry)
            }
        }
    }

    private fun writeAppInfoHeader() {
        val header = buildString {
            append("\n")
            append("=".repeat(60))
            append("\n")
            append("=== Application Start ===\n")
            append("Time       : ${ZonedDateTime.now().format(dateTimeFormatter)}\n")
            append("App        : ${BuildConfig.APPLICATION_ID}\n")
            append("Version    : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
            append("Build Type : ${BuildConfig.BUILD_TYPE}\n")
            append("Device     : ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Android    : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("ABI        : ${Build.SUPPORTED_ABIS.joinToString()}\n")
            append("=".repeat(60))
            append("\n\n")
        }
        writeToFile(header)
    }

    fun submit(priority: Int, tag: String?, message: String, throwable: Throwable?) {
        try {
            val logEntry = formatLogEntry(priority, tag, message, throwable)
            writeChannel.trySend(logEntry)
        } catch (e: Exception) {
            Timber.tag("ApplicationLogWriter").e(e, "Failed to enqueue log entry")
        }
    }


    private fun writeToFile(logEntry: String) {
        try {
            rotateIfNeeded()

            val stream = getOutputStream()
            val bytes = logEntry.toByteArray(Charsets.UTF_8)
            stream.write(bytes)
            stream.flush()
            fileSize += bytes.size
        } catch (e: Exception) {
            Timber.tag("ApplicationLogWriter").e(e, "Failed to write error log")
            closeOutputStream()
        }
    }

    private fun getOutputStream(): BufferedOutputStream {
        var stream = outputStream
        if (stream == null) {
            stream = BufferedOutputStream(
                FileOutputStream(currentLogFile, true),
                BUFFER_SIZE
            )
            outputStream = stream
            fileSize = currentLogFile.length()
        }
        return stream
    }

    private fun closeOutputStream() {
        try {
            outputStream?.close()
        } catch (_: Exception) {
        }
        outputStream = null
        fileSize = -1L
    }


    private fun formatLogEntry(
        priority: Int,
        tag: String?,
        message: String,
        throwable: Throwable?
    ): String {
        val timestamp = ZonedDateTime.now().format(dateTimeFormatter)
        val level = when (priority) {
            Log.WARN -> "WARN"
            Log.ERROR -> "ERROR"
            Log.ASSERT -> "ASSERT"
            Log.DEBUG -> "DEBUG"
            Log.INFO -> "INFO"
            Log.VERBOSE -> "VERBOSE"
            else -> "UNKNOWN"
        }
        val tagStr = tag ?: "NoTag"

        val sb = StringBuilder(128)
        sb.append("[$timestamp] [$level] [$tagStr] $message\n")

        throwable?.let {
            sb.append(Log.getStackTraceString(it))
            sb.append("\n")
        }

        return sb.toString()
    }

    /**
     * 检查文件大小并执行日志轮转
     */
    private fun rotateIfNeeded() {
        if (fileSize < 0) {
            if (!currentLogFile.exists()) return
            fileSize = currentLogFile.length()
        }

        if (fileSize < MAX_FILE_SIZE) {
            return
        }

        // 轮转前关闭当前流
        closeOutputStream()

        try {
            val oldestFile = File(logDir, "error.${MAX_LOG_FILES - 1}.log")
            if (oldestFile.exists()) {
                oldestFile.delete()
            }

            for (i in (MAX_LOG_FILES - 2) downTo 1) {
                val fromFile = File(logDir, "error.$i.log")
                val toFile = File(logDir, "error.${i + 1}.log")
                if (fromFile.exists()) {
                    fromFile.renameTo(toFile)
                }
            }

            currentLogFile.renameTo(File(logDir, "error.1.log"))
        } catch (e: Exception) {
            Timber.e(e, "Failed to rotate log files")
        }
    }

    fun getLogFiles(): List<File> {
        return try {
            logDir.listFiles()
                ?.filter { it.isFile && it.name.matches(Regex("error(\\.[0-9]+)?\\.log")) }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get log files")
            emptyList()
        }
    }

    /**
     * 清理所有错误日志文件
     */
    fun cleanupAll() {
        // 通过 channel 保证和写入操作串行
        scope.launch {
            closeOutputStream()
            try {
                getLogFiles().forEach { file ->
                    file.delete()
                }
                Timber.i("All error log files cleaned")
            } catch (e: Exception) {
                Timber.e(e, "Failed to cleanup error logs")
            }
        }
    }
}
