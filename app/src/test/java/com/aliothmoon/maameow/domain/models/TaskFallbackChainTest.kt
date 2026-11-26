package com.aliothmoon.maameow.domain.models

import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.utils.i18n.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskFallbackChainTest {

    private fun candidate(
        name: String,
        logsBefore: List<Pair<UiText, LogLevel>> = emptyList(),
    ) = TaskCandidate(
        type = MaaTaskType.FIGHT,
        params = """{"stage":"$name"}""",
        logName = UiText.Dynamic(name),
        dropTarget = DropTarget(
            dropId = "30011", dropCount = 100, stage = name,
            medicine = 0, stone = 0, series = 1, logLabel = name,
        ),
        logsBefore = logsBefore,
        logOnSuccess = UiText.Dynamic("$name 库存不足") to LogLevel.TRACE,
    )

    private fun trace(text: String) = UiText.Dynamic(text) to LogLevel.TRACE

    private class Recorder {
        val logs = mutableListOf<String>()
        val attempted = mutableListOf<String>()
        val failed = mutableListOf<String>()
    }

    private fun TaskFallbackChain.run(
        rec: Recorder,
        accept: (TaskCandidate) -> Boolean,
    ) = appendFirstSuccessful(
        log = { text, _ -> rec.logs += (text as UiText.Dynamic).value },
        append = { c ->
            rec.attempted += (c.logName as UiText.Dynamic).value
            if (accept(c)) rec.attempted.size else 0
        },
        onAppendFailed = { c -> rec.failed += (c.logName as UiText.Dynamic).value },
    )

    @Test
    fun secondCandidateWins_afterCoreRejectsTheFirst() {
        val chain = TaskFallbackChain(
            candidates = listOf(
                candidate("4-4"),
                candidate("5-5", logsBefore = listOf(trace("#3 库存足够"))),
            ),
            logsWhenExhausted = listOf(trace("#5 缺少关卡")),
        )
        val rec = Recorder()

        val hit = chain.run(rec) { (it.logName as UiText.Dynamic).value == "5-5" }

        assertEquals("5-5", (hit!!.second.logName as UiText.Dynamic).value)
        assertEquals(2, hit.first)
        assertEquals(listOf("4-4", "5-5"), rec.attempted)
        assertEquals(listOf("4-4"), rec.failed)
        assertEquals(listOf("#3 库存足够", "5-5 库存不足"), rec.logs)
    }

    @Test
    fun everyCandidateRejected_flushesTrailingSkipLogs() {
        val chain = TaskFallbackChain(
            candidates = listOf(candidate("4-4")),
            logsWhenExhausted = listOf(trace("#3 缺少关卡")),
        )
        val rec = Recorder()

        assertNull(chain.run(rec) { false })

        assertEquals(listOf("4-4"), rec.attempted)
        assertEquals(listOf("4-4"), rec.failed)
        assertEquals(listOf("#3 缺少关卡"), rec.logs)
    }

    @Test
    fun noCandidates_stillFlushesTrailingSkipLogs() {
        val chain = TaskFallbackChain(logsWhenExhausted = listOf(trace("#2 缺少关卡")))
        val rec = Recorder()

        assertNull(chain.run(rec) { true })

        assertEquals(emptyList<String>(), rec.attempted)
        assertEquals(listOf("#2 缺少关卡"), rec.logs)
    }

    @Test
    fun firstCandidateWins_skipsTheRest() {
        val chain = TaskFallbackChain(
            candidates = listOf(candidate("4-4"), candidate("5-5")),
            logsWhenExhausted = listOf(trace("尾部")),
        )
        val rec = Recorder()

        val hit = chain.run(rec) { true }

        assertEquals("4-4", (hit!!.second.logName as UiText.Dynamic).value)
        assertEquals(listOf("4-4"), rec.attempted)
        assertEquals(listOf("4-4 库存不足"), rec.logs)
    }
}
