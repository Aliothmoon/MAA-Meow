package com.aliothmoon.maameow.data.log

import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TaskLogWriter(private val pathConfig: MaaPathConfig) {

    companion object {
        private const val TAG = "TaskLogWriter"
        private const val LOG_PREFIX = "meow_log_"
        private const val LOG_EXTENSION = ".log"
        private val FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }

    private val json = JsonUtils.common

    private val mutex = Mutex()
    private var currentLogFile: File? = null
    private var bufferedWriter: BufferedWriter? = null
    private var startTime: Long = 0

    private val logDir: File
        get() = File(pathConfig.debugDir, "gui").apply {
            if (!exists()) mkdirs()
        }

    /**
     * 开始新的日志会话
     * @param taskNames 任务名称列表
     * @return 是否成功
     */
    suspend fun startSession(taskNames: List<String>): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                // 关闭之前的会话
                closeWriterInternal()

                // 创建新日志文件
                startTime = System.currentTimeMillis()
                val fileName = "${LOG_PREFIX}${
                    Instant.ofEpochMilli(startTime).atZone(ZoneId.systemDefault())
                        .format(FILE_DATE_FORMAT)
                }_${taskNames.size}$LOG_EXTENSION"
                currentLogFile = File(logDir, fileName)

                Timber.i("$TAG: Starting new log session: ${currentLogFile?.absolutePath}")

                // 创建 BufferedWriter
                bufferedWriter = BufferedWriter(FileWriter(currentLogFile, true))

                // 写入 header
                val header = LogEntry.Header(
                    startTime = startTime,
                    tasks = taskNames
                )
                writeLine(json.encodeToString(header))

                true
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to start session")
                false
            }
        }
    }

    /**
     * 追加日志条目
     */
    suspend fun appendLog(logItem: LogItem) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (bufferedWriter == null) return@withContext

                val logEntry = LogEntry.Log(
                    time = logItem.id,  // 使用 id (毫秒时间戳) 作为持久化时间
                    level = logItem.level.name,
                    content = logItem.content
                )
                writeLine(json.encodeToString(logEntry))
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to append log")
            }
        }
    }

    /**
     * 结束日志会话
     * @param status 结束状态：COMPLETED, ERROR, STOPPED
     */
    suspend fun endSession(status: String = "COMPLETED") = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (bufferedWriter == null) return@withContext

                // 写入 footer
                val footer = LogEntry.Footer(
                    endTime = System.currentTimeMillis(),
                    status = status
                )
                writeLine(json.encodeToString(footer))

                Timber.i("$TAG: Session ended with status: $status")

                closeWriterInternal()
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to end session")
            }
        }
    }

    /**
     * 获取所有日志文件列表
     */
    suspend fun getLogFiles(): List<LogFileInfo> = withContext(Dispatchers.IO) {
        try {
            logDir.listFiles { file ->
                file.isFile && file.name.startsWith(LOG_PREFIX) && file.name.endsWith(LOG_EXTENSION)
            }?.map { file ->
                parseLogFileInfo(file)
            }?.sortedByDescending { it.startTime } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to get log files")
            emptyList()
        }
    }

    /**
     * 读取日志文件内容，解析为 LogEntry 列表
     */
    suspend fun readLogFile(fileName: String): List<LogEntry> = withContext(Dispatchers.IO) {
        try {
            val file = File(logDir, fileName)
            if (!file.exists()) return@withContext emptyList()

            file.readLines().mapNotNull { line ->
                try {
                    if (line.isBlank()) return@mapNotNull null
                    json.decodeFromString<LogEntry>(line)
                } catch (e: Exception) {
                    Timber.w("$TAG: Failed to parse line: $line")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to read log file")
            emptyList()
        }
    }

    /**
     * 读取日志文件原始内容
     */
    suspend fun readLogFileRaw(fileName: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(logDir, fileName)
            if (!file.exists()) return@withContext null
            file.readText()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to read log file raw")
            null
        }
    }

    /**
     * 删除日志文件
     */
    suspend fun deleteLogFile(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(logDir, fileName)
            if (file.exists()) {
                file.delete().also {
                    Timber.i("$TAG: Deleted log file: $fileName, result: $it")
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to delete log file")
            false
        }
    }

    /**
     * 清理过期日志（保留指定天数内的日志）
     */
    suspend fun cleanupOldLogs(daysToKeep: Int = 30): Int = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = System.currentTimeMillis() - daysToKeep * 24 * 60 * 60 * 1000L
            var deletedCount = 0

            logDir.listFiles { file ->
                file.isFile && file.name.startsWith(LOG_PREFIX) && file.name.endsWith(LOG_EXTENSION)
            }?.forEach { file ->
                val info = parseLogFileInfo(file)
                if (info.startTime < cutoffTime) {
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            }

            Timber.i("$TAG: Cleaned up $deletedCount old log files")
            deletedCount
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to cleanup old logs")
            0
        }
    }

    private fun writeLine(line: String) {
        bufferedWriter?.apply {
            write(line)
            newLine()
            flush()
        }
    }

    private fun closeWriterInternal() {
        try {
            bufferedWriter?.close()
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Error closing writer")
        }
        bufferedWriter = null
        currentLogFile = null
    }

    /**
     * 解析文件名获取 LogFileInfo
     * 文件名格式：maa_log_20260121_143052_3.log
     */
    private fun parseLogFileInfo(file: File): LogFileInfo {
        val fileName = file.name
        var startTime = file.lastModified()
        var taskCount = 0

        // 解析文件名
        try {
            val namePart = fileName.removePrefix(LOG_PREFIX).removeSuffix(LOG_EXTENSION)
            val parts = namePart.split("_")
            if (parts.size >= 3) {
                val dateStr = "${parts[0]}_${parts[1]}"
                startTime =
                    LocalDateTime.parse(dateStr, FILE_DATE_FORMAT).atZone(ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                taskCount = parts.getOrNull(2)?.toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
            Timber.w("$TAG: Failed to parse filename: $fileName")
        }

        return LogFileInfo(
            fileName = fileName,
            filePath = file.absolutePath,
            startTime = startTime,
            fileSize = file.length(),
            taskCount = taskCount
        )
    }
}
