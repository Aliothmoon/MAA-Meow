package com.aliothmoon.maameow.telemetry

import com.alibaba.fastjson2.JSON
import com.aliothmoon.maameow.maa.AsstMsg
import io.mockk.every
import io.mockk.mockk
import io.sentry.ISpan
import io.sentry.SpanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 记下对一条 Span 的全部写入；子 Span 同样是它 */
private class SpanRecord(val op: String, val description: String?) {
    val data = mutableMapOf<String, Any?>()
    val tags = mutableMapOf<String, String>()
    val children = mutableListOf<SpanRecord>()
    var status: SpanStatus? = null

    val span: ISpan = mockk<ISpan>(relaxed = true).also { span ->
        every { span.setData(any(), any()) } answers { data[firstArg()] = secondArg() }
        every { span.setTag(any(), any()) } answers { tags[firstArg()] = secondArg() }
        every { span.startChild(any<String>(), any<String>()) } answers {
            SpanRecord(firstArg(), secondArg()).also(children::add).span
        }
        every { span.finish(any<SpanStatus>()) } answers { status = firstArg() }
    }
}

class RunTracerTest {

    private val transactions = mutableListOf<SpanRecord>()
    private val failures = mutableListOf<TaskFailure>()
    private var taskStarts = 0
    private var now = 1_000L
    private val environment = mutableMapOf<String, String>()
    private val tracer = RunTracer(
        startTransaction = { name, op -> SpanRecord(op, name).also(transactions::add).span },
        clock = { now },
        environment = { environment.toMap() },
        onTaskStarted = { taskStarts++ },
        onTaskFailure = failures::add,
    )

    init {
        tracer.begin("r1", "chain", listOf("StartUp", "Depot"))
        tracer.register(1, mapOf("client_type" to "Official"))
    }

    private fun emit(message: AsstMsg, json: String = "{}") = tracer.onCallback(message, JSON.parseObject(json))

    private fun start(taskId: Int, chain: String) =
        emit(AsstMsg.TaskChainStart, """{"taskchain":"$chain","taskid":$taskId}""")

    private fun processError(taskId: Int, chain: String, first: String, preTask: String = "") = emit(
        AsstMsg.SubTaskError,
        """{"class":"asst::ProcessTask","details":{},"first":["$first"],"pre_task":"$preTask",""" +
            """"subtask":"ProcessTask","taskchain":"$chain","taskid":$taskId}""",
    )

    private fun subTaskError(taskId: Int, chain: String, subtask: String, extra: String = "") = emit(
        AsstMsg.SubTaskError,
        """{"class":"asst::$subtask","details":{},"subtask":"$subtask","taskchain":"$chain","taskid":$taskId$extra}""",
    )

    private val transaction get() = transactions.single()

    @Test
    fun `一轮对应一条事务，每条任务链一条子 Span`() {
        start(1, "StartUp")
        emit(AsstMsg.TaskChainCompleted, """{"taskchain":"StartUp","taskid":1}""")
        start(2, "Depot")
        emit(AsstMsg.TaskChainError, """{"taskchain":"Depot","taskid":2}""")
        emit(AsstMsg.AllTasksCompleted)

        assertEquals(RunTracer.RUN_OP, transaction.op)
        assertEquals(RunTracer.RUN_NAME, transaction.description)
        assertEquals(2, transaction.data["task_count"])
        assertEquals("StartUp,Depot", transaction.data["tasks"])
        assertEquals("r1", transaction.tags["run.id"])
        assertEquals("chain", transaction.tags["run.kind"])
        assertEquals(SpanStatus.INTERNAL_ERROR, transaction.status)
        assertEquals("failure", transaction.data["result"])

        val (first, second) = transaction.children
        assertEquals(RunTracer.TASK_OP, first.op)
        assertEquals("StartUp", first.description)
        assertEquals(1, first.data["task_id"])
        assertEquals("Official", first.data["option.client_type"])
        assertEquals(SpanStatus.OK, first.status)
        assertEquals(SpanStatus.INTERNAL_ERROR, second.status)
        assertEquals(2, taskStarts)
    }

