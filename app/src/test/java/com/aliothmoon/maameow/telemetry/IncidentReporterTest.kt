package com.aliothmoon.maameow.telemetry

import io.mockk.mockk
import io.sentry.ISpan
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class IncidentReporterTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val debugDir by lazy { temp.newFolder("debug") }
    private val sent = mutableListOf<Pair<Incident, Evidence>>()

    private val failure = TaskFailure(
        runId = "r1",
        taskChain = "Depot",
        taskId = 1,
        startedAtMs = 1_000,
        durationMs = 300,
        failedSubTasks = 0,
        terminal = null,
        node = null,
        exception = null,
        recent = emptyList(),
        options = emptyMap(),
        tags = emptyMap(),
        span = mockk<ISpan>(relaxed = true),
    )

    private fun TestScope.reporter(
        secrets: List<String> = emptyList(),
        sampleRate: Double = 1.0,
        imagesAllowed: Boolean = true,
    ) = IncidentReporter(
        scope = this,
        io = dispatcher,
        store = { LocalEvidenceStore(debugDir) },
        secrets = { secrets },
        encodeImage = { it },
        imagesAllowed = { imagesAllowed },
        attachmentSampleRate = sampleRate,
        send = { incident, evidence -> sent += incident to evidence },
    )

    private fun write(path: String, content: String) = File(debugDir, path).apply {
        parentFile?.mkdirs()
        writeText(content)
    }

    private val evidence get() = sent.single().second

    private fun sources() = evidence.logs?.entries?.map { it.source }

    @Test
    fun `任务失败带上这个任务期间的日志与截图，密钥先打码`() = runTest(dispatcher) {
        val reporter = reporter(secrets = listOf("hunter2-secret"))
        write("asst.log", "before\n")
        write("gui/meow_log_20261002_082323_1.log", "{\"type\":\"header\"}\n")
        reporter.onRunStarted()
        reporter.onTaskStarted()
        advanceUntilIdle()

        write("asst.log", "before\n{\"account_name\":\"138\",\"penguin_id\":\"hunter2-secret\"} failed\n")
        write("gui/meow_log_20261002_082323_1.log", "{\"type\":\"header\"}\n{\"content\":\"任务出错\"}\n")
        write("interface/2026.10.02-08.23.43.20_raw.png", "image")
        reporter.report(failure)
        advanceUntilIdle()

        assertEquals(failure, sent.single().first)
        assertEquals(listOf("asst.log", "gui/meow_log_20261002_082323_1.log"), sources())
        assertEquals(
            "before\n{\"account_name\":\"***\",\"penguin_id\":\"***\"} failed\n",
            evidence.logs?.entries?.first()?.content,
        )
        assertEquals("session", evidence.logs?.entries?.last()?.kind)
        val images = (evidence.attachment as AttachmentOutcome.Attached).images
        assertEquals(listOf("2026.10.02-08.23.43.20_raw.jpg"), images.map { it.filename })
    }

    /** 上一轮的会话日志不能混进来 */
    @Test
    fun `会话日志认最新的那一份`() = runTest(dispatcher) {
        val reporter = reporter()
        write("gui/meow_log_20261001_090000_3.log", "old\n")
        write("gui/meow_log_20261002_082323_1.log", "new\n")
        reporter.onRunStarted()
        reporter.onTaskStarted()
        advanceUntilIdle()

        File(debugDir, "gui/meow_log_20261001_090000_3.log").appendText("old grows\n")
        File(debugDir, "gui/meow_log_20261002_082323_1.log").appendText("new grows\n")
        reporter.report(failure)
        advanceUntilIdle()

        assertEquals(listOf("gui/meow_log_20261002_082323_1.log"), sources())
    }

    @Test
    fun `没被采样到时不带截图，日志照带`() = runTest(dispatcher) {
        val reporter = reporter(sampleRate = 0.0)
        write("asst.log", "")
        reporter.onTaskStarted()
        advanceUntilIdle()

        write("asst.log", "failed\n")
        write("interface/shot.png", "image")
        reporter.report(failure)
        advanceUntilIdle()

        assertEquals(AttachmentOutcome.NotSelected, evidence.attachment)
        assertEquals("failed\n", evidence.logs?.entries?.single()?.content)
    }

    /** 同一个失败反复出现时画面几乎一样，再带是白占附件额度 */
    @Test
    fun `同一轮里同一个失败只有第一次带截图`() = runTest(dispatcher) {
        val reporter = reporter()
        reporter.onRunStarted()
        val outcomes = listOf(
            failure,
            failure.copy(taskId = 2),
            failure.copy(taskId = 3, taskChain = "Fight"),
        ).map { incident ->
            reporter.onTaskStarted()
            advanceUntilIdle()
            write("interface/shot_${incident.taskId}.png", "image")
            reporter.report(incident)
            advanceUntilIdle()
            sent.last().second.attachment
        }

        assertTrue(outcomes[0] is AttachmentOutcome.Attached)
        assertEquals("duplicate", (outcomes[1] as AttachmentOutcome.Omitted).status)
        // 换了个失败就不算重复
        assertTrue(outcomes[2] is AttachmentOutcome.Attached)
    }

    @Test
    fun `新一轮里同一个失败重新带截图`() = runTest(dispatcher) {
        val reporter = reporter()
        val outcomes = listOf(1, 2).map { round ->
            reporter.onRunStarted()
            reporter.onTaskStarted()
            advanceUntilIdle()
            write("interface/shot_$round.png", "image")
            reporter.report(failure.copy(runId = "r$round"))
            advanceUntilIdle()
            sent.last().second.attachment
        }

        assertTrue(outcomes.all { it is AttachmentOutcome.Attached })
    }

    @Test
    fun `没采到的那次不挡住后面采到的同一个失败`() = runTest(dispatcher) {
        val rate = 0.5
        val skipped = (1..200L).first { !shouldSampleAttachment("r1", it, rate) }.toInt()
        val sampled = (1..200L).first { shouldSampleAttachment("r1", it, rate) }.toInt()
        val reporter = reporter(sampleRate = rate)
        reporter.onRunStarted()
        val outcomes = listOf(skipped, sampled).map { taskId ->
            reporter.onTaskStarted()
            advanceUntilIdle()
            write("interface/shot_$taskId.png", "image")
            reporter.report(failure.copy(taskId = taskId))
            advanceUntilIdle()
            sent.last().second.attachment
        }

        assertEquals(AttachmentOutcome.NotSelected, outcomes[0])
        assertTrue(outcomes[1] is AttachmentOutcome.Attached)
    }

    /** 前台模式下 Core 截的是主屏，可能拍到别的应用 */
    @Test
    fun `不许带截图时只带日志`() = runTest(dispatcher) {
        val reporter = reporter(imagesAllowed = false)
        write("asst.log", "")
        reporter.onTaskStarted()
        advanceUntilIdle()

        write("asst.log", "failed\n")
        write("interface/shot.png", "image")
        reporter.report(failure)
        advanceUntilIdle()

        assertEquals(AttachmentOutcome.NotSelected, evidence.attachment)
        assertEquals("failed\n", evidence.logs?.entries?.single()?.content)
    }

    @Test
    fun `没有开跑快照时只发事件`() = runTest(dispatcher) {
        write("asst.log", "failed\n")
        reporter().report(failure)
        advanceUntilIdle()

        assertNull(evidence.logs)
        assertEquals(AttachmentOutcome.NotSelected, evidence.attachment)
    }

    @Test
    fun `整轮级别的事件取整轮快照`() = runTest(dispatcher) {
        val reporter = reporter()
        write("asst.log", "boot\n")
        write("service_bind_debug.log", "bind\n")
        reporter.onRunStarted()
        advanceUntilIdle()

        write("asst.log", "boot\nrunning\n")
        reporter.onTaskStarted()
        advanceUntilIdle()
        write("asst.log", "boot\nrunning\ndied\n")
        write("crash.log", "SIGSEGV\n")
        write("service_bind_debug.log", "bind\nbinder died\n")
        write("interface/shot.png", "image")
        reporter.report(
            ServiceDeath(
                runId = "r1",
                state = "running",
                serviceState = "died",
                backend = "shizuku",
                taskChain = "Fight",
                tags = emptyMap(),
            )
        )
        advanceUntilIdle()

        // 崩溃现场与启动诊断排在前面，免得被大日志挤掉
        assertEquals(listOf("crash.log", "service_bind_debug.log", "asst.log"), sources())
        assertEquals("boot\nrunning\ndied\n", evidence.logs?.entries?.last()?.content)
        assertEquals(AttachmentOutcome.NotSelected, evidence.attachment)
    }

    /** 触发日志一次一个文件，不需要开跑快照，整份带上 */
    @Test
    fun `触发启动失败带上整份触发日志`() = runTest(dispatcher) {
        val reporter = reporter()
        write("asst.log", "unrelated\n")
        write("schedule/trigger_20261002_110000_000.log", "{\"type\":\"header\"}\n{\"type\":\"footer\"}\n")
        reporter.report(
            LaunchFailure(
                result = "failed_ui_launch",
                launchReason = "Failed to launch UI",
                message = "界面拉起失败",
                delayMs = 2_000,
                logFile = "schedule/trigger_20261002_110000_000.log",
            )
        )
        advanceUntilIdle()

        assertEquals(listOf("schedule/trigger_20261002_110000_000.log"), sources())
        assertEquals("schedule", evidence.logs?.entries?.single()?.kind)
        assertEquals("{\"type\":\"header\"}\n{\"type\":\"footer\"}\n", evidence.logs?.entries?.single()?.content)
        assertEquals(AttachmentOutcome.NotSelected, evidence.attachment)
    }

    @Test
    fun `启动失败再带绑定与拉起日志的最近一段`() = runTest(dispatcher) {
        val reporter = reporter()
        write("schedule/trigger_20261002_110000_000.log", "{\"type\":\"header\"}\n")
        // 绑定日志不分轮次，只要最后那段
        val old = "old line\n".repeat(4_000)
        write("service_bind_debug.log", old + "BIND_DENIED\n")
        write("shizuku_launch_debug.log", "[E] child exited with status=9\n")
        reporter.report(
            LaunchFailure(
                result = "failed_start",
                launchReason = "Failed to start the elevated service via %1\$s; start canceled (%2\$s)",
                message = null,
                delayMs = 0,
                logFile = "schedule/trigger_20261002_110000_000.log",
                withBootLogs = true,
            )
        )
        advanceUntilIdle()

        assertEquals(
            listOf(
                "schedule/trigger_20261002_110000_000.log",
                "service_bind_debug.log",
                "shizuku_launch_debug.log",
            ),
            sources(),
        )
        val bind = evidence.logs!!.entries.single { it.source == "service_bind_debug.log" }
        assertTrue(bind.content.endsWith("BIND_DENIED\n"))
        assertTrue(bind.rawBytes < old.length)
    }

    @Test
    fun `Shizuku 未运行不带日志`() = runTest(dispatcher) {
        val reporter = reporter()
        write("service_bind_debug.log", "BIND\n")
        reporter.report(ShizukuDown(afterBoot = true, enabledSchedules = 2, tags = emptyMap(), extras = emptyMap()))
        advanceUntilIdle()

        assertNull(evidence.logs)
        assertEquals(AttachmentOutcome.NotSelected, evidence.attachment)
    }

    @Test
    fun `一次任务失败用掉这个任务的快照`() = runTest(dispatcher) {
        val reporter = reporter()
        write("asst.log", "")
        reporter.onTaskStarted()
        advanceUntilIdle()
        write("asst.log", "first failure\n")
        reporter.report(failure)
        reporter.report(failure.copy(taskId = 2))
        advanceUntilIdle()

        // 没快照的那条不用等日志落盘，会先发出去
        val logsByTask = sent.associate { (incident, evidence) -> (incident as TaskFailure).taskId to evidence.logs }
        assertEquals(setOf(1, 2), logsByTask.keys)
        assertEquals("first failure\n", logsByTask[1]?.entries?.single()?.content)
        assertNull(logsByTask[2])
    }

    @Test
    fun `遥测关掉后排着队的事件不发`() = runTest(dispatcher) {
        val reporter = reporter()
        reporter.onTaskStarted()
        advanceUntilIdle()

        reporter.report(failure)
        reporter.cancelAll()
        advanceUntilIdle()

        assertTrue(sent.isEmpty())
    }

    @Test
    fun `正式版抽样，预发布版与本地构建全带`() {
        assertEquals(0.2, failureAttachmentSampleRate("0.23.0", debug = false), 0.0)
        assertEquals(1.0, failureAttachmentSampleRate("0.23.0-beta.1", debug = false), 0.0)
        assertEquals(1.0, failureAttachmentSampleRate("0.22.1-alpha.48", debug = false), 0.0)
        // 没打上 tag 的构建版本名是提交哈希或 0.0.0-dev
        assertEquals(1.0, failureAttachmentSampleRate("8f7cc0df", debug = false), 0.0)
        assertEquals(1.0, failureAttachmentSampleRate("0.0.0-dev", debug = false), 0.0)
        assertEquals(1.0, failureAttachmentSampleRate("0.23.0", debug = true), 0.0)
    }

    @Test
    fun `附件采样对同一个失败结论固定，比例大致对得上`() {
        assertFalse(shouldSampleAttachment("r1", 7, 0.0))
        assertTrue(shouldSampleAttachment("r1", 7, 1.0))
        assertEquals(shouldSampleAttachment("r1", 7, 0.5), shouldSampleAttachment("r1", 7, 0.5))

        val sampled = (1..2_000).count { shouldSampleAttachment("run-$it", it.toLong(), 0.25) }
        assertTrue("sampled=$sampled", sampled in 400..600)
    }
}
