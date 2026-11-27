package com.aliothmoon.maameow.data.log

import com.aliothmoon.maameow.domain.service.GameExitCause
import com.aliothmoon.maameow.domain.service.GameExitCause.Kind
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
    private const val REASON_SIGNALED = 2
    private const val REASON_LOW_MEMORY = 3
    private const val REASON_CRASH = 4
    private const val REASON_CRASH_NATIVE = 5
    private const val REASON_ANR = 6
    private const val REASON_INITIALIZATION_FAILURE = 7
    private const val REASON_PERMISSION_CHANGE = 8
    private const val REASON_EXCESSIVE_RESOURCE_USAGE = 9
    private const val REASON_USER_REQUESTED = 10
    private const val REASON_OTHER = 13
    private const val REASON_FREEZER = 14
    private const val REASON_PACKAGE_STATE_CHANGE = 15
    private const val REASON_PACKAGE_UPDATED = 16

    private const val SIGKILL = 9
    private const val MAX_TAG_DESC = 80

    private val FORCE_STOP_BY_CALLER = Regex(""" due to (?:from (?:pid |process:)|stop by )(\d+)""")
    private val STOP_PREFIX = Regex("""^(?:\[[^\]]+] )?stop \S+ due to """)

    // 记在 USER_REQUESTED 下的厂商后台查杀
    private val OEM_BACKGROUND_KILL_MARKERS = listOf(
        "com.vivo.abe",
        "com.vivo.pem",
        "forcestoppackagebyuid 1000",
        "the system laoding is too high",
        "the system loading is too high",
        "com.miui.powerkeeper",
        "miuimemoryservice",
        "autopowerkill",
    )

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
        // forceStopPackage 记成 "stop <包> due to from pid N"（MIUI: from process:N，vivo: stop by N），任务启动和重启游戏都走它
        subject == ExitSubject.GAME && record.reason == REASON_USER_REQUESTED &&
                record.description?.let(FORCE_STOP_BY_CALLER::containsMatchIn) == true -> true

        else -> false
    }

    /** 调用方是本次提权进程的强停是 MaaMeow 自己停的，不算死因 */
    fun explain(record: ProcessExitRecord, servicePid: Int?): GameExitCause? {
        val desc = record.description.orEmpty()
        val kind = when (record.reason) {
            REASON_LOW_MEMORY -> Kind.LOW_MEMORY
            // 不经 AMS 的 SIGKILL 多是厂商守护进程直接杀，其他信号是崩了
            REASON_SIGNALED -> if (record.status == SIGKILL) Kind.BACKGROUND_KILL else Kind.CRASH
            REASON_OTHER, REASON_EXCESSIVE_RESOURCE_USAGE, REASON_FREEZER -> Kind.BACKGROUND_KILL
            REASON_USER_REQUESTED -> when {
                servicePid != null && FORCE_STOP_BY_CALLER.find(desc)?.groupValues?.get(1)?.toIntOrNull() == servicePid -> return null
                "installPackageLI" in desc -> Kind.PACKAGE_CHANGE
                isOemBackgroundKill(desc) -> Kind.BACKGROUND_KILL
                else -> Kind.FORCE_STOPPED
            }

            REASON_CRASH, REASON_CRASH_NATIVE, REASON_ANR, REASON_INITIALIZATION_FAILURE -> Kind.CRASH
            REASON_EXIT_SELF -> Kind.EXIT_SELF
            REASON_PERMISSION_CHANGE, REASON_PACKAGE_STATE_CHANGE, REASON_PACKAGE_UPDATED -> Kind.PACKAGE_CHANGE
            else -> Kind.OTHER
        }
        return GameExitCause(kind, record.timestamp, tag(record))
    }

    private fun isOemBackgroundKill(desc: String): Boolean {
        if (desc.isEmpty()) return false
        val lower = desc.lowercase(Locale.ROOT)
        return OEM_BACKGROUND_KILL_MARKERS.any { it in lower }
    }

    private fun tag(record: ProcessExitRecord): String = buildList {
        val reason = record.reasonName.ifEmpty { "reason ${record.reason}" }
        add(if (record.reason == REASON_EXIT_SELF || record.reason == REASON_SIGNALED) "$reason ${record.status}" else reason)
        if (record.detail != 0) add(record.detailName.ifEmpty { "detail ${record.detail}" })
        record.description?.lineSequence()?.firstOrNull()?.replace(STOP_PREFIX, "")?.trim()
            ?.takeIf { it.isNotEmpty() }?.let { add(it.take(MAX_TAG_DESC)) }
    }.joinToString(", ")

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
