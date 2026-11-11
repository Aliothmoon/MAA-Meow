package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.runtime.Immutable
import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.data.model.LogKind
import com.aliothmoon.maameow.data.model.LogLevel

enum class LogGroupKind { PLAIN, TASK, SUMMARY }

enum class LogTaskStatus { RUNNING, DONE, FAILED, INTERRUPTED }

@Immutable
class LogGroup(
    val kind: LogGroupKind,
    val start: LogItem?,
    val end: LogItem?,
    val lines: List<LogItem>,
) {
    val id: Long = start?.id ?: lines.first().id
    val warnings: Int = lines.count { it.level == LogLevel.WARNING }

    fun status(running: Boolean, isLast: Boolean): LogTaskStatus = when {
        end?.kind == LogKind.TASK_DONE -> LogTaskStatus.DONE
        end != null -> LogTaskStatus.FAILED
        running && isLast -> LogTaskStatus.RUNNING
        else -> LogTaskStatus.INTERRUPTED
    }
}

/** 按任务链起止切分；首个任务前、摘要后的散行归入无标题分组，任务结束后的补充日志仍挂在该任务下 */
fun groupLogs(logs: List<LogItem>): List<LogGroup> {
    val groups = ArrayList<LogGroup>()
    var kind = LogGroupKind.PLAIN
    var start: LogItem? = null
    var end: LogItem? = null
    var lines = ArrayList<LogItem>()

    fun open(next: LogGroupKind, first: LogItem? = null) {
        if (start != null || lines.isNotEmpty()) groups += LogGroup(kind, start, end, lines)
        kind = next
        start = first
        end = null
        lines = ArrayList()
    }

    for (item in logs) {
        when (item.kind) {
            LogKind.TASK_START -> open(LogGroupKind.TASK, item)
            LogKind.TASK_DONE, LogKind.TASK_FAILED ->
                if (kind == LogGroupKind.TASK && end == null) {
                    end = item
                    // 出错原因要留在组内可见
                    if (item.kind == LogKind.TASK_FAILED) lines += item
                } else {
                    lines += item
                }

            LogKind.SUMMARY -> {
                if (kind != LogGroupKind.SUMMARY) open(LogGroupKind.SUMMARY)
                lines += item
            }

            LogKind.NORMAL -> {
                if (kind == LogGroupKind.SUMMARY) open(LogGroupKind.PLAIN)
                lines += item
            }
        }
    }
    open(LogGroupKind.PLAIN)
    return groups
}

fun formatElapsed(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}
