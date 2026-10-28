package com.aliothmoon.maameow.remote.internal

import com.aliothmoon.maameow.third.Ln
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 替被杀的上一个提权进程翻系统日志：lmkd、AMS、厂商守护进程杀进程只写 logcat
 *
 * 只留提到死者的行，同时段别的查杀只计条数，不把别的应用带出设备
 */
internal object SystemLogDump {

    private const val TAG = "SystemLogDump"
    private const val WAIT_SECONDS = 10L

    fun dump(sinceMs: Long, untilMs: Long, pid: Int, processPrefix: String): String {
        val since = String.format(Locale.US, "%d.%03d", sinceMs / 1000, sinceMs % 1000)
        val process = runCatching {
            ProcessBuilder("logcat", "-d", "-b", "default", "-b", "events", "-v", "epoch", "-t", since)
                .redirectErrorStream(true)
                .start()
        }.getOrElse {
            Ln.w("$TAG: start logcat failed", it)
            return "# logcat start failed: ${it.message}"
        }
        return try {
            val result = process.inputStream.bufferedReader().useLines { lines ->
                SystemLogFilter.filter(lines, pid, processPrefix, untilMs)
            }
            if (!process.waitFor(WAIT_SECONDS, TimeUnit.SECONDS)) process.destroy()
            result.render(sinceMs, untilMs)
        } catch (e: Exception) {
            Ln.w("$TAG: read logcat failed", e)
            process.destroy()
            "# logcat read failed: ${e.message}"
        }
    }
}

internal object SystemLogFilter {

    private const val MAX_LINES = 300
    private val KILL = Regex("kill", RegexOption.IGNORE_CASE)

    class Result(val kept: List<String>, val otherKills: Int, val scanned: Int) {
        fun render(sinceMs: Long, untilMs: Long): String = buildString {
            append("# window ").append(sinceMs).append("..").append(untilMs)
            append(", scanned ").append(scanned)
            append(", kept ").append(kept.size)
            append(", other kill lines ").append(otherKills).append('\n')
            kept.forEach { append(it).append('\n') }
        }
    }

    /** [lines] 须按时间排好，越过 [untilMs] 即停 */
    fun filter(
        lines: Sequence<String>,
        pid: Int,
        processPrefix: String,
        untilMs: Long,
        maxLines: Int = MAX_LINES,
    ): Result {
        // 整数匹配：1234 不命中 12345
        val pidToken = Regex("""\b$pid\b""")
        val kept = ArrayDeque<String>()
        var otherKills = 0
        var scanned = 0
        for (line in lines) {
            val at = epochMs(line)
            if (at != null && at > untilMs) break
            scanned++
            if (processPrefix in line || pidToken.containsMatchIn(line)) {
                kept.addLast(line)
                // 离死亡越近越要紧，超了丢最早的
                if (kept.size > maxLines) kept.removeFirst()
            } else if (KILL.containsMatchIn(line)) {
                otherKills++
            }
        }
        return Result(kept.toList(), otherKills, scanned)
    }

    /** `-v epoch` 行首是「秒.毫秒」，分隔线等解析不了的返回 null */
    internal fun epochMs(line: String): Long? {
        val token = line.trimStart().substringBefore(' ')
        val seconds = token.substringBefore('.').toLongOrNull() ?: return null
        val millis = token.substringAfter('.', "").take(3).padEnd(3, '0').toLongOrNull() ?: 0L
        return seconds * 1000 + millis
    }
}
