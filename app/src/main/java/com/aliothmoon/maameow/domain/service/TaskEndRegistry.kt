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
) {
    enum class Reason {
        /** RUNNING → IDLE/ERROR */
        NATURAL,
        /** STOPPING → IDLE/ERROR */
        MANUAL,
    }

    fun interface PendingAction {
        suspend fun run(reason: Reason)
    }

    private val _taskEnded = MutableSharedFlow<Reason>(extraBufferCapacity = 4)

    /** 常驻广播，随订阅方 scope 自动取消 */
    val taskEnded: SharedFlow<Reason> = _taskEnded.asSharedFlow()

    private val pending = AtomicReference<PendingAction?>(null)
    private val started = AtomicBoolean(false)

    /** 登记本次收尾；重复登记覆盖前一次 */
    fun armOnce(action: PendingAction) {
        pending.set(action)
        // 边沿可能已过，补跑一次；与 dispatch 共用 getAndSet
        val now = compositionService.state.value
        if (now == MaaExecutionState.IDLE || now == MaaExecutionState.ERROR) {
            scope.launch { runPending(Reason.NATURAL) }
        }
    }

    /** 抢占前撤掉，防止旧 autoSleep 落在新一轮 */
    fun disarmOnce() {
        pending.set(null)
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            var prev = compositionService.state.value
            compositionService.state.collect { cur ->
                reasonFor(prev, cur)?.let { dispatch(it) }
                prev = cur
            }
        }
        Timber.i("TaskEndRegistry: started observing task end")
    }

    private fun reasonFor(prev: MaaExecutionState, cur: MaaExecutionState): Reason? {
        if (cur != MaaExecutionState.IDLE && cur != MaaExecutionState.ERROR) return null
        return when (prev) {
            MaaExecutionState.RUNNING -> Reason.NATURAL
            MaaExecutionState.STOPPING -> Reason.MANUAL
            else -> null
        }
    }

    private suspend fun dispatch(reason: Reason) {
        Timber.i("TaskEndRegistry: task ended, reason=%s", reason)
        runPending(reason)
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
