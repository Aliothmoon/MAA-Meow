package com.aliothmoon.maameow.presentation.view.panel

import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.data.model.LogKind
import com.aliothmoon.maameow.data.model.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogGroupsTest {

    private fun log(content: String, level: LogLevel = LogLevel.INFO, kind: LogKind = LogKind.NORMAL, task: String? = null) =
        LogItem(content = content, level = level, kind = kind, task = task)

    @Test
    fun splitsByTaskChain() {
        val groups = groupLogs(
            listOf(
                log("开始执行任务"),
                log("开始任务:A", LogLevel.TRACE, LogKind.TASK_START, "A"),
                log("a1"),
                log("帧率低", LogLevel.WARNING),
                log("完成任务:A", LogLevel.SUCCESS, LogKind.TASK_DONE, "A"),
                log("A 收尾补充"),
                log("开始任务:B", LogLevel.TRACE, LogKind.TASK_START, "B"),
                log("任务出错:B", LogLevel.ERROR, LogKind.TASK_FAILED, "B"),
                log("全部完成", LogLevel.SUCCESS, LogKind.SUMMARY),
                log("理智", LogLevel.MESSAGE, LogKind.SUMMARY),
                log("之后的散行"),
            )
        )

        assertEquals(
            listOf(LogGroupKind.PLAIN, LogGroupKind.TASK, LogGroupKind.TASK, LogGroupKind.SUMMARY, LogGroupKind.PLAIN),
            groups.map { it.kind }
        )
        val a = groups[1]
        assertEquals("A", a.start?.task)
        assertEquals(listOf("a1", "帧率低", "A 收尾补充"), a.lines.map { it.content })
        assertEquals(1, a.warnings)
        assertEquals(LogTaskStatus.DONE, a.status(running = false, isLast = false))

        val b = groups[2]
        assertEquals(listOf("任务出错:B"), b.lines.map { it.content })
        assertEquals(LogTaskStatus.FAILED, b.status(running = true, isLast = false))

        assertEquals(2, groups[3].lines.size)
    }

    @Test
    fun unfinishedTaskStatusFollowsRunState() {
        val group = groupLogs(
            listOf(
                log("开始任务:A", LogLevel.TRACE, LogKind.TASK_START, "A"),
                log("a1"),
            )
        ).single()
        assertNull(group.end)
        assertEquals(LogTaskStatus.RUNNING, group.status(running = true, isLast = true))
        assertEquals(LogTaskStatus.INTERRUPTED, group.status(running = false, isLast = true))
    }

    @Test
    fun orphanEndMarkerStaysVisible() {
        // 起点被日志上限挤掉时，结束条目不能丢
        val group = groupLogs(listOf(log("完成任务:A", LogLevel.SUCCESS, LogKind.TASK_DONE, "A"))).single()
        assertEquals(LogGroupKind.PLAIN, group.kind)
        assertEquals(1, group.lines.size)
    }

    @Test
    fun formatsElapsed() {
        assertEquals("48s", formatElapsed(48_900))
        assertEquals("1m 30s", formatElapsed(90_860))
        assertEquals("11m 8s", formatElapsed(668_000))
        assertEquals("1h 2m", formatElapsed(3_725_000))
    }
}
