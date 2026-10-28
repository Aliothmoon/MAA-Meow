package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.domain.state.MaaExecutionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** 任务结束唯一订阅点，避免多处各自 collect 边沿 */
class TaskEndRegistry(
    private val compositionService: MaaCompositionService,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /** 全局「任务自动结束时关闭游戏」；放进程级，没拉起界面的轮次也要关 */
    private val closeGameOnEnd: () -> Boolean = { false },
) {
    enum class Reason {
        /** RUNNING → IDLE/ERROR */
        NATURAL,

        /** STOPPING → IDLE/ERROR，用户手动停止 */
        MANUAL,

        /** STOPPING → IDLE/ERROR，回调侧（掉线等）中止 */
        ABORTED,

        /** STOPPING → IDLE/ERROR，到达运行时长上限，按自动结束执行收尾 */
        DURATION_LIMIT,
    }

    fun interface PendingAction {
        suspend fun run(reason: Reason)
    }

    private val _taskEnded = MutableSharedFlow<Reason>(extraBufferCapacity = 4)

    /** 常驻广播，随订阅方 scope 自动取消 */
    val taskEnded: SharedFlow<Reason> = _taskEnded.asSharedFlow()

    private val pending = AtomicReference<PendingAction?>(null)
    private val started = AtomicBoolean(false)

    /** 收集协程处理到的状态与最近一次结束原因，补跑据此判断 */
    @Volatile
    private var observed: MaaExecutionState? = null

    @Volatile
    private var lastReason = Reason.NATURAL

    /** 登记本次收尾；重复登记覆盖前一次 */
    fun armOnce(action: PendingAction) {
        pending.set(action)
        // 边沿已过：收集协程处理过的按那次原因补跑，没处理到的交给 dispatch；与 dispatch 共用 getAndSet
        val now = compositionService.state.value
        if (isTerminal(now) && (observed == now || !started.get())) {
            val reason = lastReason
            scope.launch { runPending(reason) }
        }
    }

    /** 抢占前撤掉，防止旧 autoSleep 落在新一轮 */
    fun disarmOnce() {
        pending.set(null)
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        var prev = compositionService.state.value
        observed = prev
        scope.launch {
            compositionService.state.collect { cur ->
                val reason = reasonFor(prev, cur)
                // 先记下再 dispatch，armOnce 才不会漏补跑
                if (isTerminal(cur) && !isTerminal(prev)) lastReason = reason ?: Reason.NATURAL
                observed = cur
                prev = cur
                reason?.let { dispatch(it) }
            }
        }
        Timber.i("TaskEndRegistry: started observing task end")
    }

    private fun isTerminal(state: MaaExecutionState) =
        state == MaaExecutionState.IDLE || state == MaaExecutionState.ERROR

    private fun reasonFor(prev: MaaExecutionState, cur: MaaExecutionState): Reason? {
        if (!isTerminal(cur)) return null
        return when (prev) {
            MaaExecutionState.RUNNING -> Reason.NATURAL
            MaaExecutionState.STOPPING -> when (compositionService.lastStopOrigin) {
                MaaCompositionService.StopOrigin.CALLBACK -> Reason.ABORTED
                MaaCompositionService.StopOrigin.USER -> Reason.MANUAL
                MaaCompositionService.StopOrigin.RUN_DURATION_LIMIT -> Reason.DURATION_LIMIT
            }
            else -> null
        }
    }

    private suspend fun dispatch(reason: Reason) {
        Timber.i("TaskEndRegistry: task ended, reason=%s", reason)
        runPending(reason)
        // 手动停止不关游戏，其余结束（自然完成 / 掉线中止 / 到达时长上限）都关
        if (reason != Reason.MANUAL && closeGameOnEnd()) {
            Timber.i("TaskEndRegistry: auto closing game")
            try {
                compositionService.stopVirtualDisplay()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "TaskEndRegistry: close game failed")
            }
        }
        _taskEnded.tryEmit(reason)
    }

    /** 只跑 pending，不广播（补跑与 dispatch 共用） */
    private suspend fun runPending(reason: Reason) {
        val action = pending.getAndSet(null) ?: return
        try {
            action.run(reason)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "TaskEndRegistry: pending action failed")
        }
    }
}