    /** 真机 asst.log 里的序列：ProcessTask 先报，包着它的子任务再报，然后任务链失败 */
    @Test
    fun `致命子任务与卡住的节点取自最后两条错误`() {
        start(1, "Depot")
        processError(1, "Depot", first = "DepotBegin")
        now += 1_300
        subTaskError(1, "Depot", "DepotRecognitionTask")
        now += 100
        emit(AsstMsg.TaskChainError, """{"taskchain":"Depot","taskid":1}""")

        val failure = failures.single()
        assertEquals("r1", failure.runId)
        assertEquals("Depot", failure.taskChain)
        assertEquals(1, failure.taskId)
        assertEquals(1_000L, failure.startedAtMs)
        assertEquals(1_400L, failure.durationMs)
        assertEquals(2, failure.failedSubTasks)
        assertEquals("DepotRecognitionTask", failure.terminal?.subtask)
        assertEquals("DepotBegin", failure.node?.first)
        assertEquals("", failure.node?.preTask)
        assertNull(failure.exception)
        assertEquals(mapOf("client_type" to "Official"), failure.options)
        assertEquals(mapOf("run.id" to "r1", "run.kind" to "chain"), failure.tags)
        assertEquals(transaction.children.single().span, failure.span)

        val (processSpan, ownerSpan) = transaction.children.single().children
        assertEquals(RunTracer.SUBTASK_OP, processSpan.op)
        assertEquals("ProcessTask", processSpan.description)
        assertEquals("DepotBegin", processSpan.data["first"])
        assertEquals("DepotRecognitionTask", ownerSpan.description)
        assertEquals(SpanStatus.INTERNAL_ERROR, ownerSpan.status)
    }

    @Test
    fun `任务链直接死在 ProcessTask 上时它自己就是节点`() {
        start(1, "Award")
        processError(1, "Award", first = "AwardBegin", preTask = "ReturnButton")
        emit(AsstMsg.TaskChainError, """{"taskchain":"Award","taskid":1}""")

        val failure = failures.single()
        assertEquals("ProcessTask", failure.terminal?.subtask)
        assertEquals(failure.terminal, failure.node)
        assertEquals("ReturnButton", failure.node?.preTask)
    }

    @Test
    fun `后续失败标上本轮第一条失败的任务链，成功的不算`() {
        start(1, "StartUp")
        processError(1, "StartUp", first = "StartUpBegin")
        emit(AsstMsg.TaskChainError, """{"taskchain":"StartUp","taskid":1}""")
        start(2, "Recruit")
        emit(AsstMsg.TaskChainCompleted, """{"taskchain":"Recruit","taskid":2}""")
        start(3, "Fight")
        processError(3, "Fight", first = "StageBegin")
        emit(AsstMsg.TaskChainError, """{"taskchain":"Fight","taskid":3}""")
        start(4, "Mall")
        processError(4, "Mall", first = "MallBegin")
        emit(AsstMsg.TaskChainError, """{"taskchain":"Mall","taskid":4}""")

        assertEquals(listOf(null, "StartUp", "StartUp"), failures.map { it.priorFailure })
        assertEquals(
            listOf(NO_PRIOR_FAILURE, "StartUp", "StartUp", "StartUp"),
            transaction.children.map { it.data[PRIOR_FAILURE_KEY] },
        )
    }

    @Test
    fun `任务 Span 抄上开跑时的环境 tag`() {
        environment["maacore.version"] = "v6.19.0"
        start(1, "StartUp")
        emit(AsstMsg.TaskChainCompleted, """{"taskchain":"StartUp","taskid":1}""")
        // 资源中途更新，后面的任务按新版本记
        environment["resource.version"] = "2026-10-09 23:31:13.000"
        start(2, "Fight")

        val (startUp, fight) = transaction.children
        assertEquals("v6.19.0", startUp.data["maacore.version"])
        assertNull(startUp.data["resource.version"])
        assertEquals("2026-10-09 23:31:13.000", fight.data["resource.version"])
    }

    @Test
    fun `SSS 任务名里的实例地址在入口就去掉`() {
        start(1, "SSSCopilot")
        processError(1, "SSSCopilot", first = "0xb400007401ddbb10@SSSBegin", preTask = "0xb40000784f028880@SSSStart")
        emit(AsstMsg.TaskChainError, """{"taskchain":"SSSCopilot","taskid":1}""")

        val failure = failures.single()
        assertEquals("SSSBegin", failure.node?.first)
        assertEquals("SSSStart", failure.node?.preTask)
        assertEquals("SSSBegin", transaction.children.single().children.single().data["first"])
    }

