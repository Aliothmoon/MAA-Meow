package com.aliothmoon.maameow.root

import com.aliothmoon.maameow.TestSources
import com.aliothmoon.maameow.third.Ln
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.function.IntConsumer

/** 孤儿进程的最后一道闸：清理卡死也必须按期 halt */
class ProcessExitTest {

    private val halts = LinkedBlockingQueue<Int>()
    private val defaultHalt = ProcessExit.halt

    @Before
    fun setUp() {
        mockkStatic(Ln::class)
        every { Ln.i(any()) } just runs
        every { Ln.e(any()) } just runs
        every { Ln.e(any(), any()) } just runs
        ProcessExit.resetForTest()
        ProcessExit.halt = IntConsumer { halts.add(it) }
        ProcessExit.deadlineMs = 300L
    }

    @After
    fun tearDown() {
        ProcessExit.halt = defaultHalt
        ProcessExit.deadlineMs = ProcessExit.DEADLINE_MS
        ProcessExit.resetForTest()
        unmockkStatic(Ln::class)
    }

    @Test
    fun cleanupFinished_haltsZeroAndDeadlineNeverFires() {
        var cleaned = false

        ProcessExit.exit("test") { cleaned = true }

        assertTrue(cleaned)
        assertEquals(0, halts.poll(1, TimeUnit.SECONDS))
        assertEquals("清理完成后期限不得再 halt", null, halts.poll(600, TimeUnit.MILLISECONDS))
    }

    @Test
    fun cleanupHangs_deadlineHaltsOne() {
        val release = CountDownLatch(1)
        val worker = Thread { ProcessExit.exit("hang") { release.await() } }.apply { start() }

        assertEquals("清理卡死必须按期 halt(1)", 1, halts.poll(2, TimeUnit.SECONDS))

        release.countDown()
        worker.join(2_000)
    }

    @Test
    fun cleanupThrows_stillHaltsZero() {
        ProcessExit.exit("boom") { error("cleanup failed") }

        assertEquals(0, halts.poll(1, TimeUnit.SECONDS))
    }

    @Test
    fun secondCallerOnOtherThread_isIgnored() {
        val inCleanup = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = Thread {
            ProcessExit.exit("first") {
                inCleanup.countDown()
                release.await()
            }
        }.apply { start() }
        assertTrue(inCleanup.await(1, TimeUnit.SECONDS))

        var secondRan = false
        ProcessExit.exit("second") { secondRan = true }

        assertFalse("其他线程的后来者不得重复清理", secondRan)
        release.countDown()
        first.join(2_000)
        assertEquals(0, halts.poll(1, TimeUnit.SECONDS))
    }

    @Test
    fun nestedCallOnSameThread_runsInline() {
        val order = mutableListOf<String>()

        ProcessExit.exit("app died") {
            order += "outer"
            // App 死亡回调转进服务 destroy，同一线程再进来
            ProcessExit.exit("destroy requested") { order += "inner" }
            order += "outer-end"
        }

        assertEquals(listOf("outer", "inner", "outer-end"), order)
        assertEquals("只由外层 halt 一次", 0, halts.poll(1, TimeUnit.SECONDS))
        assertEquals(null, halts.poll(400, TimeUnit.MILLISECONDS))
    }

    @Test
    fun remoteServicesExitOnlyThroughProcessExit() {
        val offenders = TestSources.resolveDir("src/main/java/com/aliothmoon/maameow/remote")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.readText().contains("exitProcess(") }
            .map { it.name }
            .toList()
        assertTrue(
            "提权服务退出必须走 ProcessExit：直接 exitProcess 没有期限，清理一卡就成孤儿: $offenders",
            offenders.isEmpty(),
        )
    }
}
