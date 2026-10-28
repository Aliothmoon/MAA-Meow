package com.aliothmoon.maameow.schedule.service

import android.content.Context
import android.content.res.Configuration
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.domain.service.LaunchOutcome
import com.aliothmoon.maameow.domain.service.RunTelemetry
import com.aliothmoon.maameow.domain.service.UnlockAttempt
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
import java.io.IOException
import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class ScheduleTriggerLogger(
    private val pathConfig: MaaPathConfig,
    private val context: Context,
    private val telemetry: RunTelemetry,
) {

    private companion object {
        private const val TAG = "TriggerLogger"
        private const val LOG_DIR = "schedule"
        private const val LOG_PREFIX = "trigger_"
        private const val LOG_EXTENSION = ".log"
        private const val MAX_LOG_FILES = 100
        private val FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
    }

    private val json = JsonUtils.common

    /** Writer.nullWriter() 要 API 33 */
    private object NullWriter : Writer() {
        override fun write(cbuf: CharArray, off: Int, len: Int) = Unit
        override fun flush() = Unit
        override fun close() = Unit
    }

    private val logDir: File
        get() = File(pathConfig.debugDir, LOG_DIR).apply {
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
        val writer = try {
            BufferedWriter(FileWriter(file, true))
        } catch (e: IOException) {
            // 存储没就绪（ENOENT / ENOTCONN）不能把这次触发弄崩，日志落不了盘就算了
            Timber.e(e, "$TAG: Failed to create trigger log")
            BufferedWriter(NullWriter)
        }
        val session = Session(
            writer = writer,
            logFile = "$LOG_DIR/${file.name}",
            delayMs = (now - scheduledTimeMs).takeIf { scheduledTimeMs > 0L },
            runMode = runMode,
        )
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

    /** 归类用的文案键：取英文模板，不随界面语言变，也不带参数 */
    private fun reasonKey(text: UiText?): String? = when (text) {
        is UiText.Resource -> runCatching { englishResources.getString(text.resId) }.getOrNull()
        is UiText.Joined -> text.parts.firstNotNullOfOrNull(::reasonKey)
        else -> null
    }

    private val englishResources by lazy {
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        context.createConfigurationContext(configuration).resources
    }

    inner class Session internal constructor(
        private val writer: BufferedWriter,
        private val logFile: String,
        private val delayMs: Long?,
        private val runMode: String,
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

        /** @param unlock 唤醒解锁没成时才有，随结果交给遥测 */
        fun end(result: ExecutionResult, message: UiText? = null, unlock: UnlockAttempt? = null) {
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
            // 每一次触发的终局都从这里过，遥测只在这一处接
            telemetry.onLaunchFinished(
                LaunchOutcome(
                    result = result,
                    reason = reasonKey(message),
                    message = resolveMessage(message),
                    delayMs = delayMs,
                    runMode = runMode,
                    logFile = logFile,
                    unlock = unlock,
                )
            )
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