    @Test
    fun `隔得久的 ProcessTask 错误不算在致命子任务头上`() {
        start(1, "Recruit")
        processError(1, "Recruit", first = "RecruitProbe")
        now += RunTracer.NODE_WINDOW_MS + 1
        subTaskError(1, "Recruit", "AutoRecruitTask", extra = ""","why":"NoPermit"""")
        emit(AsstMsg.TaskChainError, """{"taskchain":"Recruit","taskid":1}""")

        val failure = failures.single()
        assertEquals("AutoRecruitTask", failure.terminal?.subtask)
        assertEquals("NoPermit", failure.terminal?.why)
        assertNull(failure.node)
    }

    @Test
    fun `被忽略的子任务错误之后另一个子任务致命`() {
        start(1, "Fight")
        processError(1, "Fight", first = "ReportProbe")
        subTaskError(1, "Fight", "ReportToPenguinStats")
        now += 60_000
        subTaskError(1, "Fight", "StageNavigationTask")
        emit(AsstMsg.TaskChainError, """{"taskchain":"Fight","taskid":1}""")

        val failure = failures.single()
        assertEquals("StageNavigationTask", failure.terminal?.subtask)
        assertNull(failure.node)
        assertEquals(3, failure.failedSubTasks)
    }

    @Test
    fun `Core 抛异常时没有子任务错误，带上异常种类`() {
        start(1, "Fight")
        emit(AsstMsg.TaskChainError, """{"taskchain":"Fight","taskid":1,"details":{"error":"OutOfMemory"}}""")

        val failure = failures.single()
        assertNull(failure.terminal)
        assertNull(failure.node)
        assertEquals("OutOfMemory", failure.exception)
    }

    @Test
    fun `子任务细节里的 what 也认`() {
        start(1, "Infrast")
        emit(
            AsstMsg.SubTaskError,
            """{"class":"asst::InfrastInfoTask","subtask":"InfrastInfoTask","taskchain":"Infrast","taskid":1,""" +
                """"details":{"what":"FacilityLayoutRecognitionFailed"}}""",
        )
        emit(AsstMsg.TaskChainError, """{"taskchain":"Infrast","taskid":1}""")

        assertEquals("FacilityLayoutRecognitionFailed", failures.single().terminal?.what)
    }

    @Test
    fun `成功的任务链不出失败摘要`() {
        start(1, "Fight")
        processError(1, "Fight", first = "Retry")
        emit(AsstMsg.TaskChainCompleted, """{"taskchain":"Fight","taskid":1}""")
        emit(AsstMsg.AllTasksCompleted)

        assertTrue(failures.isEmpty())
        assertEquals(SpanStatus.OK, transaction.status)
        assertEquals("success", transaction.data["result"])
    }

    /** 用户中途停止时 Core 只发一条 TaskChainStopped，不跟 AllTasksCompleted */
    @Test
    fun `停止时进行中的任务链记为取消`() {
        start(1, "Fight")
        processError(1, "Fight", first = "Retry")
        emit(AsstMsg.TaskChainStopped, """{"taskchain":"Fight","taskid":1}""")

        assertTrue(failures.isEmpty())
        assertEquals(SpanStatus.CANCELLED, transaction.children.single().status)
        assertEquals(SpanStatus.CANCELLED, transaction.status)
        assertEquals("cancelled", transaction.data["result"])
        assertNull(tracer.runId)
    }

    /** 最后一条任务链跑完后的收尾窗口里停止，Core 先发 AllTasksCompleted 再发 TaskChainStopped */
    @Test
    fun `跑完之后才到的停止不改写结局`() {
        start(1, "Fight")
        emit(AsstMsg.TaskChainCompleted, """{"taskchain":"Fight","taskid":1}""")
        emit(AsstMsg.AllTasksCompleted)
        emit(AsstMsg.TaskChainStopped, """{"taskchain":"Fight","taskid":1}""")

        assertEquals(SpanStatus.OK, transaction.status)
        assertEquals(1, transactions.size)
    }

    /** Core 内存不足时先报 TaskChainError 再报 TaskChainStopped */
    @Test
    fun `内存不足的任务链照样出失败摘要`() {
        start(1, "Fight")
        emit(AsstMsg.TaskChainError, """{"taskchain":"Fight","taskid":1,"details":{"error":"OutOfMemory"}}""")
        emit(AsstMsg.TaskChainStopped, """{"taskchain":"Fight","taskid":1}""")

        assertEquals("OutOfMemory", failures.single().exception)
        assertEquals(SpanStatus.INTERNAL_ERROR, transaction.children.single().status)
    }

    @Test
    fun `提权进程死亡时整轮记为中止`() {
        start(1, "Fight")
        assertEquals("Fight", tracer.currentTaskChain)

        tracer.abort(SpanStatus.ABORTED)

        assertEquals(SpanStatus.ABORTED, transaction.children.single().status)
        assertEquals(SpanStatus.ABORTED, transaction.status)
        assertEquals("aborted", transaction.data["result"])
    }

    @Test
    fun `还没开跑就失败不出事务`() {
        assertEquals("r1", tracer.runId)
        assertEquals(mapOf("run.id" to "r1", "run.kind" to "chain"), tracer.tags)

        tracer.abort(SpanStatus.INTERNAL_ERROR)

        assertTrue(transactions.isEmpty())
        assertNull(tracer.runId)
    }

    @Test
    fun `开跑前的实例销毁不清掉刚登记的这一轮`() {
        emit(AsstMsg.Destroyed)
        start(1, "StartUp")

        assertEquals("r1", transaction.tags["run.id"])
        assertEquals(2, transaction.data["task_count"])
    }

    @Test
    fun `没有登记时照样记事务`() {
        tracer.reset()
        start(5, "Award")
        emit(AsstMsg.TaskChainCompleted, """{"taskchain":"Award","taskid":5}""")
        emit(AsstMsg.AllTasksCompleted)

        assertNull(transaction.data["task_count"])
        assertEquals(SpanStatus.OK, transaction.children.single().status)
        assertEquals(SpanStatus.OK, transaction.status)
    }

    @Test
    fun `上一轮没收尾时新一轮先按取消结掉`() {
        start(1, "Fight")
        tracer.begin("r2", "aux", listOf("Copilot"))
        start(1, "Copilot")

        assertEquals(2, transactions.size)
        assertEquals(SpanStatus.CANCELLED, transactions[0].status)
        assertEquals("r2", transactions[1].tags["run.id"])
        assertNull(transactions[1].children.single().status)
    }

    @Test
    fun `别的任务链的终局不落到当前任务上`() {
        start(1, "Fight")
        emit(AsstMsg.TaskChainError, """{"taskchain":"Depot","taskid":9}""")

        assertTrue(failures.isEmpty())
        assertNull(transaction.children.single().status)
    }

    /** Core 里临时构造的 ProcessTask（助战列表、快速编队）没设 task id，报上来恒为 0 */
    @Test
    fun `taskid 为 0 的子任务错误归到当前任务链`() {
        start(3, "Fight")
        processError(0, "Fight", first = "SupportList-DetailPanel-Confirm")
        subTaskError(3, "Fight", "BattleFormationTask")
        emit(AsstMsg.TaskChainError, """{"taskchain":"Fight","taskid":3}""")

        val failure = failures.single()
        assertEquals("BattleFormationTask", failure.terminal?.subtask)
        assertEquals("SupportList-DetailPanel-Confirm", failure.node?.first)
    }

    @Test
    fun `子任务错误的 Span 有上限，失败计数不受限`() {
        start(1, "Roguelike")
        repeat(RunTracer.MAX_TRACED_SUBTASKS_PER_TASK + 5) { processError(1, "Roguelike", first = "Node$it") }
        emit(AsstMsg.TaskChainError, """{"taskchain":"Roguelike","taskid":1}""")

        assertEquals(RunTracer.MAX_TRACED_SUBTASKS_PER_TASK, transaction.children.single().children.size)
        val failure = failures.single()
        assertEquals(RunTracer.MAX_TRACED_SUBTASKS_PER_TASK + 5, failure.failedSubTasks)
        assertEquals(RunTracer.MAX_RECENT_SIGNALS, failure.recent.size)
        assertEquals("Node${RunTracer.MAX_TRACED_SUBTASKS_PER_TASK + 4}", failure.node?.first)
    }
}
