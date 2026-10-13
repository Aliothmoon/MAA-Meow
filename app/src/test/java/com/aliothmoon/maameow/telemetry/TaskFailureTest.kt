package com.aliothmoon.maameow.telemetry

import io.mockk.every
import io.mockk.mockk
import io.sentry.ISpan
import io.sentry.NoOpSpan
import io.sentry.SentryLevel
import io.sentry.SpanContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TaskFailureTest {

    private val spanContext = SpanContext(RunTracer.TASK_OP)
    private val span = mockk<ISpan> { every { spanContext } returns this@TaskFailureTest.spanContext }

    private val node = FailureSignal(
        subtask = "ProcessTask",
        className = "asst::ProcessTask",
        first = "DepotBegin",
        preTask = "",
        what = null,
        why = null,
        atMs = 1_000,
    )
    private val terminal = FailureSignal(
        subtask = "DepotRecognitionTask",
        className = "asst::DepotRecognitionTask",
        first = null,
        preTask = null,
        what = null,
        why = null,
        atMs = 2_300,
    )

    private val failure = TaskFailure(
        runId = "r1",
        taskChain = "Depot",
        taskId = 1,
        startedAtMs = 1_000,
        durationMs = 1_400,
        failedSubTasks = 2,
        terminal = terminal,
        node = node,
        exception = null,
        recent = listOf(node, terminal),
        options = mapOf("client_type" to "Official"),
        tags = mapOf("run.id" to "r1", "run.kind" to "chain"),
        span = span,
    )

    @Test
    fun `标题与分组落在致命子任务和它卡住的节点上`() {
        val event = failure.toSentryEvent()

        assertEquals("Maa task failed: Depot at DepotRecognitionTask (DepotBegin)", event.message?.formatted)
        assertEquals(SentryLevel.ERROR, event.level)
        assertEquals(TASK_FAILURE_TRANSACTION, event.transaction)
        assertEquals(
            listOf("maameow-task-failure", "Depot", "DepotRecognitionTask", "DepotBegin", "", ""),
            event.fingerprints,
        )
    }

    @Test
    fun `卡在哪一步之后只进 tag，不拆组也不进标题`() {
        val stuck = node.copy(preTask = "DepotAllTab")
        val event = failure.copy(node = stuck).toSentryEvent()

        assertEquals(failure.toSentryEvent().fingerprints, event.fingerprints)
        assertEquals("Maa task failed: Depot at DepotRecognitionTask (DepotBegin)", event.message?.formatted)
        assertEquals("DepotAllTab", event.tags?.get("failure.pre_task"))
    }

    @Test
    fun `连锁失败标出本轮先失败的任务链，决定流程的参数进 tag`() {
        val event = failure.copy(
            priorFailure = "StartUp",
            options = mapOf("stage" to "1-7", "medicine" to "999"),
        ).toSentryEvent()

        assertEquals("StartUp", event.tags?.get("run.prior_failure"))
        assertEquals("1-7", event.tags?.get("option.stage"))
        assertNull(event.tags?.get("option.medicine"))
        // 全量参数仍在 extra
        assertEquals("1-7", event.extras?.get("option.stage"))
        assertEquals("999", event.extras?.get("option.medicine"))
    }

    @Test
    fun `任务名去掉实例地址，普通任务名不动`() {
        assertEquals("SSSBegin", stripTaskAddress("0xb400007401ddbb10@SSSBegin"))
        assertEquals("Sarkaz@Roguelike@Begin", stripTaskAddress("Sarkaz@Roguelike@Begin"))
        assertEquals("", stripTaskAddress(""))
    }

    @Test
    fun `可搜的进 tag，空值不打，明细进 extra`() {
        val event = failure.toSentryEvent()

        assertEquals(
            mapOf(
                "run.id" to "r1",
                "run.kind" to "chain",
                "task.chain" to "Depot",
                "failure.subtask" to "DepotRecognitionTask",
                "failure.node" to "DepotBegin",
                "run.prior_failure" to "none",
                "result" to "failure",
            ),
            event.tags,
        )
        assertEquals(
            mapOf(
                "task.id" to 1,
                "task.duration_ms" to 1_400L,
                "task.started_at_ms" to 1_000L,
                "failure.count" to 2,
                "failure.class" to "asst::DepotRecognitionTask",
                "failure.recent" to "ProcessTask(DepotBegin) > DepotRecognitionTask",
                "option.client_type" to "Official",
            ),
            event.extras,
        )
    }

    @Test
    fun `Core 异常没有子任务可指，标题只带异常种类`() {
        val event = failure.copy(terminal = null, node = null, exception = "OutOfMemory", recent = emptyList())
            .toSentryEvent()

        assertEquals("Maa task failed: Depot (OutOfMemory)", event.message?.formatted)
        assertEquals("OutOfMemory", event.tags?.get("failure.exception"))
        assertEquals(UNOBSERVED_SUBTASK, event.tags?.get("failure.subtask"))
    }

    @Test
    fun `没观测到子任务错误时用占位`() {
        val event = failure.copy(terminal = null, node = null, recent = emptyList()).toSentryEvent()

        assertEquals("Maa task failed: Depot at terminal_failure", event.message?.formatted)
        assertFalse(event.tags.orEmpty().containsKey("failure.node"))
    }

    @Test
    fun `没有节点时标题退到 what`() {
        val recruit = terminal.copy(subtask = "InfrastInfoTask", what = "FacilityLayoutRecognitionFailed", why = "x")
        val event = failure.copy(terminal = recruit, node = null).toSentryEvent()

        assertEquals(
            "Maa task failed: Depot at InfrastInfoTask (FacilityLayoutRecognitionFailed)",
            event.message?.formatted,
        )
        assertEquals("x", event.extras?.get("failure.why"))
    }

    @Test
    fun `事件挂回任务 Span 所在的 Trace`() {
        val trace = failure.toSentryEvent().contexts.trace

        assertEquals(spanContext.traceId, trace?.traceId)
        assertEquals(spanContext.spanId, trace?.spanId)
    }

    @Test
    fun `no-op Span 不写 trace 上下文`() {
        assertNull(failure.copy(span = NoOpSpan.getInstance()).toSentryEvent().contexts.trace)
    }

    @Test
    fun `启动失败按状态码与异常类型分组`() {
        val event = StartFailure(
            runId = "r1",
            code = "REMOTE_ACCESS_UNAVAILABLE",
            what = "TimeoutCancellationException",
            cause = "TimeoutCancellationException: Timed out waiting for 8000 ms",
            tags = mapOf("run.id" to "r1"),
        ).toSentryEvent()

        assertEquals(
            "Maa start failed: REMOTE_ACCESS_UNAVAILABLE (TimeoutCancellationException)",
            event.message?.formatted,
        )
        assertEquals(
            listOf("maameow-start-failure", "REMOTE_ACCESS_UNAVAILABLE", "TimeoutCancellationException"),
            event.fingerprints,
        )
        assertEquals("REMOTE_ACCESS_UNAVAILABLE", event.tags?.get("failure.code"))
        assertEquals(
            "TimeoutCancellationException: Timed out waiting for 8000 ms",
            event.extras?.get("failure.cause"),
        )
        assertEquals(START_FAILURE_TRANSACTION, event.transaction)
    }

    @Test
    fun `进程死亡按服务状态、任务链与后端分组`() {
        val event = ServiceDeath(
            runId = "r1",
            state = "running",
            serviceState = "died",
            backend = "shizuku",
            taskChain = "Fight",
            tags = emptyMap(),
        ).toSentryEvent()

        assertEquals("Maa service died during running (Fight)", event.message?.formatted)
        assertEquals(SentryLevel.FATAL, event.level)
        assertEquals(listOf("maameow-service-died", "died", "Fight", "shizuku"), event.fingerprints)
        assertEquals("died", event.tags?.get("service.state"))
        assertEquals("running", event.tags?.get("run.state"))
    }

    @Test
    fun `触发启动失败按结果与文案模板分组`() {
        val event = LaunchFailure(
            result = "failed_start",
            launchReason = "Launch exception: %1\$s",
            message = "启动异常：boom",
            delayMs = 1_500,
            logFile = "schedule/trigger_20261002_110000_000.log",
            tags = mapOf("run_mode" to "background"),
        ).toSentryEvent()

        assertEquals("Maa launch failed: failed_start (Launch exception: %1\$s)", event.message?.formatted)
        assertEquals(
            listOf("maameow-launch-failure", "failed_start", "Launch exception: %1\$s"),
            event.fingerprints,
        )
        assertEquals("failed_start", event.tags?.get("launch.result"))
        assertEquals("background", event.tags?.get("run_mode"))
        assertEquals("启动异常：boom", event.extras?.get("launch.message"))
        assertEquals(1_500L, event.extras?.get("launch.delay_ms"))
        assertEquals(LAUNCH_FAILURE_TRANSACTION, event.transaction)
        assertEquals(SentryLevel.ERROR, event.level)
    }

    @Test
    fun `锁屏跳过不论文案都归一组，记为警告，原因留在标签里`() {
        fun skipped(reason: String) = LaunchFailure(
            result = "skipped_locked",
            launchReason = reason,
            message = null,
            delayMs = 0,
            logFile = "schedule/trigger_20261002_110000_000.log",
        ).toSentryEvent()

        val pinRequired = skipped("Skipped: a device lock password is set")
        val locked = skipped("Device is locked")

        assertEquals(pinRequired.fingerprints, locked.fingerprints)
        assertEquals(listOf("maameow-launch-failure", "skipped_locked"), locked.fingerprints)
        assertEquals("Maa launch skipped: skipped_locked", locked.message?.formatted)
        assertEquals(SentryLevel.WARNING, locked.level)
        assertEquals("Device is locked", locked.tags?.get("launch.reason"))
    }

    @Test
    fun `Shizuku 未运行按开机后没起与中途停了分组，记为警告`() {
        val event = ShizukuDown(
            afterBoot = false,
            enabledSchedules = 3,
            tags = mapOf("shizuku.binder" to "dead"),
            extras = mapOf("shizuku.dead_ago_s" to 60L),
        ).toSentryEvent()

        assertEquals("Shizuku not running with schedules enabled (stopped)", event.message?.formatted)
        assertEquals(listOf("maameow-shizuku-down", "stopped"), event.fingerprints)
        assertEquals(SentryLevel.WARNING, event.level)
        assertEquals("dead", event.tags?.get("shizuku.binder"))
        assertEquals("stopped", event.tags?.get("shizuku.down"))
        assertEquals(3, event.extras?.get("schedules.enabled"))
        assertEquals(60L, event.extras?.get("shizuku.dead_ago_s"))
        assertEquals(SHIZUKU_DOWN_TRANSACTION, event.transaction)
    }

    private fun evidenceExtras(evidence: Evidence): Map<String, Any?> =
        failure.toSentryEvent().apply { setEvidence(evidence) }.extras.orEmpty()
            .filterKeys { it.startsWith("logs.") || it.startsWith("attachment.") }

    @Test
    fun `证据带上了就记下各带了多少`() {
        val logs = DiagnosticLogs(
            entries = listOf(DiagnosticLog("asst.log", "maacore", "failed", 6)),
            selectedRawBytes = 6,
            truncated = true,
            warnings = listOf("new_log_included_whole:asst.log"),
        )
        val attachment = AttachmentOutcome.Attached(listOf(EvidenceImage("a.jpg", ByteArray(40))))

        assertEquals(
            mapOf(
                "logs.status" to "captured",
                "logs.count" to 1,
                "logs.selected_raw_bytes" to 6L,
                "logs.truncated" to true,
                "logs.warnings" to "new_log_included_whole:asst.log",
                "attachment.status" to "attached",
                "attachment.image_count" to 1,
                "attachment.bytes" to 40,
            ),
            evidenceExtras(Evidence(logs, attachment)),
        )
    }

    @Test
    fun `证据没带上就记下原因`() {
        assertEquals(
            mapOf("logs.status" to "not_available", "attachment.status" to "not_selected"),
            evidenceExtras(Evidence(null, AttachmentOutcome.NotSelected)),
        )
        assertEquals(
            mapOf(
                "logs.status" to "no_evidence",
                "logs.count" to 0,
                "logs.selected_raw_bytes" to 0L,
                "logs.truncated" to false,
                "attachment.status" to "too_large",
                "attachment.detail" to "too large",
            ),
            evidenceExtras(
                Evidence(
                    DiagnosticLogs(emptyList(), 0, truncated = false, warnings = emptyList()),
                    AttachmentOutcome.Omitted("too_large", "too large"),
                )
            ),
        )
    }

    @Test
    fun `日志记录的关联信息取自事件自己`() {
        val event = failure.toSentryEvent()
        val context = event.diagnosticLogContext(failure)

        assertEquals(event.eventId.toString(), context.eventId)
        assertEquals("task_failure", context.reason)
        assertEquals("r1", context.runId)
        assertEquals("DepotRecognitionTask", context.attributes["failure.subtask"])
        assertEquals(spanContext.traceId.toString(), context.traceId)
        assertEquals(spanContext.spanId.toString(), context.spanId)
    }
}
