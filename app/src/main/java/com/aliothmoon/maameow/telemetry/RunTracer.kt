package com.aliothmoon.maameow.telemetry

import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.maa.AsstMsg
import io.sentry.ISpan
import io.sentry.SpanStatus

/**
 * 把 MaaCore 的回调翻成一轮一条 Transaction、每条任务链一条 Span，结构对应 MaaFwApp `RunTracer`
 *
 * 事务开在首个任务链真正开跑时，准备阶段失败不算一轮
 * Span 只进 Trace 不进 Issues，任务链终态失败另经 [onTaskFailure] 交出 [TaskFailure] 去发 Error Event
 * 不给 `SubTaskStart` 建 Span：`ProcessTask` 每命中一个任务就发一条，肉鸽一轮数千条
 *
 * 不是线程安全的，调用方负责串行
 */
internal class RunTracer(
    private val startTransaction: (name: String, op: String) -> ISpan,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Scope 上的环境 tag 只落到事务上，任务 Span 拿不到；开跑时抄一份，失败率才能按版本与环境切 */
    private val environment: () -> Map<String, String> = { emptyMap() },
    private val onTaskStarted: () -> Unit,
    private val onTaskFailure: (TaskFailure) -> Unit,
) {

    private class PendingRun(val runId: String, val kind: String, val tasks: List<String>)

    private class TaskTrace(
        val taskId: Int,
        val chain: String,
        val span: ISpan,
        val startedAt: Long,
        val options: Map<String, String>,
    ) {
        var failedSubTasks = 0
        var tracedSubTasks = 0

        /** 只留最近几条：致命的那条总在最后 */
        val recent = ArrayDeque<FailureSignal>()
    }

    private class RunTrace(
        val runId: String,
        val transaction: ISpan,
        /** 事务与本轮每条事件共用的 tag */
        val tags: Map<String, String>,
    ) {
        var task: TaskTrace? = null
        var failedTasks = 0

        /** 本轮第一条失败的任务链：它没把游戏带回主界面，后面的任务常在第一步就跟着失败 */
        var firstFailedChain: String? = null
    }

    private var pending: PendingRun? = null
    private var run: RunTrace? = null

    /** Core 的 task id → 入队时的参数摘要 */
    private val options = mutableMapOf<Int, Map<String, String>>()

    /** 还没登记或已经收尾为 null */
    val runId: String? get() = run?.runId ?: pending?.runId

    val tags: Map<String, String>
        get() = run?.tags ?: pending?.let { tagsOf(it.runId, it.kind) }.orEmpty()

    val currentTaskChain: String? get() = run?.task?.chain

    fun begin(runId: String, kind: String, tasks: List<String>) {
        // 上一轮没等到终局，按取消收掉，别让它挂到新一轮上
        run?.let { finish(it, SpanStatus.CANCELLED) }
        options.clear()
        pending = PendingRun(runId, kind, tasks)
    }

    fun register(taskId: Int, summary: Map<String, String>) {
        options[taskId] = summary
    }

    fun onCallback(message: AsstMsg, details: JSONObject?) {
        when (message) {
            AsstMsg.TaskChainStart -> details?.let(::onTaskChainStart)
            AsstMsg.SubTaskError -> details?.let(::onSubTaskError)
            AsstMsg.TaskChainError -> details?.let(::onTaskChainError)
            AsstMsg.TaskChainCompleted -> details?.let(::onTaskChainCompleted)
            // 不碰刚登记还没开跑的那轮：启动时换资源会销毁旧实例
            AsstMsg.TaskChainStopped, AsstMsg.Destroyed -> run?.let { finish(it, SpanStatus.CANCELLED) }
            AsstMsg.AllTasksCompleted -> run?.let {
                finish(it, if (it.failedTasks > 0) SpanStatus.INTERNAL_ERROR else SpanStatus.OK)
            }
            else -> Unit
        }
    }

    /** 启动失败、提权进程死亡 */
    fun abort(status: SpanStatus) {
        pending = null
        run?.let { finish(it, status) }
    }

    /** 遥测关掉时客户端已经关了，进行中的这轮直接丢掉，不再往旧客户端上结 */
    fun reset() {
        pending = null
        run = null
        options.clear()
    }

    private fun onTaskChainStart(details: JSONObject) {
        val trace = run ?: startRun()
        // 上一条任务链的终局丢了，按取消收掉，别让它把子任务错误吞到新任务之前
        trace.task?.let { finishTask(it, SpanStatus.CANCELLED) }

        val taskId = details.getIntValue("taskid", 0)
        val chain = details.getString("taskchain") ?: UNKNOWN_CHAIN
        val summary = options[taskId].orEmpty()
        val span = trace.transaction.startChild(TASK_OP, chain).apply {
            environment().forEach { (key, value) -> setData(key, value) }
            setData("run_id", trace.runId)
            setData("task", chain)
            setData("task_id", taskId)
            setData(PRIOR_FAILURE_KEY, trace.firstFailedChain ?: NO_PRIOR_FAILURE)
            summary.forEach { (key, value) -> setData("option.$key", value) }
        }
        trace.task = TaskTrace(taskId, chain, span, clock(), summary)
        onTaskStarted()
    }

    private fun onSubTaskError(details: JSONObject) {
        // 不比 taskid：Core 里临时构造的 ProcessTask（助战列表、快速编队等）报的恒为 0，任务链串行，一律归当前这条
        val task = run?.task ?: return
        val subtask = details.getString("subtask") ?: return
        val inner = details.getJSONObject("details")
        val signal = FailureSignal(
            subtask = subtask,
            className = details.getString("class"),
            first = details.getJSONArray("first")?.let { stripTaskAddress(it.firstOrNull()?.toString().orEmpty()) },
            preTask = details.getString("pre_task")?.let(::stripTaskAddress),
            what = details.getString("what") ?: inner?.getString("what"),
            why = details.getString("why") ?: inner?.getString("why"),
            atMs = clock(),
        )
        task.failedSubTasks++
        task.recent.addLast(signal)
        if (task.recent.size > MAX_RECENT_SIGNALS) task.recent.removeFirst()

        task.tracedSubTasks++
        if (task.tracedSubTasks > MAX_TRACED_SUBTASKS_PER_TASK) return
        task.span.startChild(SUBTASK_OP, signal.subtask).apply {
            // 冗余任务链名：Sentry 的 span 查询不能沿父子关系往上过滤
            setData("task", task.chain)
            setData("task_id", task.taskId)
            signal.className?.let { setData("class", it) }
            signal.first?.let { setData("first", it) }
            signal.preTask?.let { setData("pre_task", it) }
            signal.what?.let { setData("what", it) }
            finish(SpanStatus.INTERNAL_ERROR)
        }
    }

    private fun onTaskChainError(details: JSONObject) {
        val trace = run ?: return
        val task = currentTask(details) ?: return
        trace.task = null
        trace.failedTasks++
        val prior = trace.firstFailedChain
        if (prior == null) trace.firstFailedChain = task.chain
        finishTask(task, SpanStatus.INTERNAL_ERROR)

        val signals = task.recent.toList()
        val terminal = signals.lastOrNull()
        onTaskFailure(
            TaskFailure(
                runId = trace.runId,
                taskChain = task.chain,
                taskId = task.taskId,
                startedAtMs = task.startedAt,
                durationMs = clock() - task.startedAt,
                failedSubTasks = task.failedSubTasks,
                terminal = terminal,
                node = nodeOf(signals),
                // Core 写在 details.error，WPF 读的是根级，两处都兼容
                exception = details.getJSONObject("details")?.getString("error") ?: details.getString("error"),
                recent = signals,
                options = task.options,
                tags = trace.tags,
                span = task.span,
                priorFailure = prior,
            )
        )
    }

    private fun onTaskChainCompleted(details: JSONObject) {
        val trace = run ?: return
        val task = currentTask(details) ?: return
        trace.task = null
        finishTask(task, SpanStatus.OK)
    }

    /**
     * 致命子任务卡在哪个 `ProcessTask` 上：它自己就是，或者是紧挨在它前面报错的那个
     *
     * 隔得久的不算：子任务可以吞掉里面 `ProcessTask` 的失败继续跑，之后因别的原因出错
     */
    private fun nodeOf(signals: List<FailureSignal>): FailureSignal? {
        val terminal = signals.lastOrNull() ?: return null
        if (terminal.isProcessTask) return terminal
        return signals.getOrNull(signals.size - 2)
            ?.takeIf { it.isProcessTask && terminal.atMs - it.atMs <= NODE_WINDOW_MS }
    }

    /** 任务链级回调的 id 对不上，说明是别的轮次漏过来的 */
    private fun currentTask(details: JSONObject): TaskTrace? =
        run?.task?.takeIf { it.taskId == details.getIntValue("taskid", it.taskId) }

    private fun startRun(): RunTrace {
        // 没登记就开跑（遥测中途打开）也照样出事务，只是缺任务清单与选项
        val info = pending.also { pending = null }
        val runId = info?.runId ?: "$UNTRACKED_RUN_PREFIX${clock()}"
        val tags = tagsOf(runId, info?.kind)
        val transaction = startTransaction(RUN_NAME, RUN_OP).apply {
            setData("run_id", runId)
            if (info != null) {
                setData("task_count", info.tasks.size)
                if (info.tasks.isNotEmpty()) setData("tasks", info.tasks.joinToString(","))
            }
            tags.forEach { (key, value) -> setTag(key, value) }
        }
        return RunTrace(runId, transaction, tags).also { run = it }
    }

    private fun tagsOf(runId: String, kind: String?): Map<String, String> =
        listOfNotNull("run.id" to runId, kind?.let { "run.kind" to it }).toMap()

    /** 未收尾的任务链（中止时还在跑的那条）一并结掉，整轮也跟着算没跑完 */
    private fun finish(trace: RunTrace, status: SpanStatus) {
        val interrupted = trace.task
        val runStatus = if (interrupted != null && status.isCompletion()) SpanStatus.CANCELLED else status
        if (interrupted != null) {
            finishTask(interrupted, if (runStatus == SpanStatus.ABORTED) SpanStatus.ABORTED else SpanStatus.CANCELLED)
        }
        trace.task = null
        trace.transaction.setData("result", resultLabel(runStatus))
        trace.transaction.finish(runStatus)
        if (run === trace) run = null
        options.clear()
    }

    private fun finishTask(task: TaskTrace, status: SpanStatus) {
        task.span.setData("result", resultLabel(status))
        task.span.finish(status)
    }

    private fun SpanStatus.isCompletion() = this == SpanStatus.OK || this == SpanStatus.INTERNAL_ERROR

    private fun resultLabel(status: SpanStatus): String = when (status) {
        SpanStatus.OK -> "success"
        SpanStatus.CANCELLED -> "cancelled"
        SpanStatus.ABORTED -> "aborted"
        else -> "failure"
    }

    companion object {
        const val RUN_NAME = "maameow.task_run"
        const val RUN_OP = "maameow.run"
        const val TASK_OP = "maameow.task"
        const val SUBTASK_OP = "maameow.subtask"

        const val MAX_TRACED_SUBTASKS_PER_TASK = 100
        const val MAX_RECENT_SIGNALS = 10

        /** `ProcessTask` 报错到包着它的子任务报错，中间隔着一次任务延迟与失败处理 */
        const val NODE_WINDOW_MS = 10_000L

        private const val UNKNOWN_CHAIN = "Unknown"
        private const val UNTRACKED_RUN_PREFIX = "untracked-"
    }
}
