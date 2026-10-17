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

/** 统一处理服务登记的任务结束事件，页面与自动收尾共用同一份运行归属。 */
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

    /** 标识一次实际启动；只保留本轮结果，晚返回的启动调用也能检查是否已经结束。 */
    class Run internal constructor(val id: Long) {
        @Volatile
        var endEvent: EndEvent? = null
            private set

        // 生产调用由服务状态锁串行化，结果一经保存就不再被重复回调替换。
        internal fun finish(
            state: MaaExecutionState,
            previous: MaaExecutionState,
            origin: MaaCompositionService.StopOrigin,
        ): EndEvent {
            endEvent?.let { return it }
            val reason = if (previous == MaaExecutionState.STOPPING) {
                when (origin) {
                    MaaCompositionService.StopOrigin.USER -> Reason.MANUAL
                    MaaCompositionService.StopOrigin.CALLBACK -> Reason.ABORTED
                    MaaCompositionService.StopOrigin.RUN_DURATION_LIMIT -> Reason.DURATION_LIMIT
                }
            } else Reason.NATURAL
            return EndEvent(this, reason, state).also { endEvent = it }
        }
    }

    data class EndEvent(
        val run: Run,
        val reason: Reason,
        val state: MaaExecutionState,
    )

    fun interface PendingAction {
        suspend fun run(reason: Reason)
    }

    private val _taskEnded = MutableSharedFlow<EndEvent>(extraBufferCapacity = 4)

    /** 常驻广播，随订阅方 scope 自动取消 */
    val taskEnded: SharedFlow<EndEvent> = _taskEnded.asSharedFlow()

    private data class Pending(val run: Run?, val action: PendingAction)

    private val pending = AtomicReference<Pending?>(null)
    private val started = AtomicBoolean(false)

    /** 登记本次收尾；重复登记覆盖前一次 */
    fun armOnce(action: PendingAction) {
        val run = compositionService.currentRun
        val registration = Pending(run, action)
        pending.set(registration)
        // 边沿可能已过，补跑一次；与 dispatch 共用同一登记的 compareAndSet
        val now = compositionService.state.value
        if (run?.endEvent != null || (run == null &&
                (now == MaaExecutionState.IDLE || now == MaaExecutionState.ERROR))
        ) {
            scope.launch { runPending(run?.endEvent?.reason ?: Reason.NATURAL, registration) }
        }
    }

    /** 抢占前撤掉，防止旧 autoSleep 落在新一轮 */
    fun disarmOnce() {
        pending.set(null)
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            // 服务在终态入口排队，不能再由可合并的 StateFlow 重建结束边沿。
            compositionService.taskEndEvents.collect { event ->
                dispatch(event)
            }
        }
        Timber.i("TaskEndRegistry: started observing task end")
    }

    private suspend fun dispatch(event: EndEvent) {
        val reason = event.reason
        Timber.i("TaskEndRegistry: run=%s ended, reason=%s", event.run.id, reason)
        // 延迟事件不能领取后来为另一轮登记的息屏/关闭游戏操作。
        pending.get()?.takeIf { it.run === event.run }?.let { runPending(reason, it) }
        // 手动停止不关游戏，其余结束（自然完成 / 掉线中止 / 到达时长上限）都关
        if (reason != Reason.MANUAL && closeGameOnEnd()
            && compositionService.currentRun === event.run
        ) {
            Timber.i("TaskEndRegistry: auto closing game")
            try {
                compositionService.stopVirtualDisplay()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "TaskEndRegistry: close game failed")
            }
        }
        // 慢订阅者可以延迟消费，但不能让页面漏掉所属运行的结束。
        _taskEnded.emit(event)
    }

    /** 只跑 pending，不广播（补跑与 dispatch 共用） */
    private suspend fun runPending(reason: Reason, registration: Pending) {
        if (!pending.compareAndSet(registration, null)) return
        try {
            registration.action.run(reason)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "TaskEndRegistry: pending action failed")
        }
    }
}
