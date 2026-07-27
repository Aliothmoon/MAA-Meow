package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.constant.LogConfig
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.log.LogEntry
import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.data.model.LogLevel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * [MaaSessionLogger] 位于 MaaCore 回调热路径的下游：每条回调都可能 [MaaSessionLogger.append]。
 *
 * 本测试锁住批量缓冲改造后必须保持不变的契约 —— 不丢条目、保持顺序、
 * 超上限时保留**最新**而非最旧、关键写入立即可见。
 */
class MaaSessionLoggerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var logger: MaaSessionLogger

    private val max = LogConfig.MAX_RUNTIME_LOG_COUNT

    @Before
    fun setUp() {
        val pathConfig = mockk<MaaPathConfig> {
            every { debugDir } returns tempFolder.root.absolutePath
        }
        logger = MaaSessionLogger(pathConfig)
    }

    @After
    fun tearDown() = runBlocking {
        logger.endSessionAndWait("TEST_TEARDOWN")
    }

    // ==================== 辅助 ====================

    private fun item(content: String, level: LogLevel = LogLevel.MESSAGE) =
        LogItem(content = content, level = level)

    private suspend fun awaitLogs(predicate: (List<LogItem>) -> Boolean): List<LogItem> =
        withTimeout(AWAIT_TIMEOUT_MS) { logger.logs.first(predicate) }

    private suspend fun readOnlyLogEntries(): List<LogEntry.Log> {
        val file = logger.getLogFiles().single()
        return logger.readLogFile(file.fileName).filterIsInstance<LogEntry.Log>()
    }

    private suspend fun awaitPersisted(count: Int): List<LogEntry.Log> =
        withTimeout(AWAIT_TIMEOUT_MS) {
            var entries = readOnlyLogEntries()
            while (entries.size < count) {
                kotlinx.coroutines.delay(LogConfig.LOG_FLUSH_INTERVAL_MS)
                entries = readOnlyLogEntries()
            }
            entries
        }

    // ==================== 基本落盘 / 展示 ====================

    @Test
    fun append_isDrainedByFlushLoop_intoBothUiAndFile() = runBlocking {
        logger.startSession(listOf("Fight"))

        logger.append(item("a"))
        logger.append(item("b"))

        val logs = awaitLogs { it.size == 2 }
        assertEquals(listOf("a", "b"), logs.map { it.content })

        val persisted = awaitPersisted(2)
        assertEquals(listOf("a", "b"), persisted.map { it.content })
    }

    /** 入队顺序即调用顺序：批量排空不得打乱 */
    @Test
    fun append_preservesCallOrderAcrossFlushCycles() = runBlocking {
        logger.startSession(listOf("Fight"))

        val expected = (1..40).map { "line-$it" }
        expected.take(20).forEach { logger.append(item(it)) }
        // 跨越至少一个 flush 周期后再补后半批
        kotlinx.coroutines.delay(LogConfig.LOG_FLUSH_INTERVAL_MS * 2)
        expected.drop(20).forEach { logger.append(item(it)) }

        val logs = awaitLogs { it.size == expected.size }
        assertEquals(expected, logs.map { it.content })
        assertEquals(expected, awaitPersisted(expected.size).map { it.content })
    }

    @Test
    fun append_preservesLevelAndContentOnDisk() = runBlocking {
        logger.startSession(listOf("Fight"))

        logger.append(item("警告内容", LogLevel.WARNING))

        val entry = awaitPersisted(1).single()
        assertEquals("警告内容", entry.content)
        assertEquals("WARNING", entry.level)
        assertTrue("落盘时间戳必须写入", entry.time > 0)
    }

    // ==================== 运行时上限（emitToUI 的核心改动） ====================

    @Test
    fun uiBuffer_keepsNewestWhenExceedingCap() = runBlocking {
        logger.startSession(listOf("Fight"))

        val total = max + 50
        (1..total).forEach { logger.append(item("n$it")) }

        val logs = awaitLogs { it.size == max }
        assertEquals("超上限必须保留最新的", "n$total", logs.last().content)
        assertEquals("最旧的应被丢弃", "n${total - max + 1}", logs.first().content)
        // 截断后仍必须是连续的一段，不能出现空洞
        assertEquals(
            (total - max + 1..total).map { "n$it" },
            logs.map { it.content },
        )
    }

    @Test
    fun uiBuffer_neverExceedsCapAcrossManyFlushCycles() = runBlocking {
        logger.startSession(listOf("Fight"))

        repeat(3) { round ->
            (1..max).forEach { logger.append(item("r$round-$it")) }
            kotlinx.coroutines.delay(LogConfig.LOG_FLUSH_INTERVAL_MS * 2)
            assertTrue("UI 缓冲越界: ${logger.logs.value.size}", logger.logs.value.size <= max)
        }

        val logs = awaitLogs { it.isNotEmpty() }
        assertEquals(max, logs.size)
        assertEquals("r2-$max", logs.last().content)
    }

    /** 单批就超过上限：走的是与「跨批累积超限」不同的分支 */
    @Test
    fun uiBuffer_handlesSingleBatchLargerThanCap() = runBlocking {
        logger.startSession(listOf("Fight"))

        val total = max * 2
        // 一次性灌入，绝大概率落在同一个 flush 批次里
        (1..total).forEach { logger.append(item("b$it")) }

        val logs = awaitLogs { it.size == max }
        assertEquals(max, logs.size)
        assertEquals("b$total", logs.last().content)
        assertEquals("b${total - max + 1}", logs.first().content)
    }

    /** 文件不受运行时上限影响：上限只裁剪内存展示 */
    @Test
    fun fileKeepsAllEntries_evenBeyondUiCap() = runBlocking {
        logger.startSession(listOf("Fight"))

        val total = max + 20
        (1..total).forEach { logger.append(item("f$it")) }

        val persisted = awaitPersisted(total)
        assertEquals(total, persisted.size)
        assertEquals("f1", persisted.first().content)
        assertEquals("f$total", persisted.last().content)
    }

    // ==================== 并发入队 ====================

    /**
     * append 现在可从任意线程直接入队（无协程投递）。
     * 跨线程的相对顺序不做保证，但**一条都不能丢**。
     */
    @Test
    fun append_losesNothingUnderConcurrentProducers() = runBlocking {
        logger.startSession(listOf("Fight"))

        val threads = 8
        val perThread = 100
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        repeat(threads) { t ->
            pool.execute {
                start.await()
                repeat(perThread) { i -> logger.append(item("t$t-$i")) }
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        pool.shutdownNow()

        val expected = threads * perThread
        val persisted = awaitPersisted(expected)
        assertEquals(expected, persisted.size)
        assertEquals(
            "所有生产者的条目都必须落盘",
            expected,
            persisted.map { it.content }.toSet().size,
        )
    }

    // ==================== appendAndWait：关键写入立即可见 ====================

    @Test
    fun appendAndWait_isVisibleImmediatelyWithoutWaitingForFlushLoop() = runBlocking {
        logger.startSession(listOf("Fight"))

        logger.appendAndWait("立即可见", LogLevel.INFO)

        // 不等待 flush 周期，直接读
        assertEquals(listOf("立即可见"), logger.logs.value.map { it.content })
        assertEquals(listOf("立即可见"), readOnlyLogEntries().map { it.content })
    }

    /** appendAndWait 会先排空 pending，故先前的 append 必须排在它前面 */
    @Test
    fun appendAndWait_flushesPendingFirst_preservingOrder() = runBlocking {
        logger.startSession(listOf("Fight"))

        logger.append(item("先入队"))
        logger.appendAndWait("后同步写", LogLevel.INFO)

        assertEquals(listOf("先入队", "后同步写"), logger.logs.value.map { it.content })
        assertEquals(listOf("先入队", "后同步写"), readOnlyLogEntries().map { it.content })
    }

    // ==================== appendToFileOnly ====================

    @Test
    fun appendToFileOnly_writesFileButNotUi() = runBlocking {
        logger.startSession(listOf("Fight"))

        logger.appendToFileOnly("[TaskParams] Fight: {}")

        val persisted = awaitPersisted(1)
        assertEquals("[TaskParams] Fight: {}", persisted.single().content)
        assertTrue("仅文件的条目不得进入运行时 UI", logger.logs.value.isEmpty())
    }

    // ==================== 会话生命周期 ====================

    @Test
    fun startSession_writesHeaderWithTaskNames() = runBlocking {
        logger.startSession(listOf("StartUp", "Fight"))

        val file = logger.getLogFiles().single()
        val header = logger.readLogFile(file.fileName).filterIsInstance<LogEntry.Header>().single()
        assertEquals(listOf("StartUp", "Fight"), header.tasks)
        assertTrue(logger.sessionStartTimeMillis > 0)
    }

    @Test
    fun startSession_clearsUiAndPendingFromPreviousSession() = runBlocking {
        logger.startSession(listOf("Fight"))
        logger.appendAndWait("旧会话")
        assertEquals(1, logger.logs.value.size)

        // 未排空的 pending 也不得泄漏到新会话
        logger.append(item("旧会话未排空"))
        logger.startSession(listOf("Mall"))

        assertTrue(logger.logs.value.isEmpty())
        logger.appendAndWait("新会话")
        assertEquals(listOf("新会话"), logger.logs.value.map { it.content })
    }

    @Test
    fun endSession_writesFooterAndFlushesRemainingPending() = runBlocking {
        logger.startSession(listOf("Fight"))
        logger.append(item("收尾前未排空"))

        logger.endSessionAndWait("COMPLETED")

        val file = logger.getLogFiles().single()
        val entries = logger.readLogFile(file.fileName)
        assertEquals(
            "会话结束必须把剩余 pending 落盘",
            listOf("收尾前未排空"),
            entries.filterIsInstance<LogEntry.Log>().map { it.content },
        )
        val footer = entries.filterIsInstance<LogEntry.Footer>().single()
        assertEquals("COMPLETED", footer.status)
        assertEquals(-1L, logger.sessionStartTimeMillis)
    }

    @Test
    fun completeSessionAndWait_appendsFinalLogBeforeFooter() = runBlocking {
        logger.startSession(listOf("Fight"))

        logger.completeSessionAndWait("ERROR", "崩溃收尾", LogLevel.ERROR)

        val file = logger.getLogFiles().single()
        val entries = logger.readLogFile(file.fileName)
        val log = entries.filterIsInstance<LogEntry.Log>().single()
        assertEquals("崩溃收尾", log.content)
        assertEquals("ERROR", log.level)
        assertEquals("ERROR", entries.filterIsInstance<LogEntry.Footer>().single().status)
        // 顺序：header → log → footer
        assertTrue(entries.indexOfFirst { it is LogEntry.Log } < entries.indexOfFirst { it is LogEntry.Footer })
    }

    @Test
    fun clearRuntimeLogs_emptiesUiButKeepsFile() = runBlocking {
        logger.startSession(listOf("Fight"))
        logger.appendAndWait("留在文件里")

        logger.clearRuntimeLogs()

        awaitLogs { it.isEmpty() }
        assertEquals(listOf("留在文件里"), readOnlyLogEntries().map { it.content })
    }

    @Test
    fun appendWithoutActiveSession_doesNotCrash() = runBlocking {
        logger.append(item("无会话"))
        logger.appendToFileOnly("无会话-文件")

        assertTrue(logger.getLogFiles().isEmpty())
        assertFalse(logger.logs.value.any { it.content == "无会话" })
    }

    private companion object {
        const val AWAIT_TIMEOUT_MS = 10_000L
    }
}
