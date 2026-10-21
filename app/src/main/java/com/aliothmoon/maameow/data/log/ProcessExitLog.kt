package com.aliothmoon.maameow.data.log

import com.aliothmoon.maameow.remote.ProcessExitRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class ExitSubject(val label: String) { SELF("self"), GAME("game") }

/** 退出记录的去重、分级与格式化，纯函数 */
internal object ProcessExitLog {

    const val MAX_RECORDS = 16

    /** 首次没有水位时只回溯最近几条，覆盖「被杀后才升级到这版」 */
    const val FIRST_RUN_BACKFILL = 3

    // 对应 ApplicationExitInfo.REASON_*，平台常量要 API 30+ 才认
    private const val REASON_EXIT_SELF = 1
    private const val REASON_USER_REQUESTED = 10
    private const val REASON_PACKAGE_UPDATED = 16

    private val FORCE_STOP_BY_CALLER = Regex(""" due to from (pid |process:)\d+""")

    private val TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS", Locale.ROOT)

    private val IMPORTANCE = mapOf(
        100 to "FOREGROUND",
        125 to "FOREGROUND_SERVICE",
        200 to "VISIBLE",
        230 to "PERCEPTIBLE",
        300 to "SERVICE",
        325 to "TOP_SLEEPING",
        350 to "CANT_SAVE_STATE",
        400 to "CACHED",
        1000 to "GONE",
    )

    data class Watermark(val timestamp: Long, val pid: Int)

    private val ORDER = compareBy<ProcessExitRecord>({ it.timestamp }, { it.pid })

    /** 水位之后的按时间正序；按（时间, pid）比，同一毫秒的也不重复 */
    fun selectNew(records: List<ProcessExitRecord>, watermark: Watermark?): List<ProcessExitRecord> {
        val sorted = records.sortedWith(ORDER)
        if (watermark == null) return sorted.takeLast(FIRST_RUN_BACKFILL)
        return sorted.filter {
            it.timestamp > watermark.timestamp || (it.timestamp == watermark.timestamp && it.pid > watermark.pid)
        }
    }

    /** 预期内的退出只记 INFO：覆盖安装、正常退出、任务重启游戏 */
    fun isExpected(subject: ExitSubject, record: ProcessExitRecord): Boolean = when {
        record.reason == REASON_PACKAGE_UPDATED -> true
        // 14 以下覆盖安装没有 PACKAGE_UPDATED，记成 USER_REQUESTED "... due to installPackageLI"
        record.reason == REASON_USER_REQUESTED && record.description?.contains("installPackageLI") == true -> true
        record.reason == REASON_EXIT_SELF && record.status == 0 -> true
        // forceStopPackage 记成 "stop <包> due to from pid N"（MIUI 是 from process:N），任务启动和重启游戏都走它
        subject == ExitSubject.GAME && record.reason == REASON_USER_REQUESTED &&
                record.description?.let(FORCE_STOP_BY_CALLER::containsMatchIn) == true -> true

        else -> false
    }

    fun format(subject: ExitSubject, record: ProcessExitRecord, zone: ZoneId = ZoneId.systemDefault()): String =
        buildString {
            append("Process exit [").append(subject.label).append("] ").append(record.processName)
            append(" pid=").append(record.pid)
            append(" at ").append(TIME.format(Instant.ofEpochMilli(record.timestamp).atZone(zone)))
            append(" reason=").append(record.reasonName.ifEmpty { "?" }).append('(').append(record.reason).append(')')
            // 0 即 SUBREASON_UNKNOWN，没有细分就不写
            if (record.detail != 0) {
                append(" detail=").append(record.detailName.ifEmpty { "?" }).append('(').append(record.detail).append(')')
            }
            append(" status=").append(record.status)
            append(" importance=").append(IMPORTANCE[record.importance] ?: "?").append('(').append(record.importance).append(')')
            if (record.pssKb > 0) append(" pss=").append(mb(record.pssKb))
            if (record.rssKb > 0) append(" rss=").append(mb(record.rssKb))
            record.description?.takeIf { it.isNotBlank() }?.let { append(" desc=\"").append(it).append('"') }
        }

    private fun mb(kb: Long): String = String.format(Locale.ROOT, "%.1fMB", kb / 1024.0)
}
