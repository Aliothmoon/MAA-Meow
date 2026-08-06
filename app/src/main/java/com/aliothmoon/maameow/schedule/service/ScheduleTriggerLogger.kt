package com.aliothmoon.maameow.schedule.service

import android.content.Context
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.model.TriggerLogEntry
import com.aliothmoon.maameow.utils.JsonUtils
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.resolve
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

class ScheduleTriggerLogger(
    private val pathConfig: MaaPathConfig,
    private val context: Context,
) {

    private companion object {
        private const val TAG = "TriggerLogger"
        private const val LOG_PREFIX = "trigger_"
        private const val LOG_EXTENSION = ".log"
        private const val MAX_LOG_FILES = 100
        private val FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
    }

    private val json = JsonUtils.common

    private val logDir: File
        get() = File(pathConfig.debugDir, "schedule").apply {
            if (!exists()) mkdirs()
        }

    fun resolveMessage(text: UiText?): String? {
        if (text == null) return null
        return text.resolve(context).ifBlank { null }
    }

    /**
     * 打开一次触发会话并发会话各自写不同文件，互不影响
     */
    fun open(
        strategyId: String,
        strategyName: String,
        scheduledTimeMs: Long,
        runMode: String = "",
    ): Session {
        val now = System.currentTimeMillis()
        val timeStr = Instant.ofEpochMilli(now)
            .atZone(ZoneId.systemDefault())
            .format(FILE_DATE_FORMAT)
        val file = File(logDir, "$LOG_PREFIX$timeStr$LOG_EXTENSION")
        val writer = BufferedWriter(FileWriter(file, true))
        val session = Session(writer)
        try {
            session.writeEntry(
                TriggerLogEntry.Header(
                    strategyId = strategyId,
                    strategyName = strategyName,
                    scheduledTimeMs = scheduledTimeMs,
                    actualTimeMs = now,
                    runMode = runMode,
                ),
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to open trigger log")
            session.forceClose()
        }
        return session
    }


    fun writeClosed(
        strategyId: String,
        strategyName: String,
        scheduledTimeMs: Long,
        result: ExecutionResult,
        message: UiText? = null,
        runMode: String = "",
    ) {
        val session = open(strategyId, strategyName, scheduledTimeMs, runMode)
        if (message != null) {
            session.append(message)
        }
        session.end(result, message)
    }


    data class TriggerLogSummary(
        val fileName: String,
        val header: TriggerLogEntry.Header,
        val footer: TriggerLogEntry.Footer?,
    )

    suspend fun getLogSummaries(): List<TriggerLogSummary> = withContext(Dispatchers.IO) {
        try {
            logDir.listFiles { file ->
                file.isFile
                        && file.name.startsWith(LOG_PREFIX)
                        && file.name.endsWith(LOG_EXTENSION)
            }?.mapNotNull { file ->
                parseSummary(file)
            }?.sortedByDescending { it.header.actualTimeMs } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to list trigger logs")
            emptyList()
        }
    }

    suspend fun readLogFile(fileName: String): List<TriggerLogEntry> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(logDir, fileName)
                if (!file.exists()) return@withContext emptyList()
                file.readLines().mapNotNull { line ->
                    if (line.isBlank()) return@mapNotNull null
                    try {
                        json.decodeFromString<TriggerLogEntry>(line)
                    } catch (e: Exception) {
                        Timber.w(e, "$TAG: Failed to parse line: $line")
                        null
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to read trigger log: $fileName")
                emptyList()
            }
        }

    suspend fun deleteLog(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(logDir, fileName).delete()
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to delete trigger log: $fileName")
            false
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        try {
            logDir.listFiles { file ->
                file.isFile
                        && file.name.startsWith(LOG_PREFIX)
                        && file.name.endsWith(LOG_EXTENSION)
            }?.forEach { it.delete() }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to clear trigger logs")
        }
    }

    private fun parseSummary(file: File): TriggerLogSummary? {
        return try {
            val lines = file.readLines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return null
            val header = json.decodeFromString<TriggerLogEntry>(lines.first())
            if (header !is TriggerLogEntry.Header) return null
            val footer = lines.lastOrNull()?.let {
                try {
                    val entry = json.decodeFromString<TriggerLogEntry>(it)
                    entry as? TriggerLogEntry.Footer
                } catch (_: Exception) {
                    null
                }
            }
            TriggerLogSummary(
                fileName = file.name,
                header = header,
                footer = footer,
            )
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to parse summary: ${file.name}")
            null
        }
    }

    private fun purgeLogs() {
        try {
            val files = logDir.listFiles { file ->
                file.isFile
                        && file.name.startsWith(LOG_PREFIX)
                        && file.name.endsWith(LOG_EXTENSION)
            }?.sortedByDescending { it.lastModified() } ?: return
            if (files.size > MAX_LOG_FILES) {
                files.drop(MAX_LOG_FILES).forEach { it.delete() }
            }
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to cleanup old trigger logs")
        }
    }

    inner class Session internal constructor(
        private val writer: BufferedWriter,
    ) {
        private val closed = AtomicBoolean(false)

        fun append(text: UiText) {
            val msg = resolveMessage(text) ?: return
            writeEntry(
                TriggerLogEntry.Log(
                    time = System.currentTimeMillis(),
                    message = msg,
                ),
            )
        }

        fun end(result: ExecutionResult, message: UiText? = null) {
            if (!closed.compareAndSet(false, true)) return
            try {
                val footer: TriggerLogEntry = TriggerLogEntry.Footer(
                    time = System.currentTimeMillis(),
                    result = result,
                    message = resolveMessage(message),
                )
                synchronized(writer) {
                    writer.write(json.encodeToString(footer))
                    writer.newLine()
                    writer.flush()
                }
            } catch (e: Exception) {
                Timber.w(e, "$TAG: Failed to write footer")
            } finally {
                try {
                    writer.close()
                } catch (_: Exception) {
                }
                purgeLogs()
            }
        }

        internal fun writeEntry(entry: TriggerLogEntry) {
            if (closed.get()) return
            try {
                synchronized(writer) {
                    if (closed.get()) return
                    writer.write(json.encodeToString(entry))
                    writer.newLine()
                    writer.flush()
                }
            } catch (e: Exception) {
                Timber.w(e, "$TAG: Failed to write entry")
            }
        }

        internal fun forceClose() {
            closed.set(true)
            try {
                writer.close()
            } catch (_: Exception) {
            }
        }
    }
}
