package com.aliothmoon.maameow.domain.service

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主任务链运行时长上限，对齐 WPF RunningState.RunDeadline
 *
 * 截止时间用 elapsedRealtime，改系统时间不影响；每轮只触发一次
 */
class RunDeadlineGuard(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    companion object {
        private const val TICK_MS = 60_000L
    }

    private val _deadline = MutableStateFlow<Long?>(null)

    /** 本轮截止时间（[clock] 时基），未计时为 null */
    val deadline: StateFlow<Long?> = _deadline.asStateFlow()

    private var job: Job? = null

    fun arm(limitMinutes: Int, onReached: suspend () -> Unit) {
        val at = clock() + limitMinutes * 60_000L
        // 协程到点要拿同一把锁，job 登记先于置空
        synchronized(this) {
            job?.cancel()
            _deadline.value = at
            job = scope.launch {
                while (true) {
                    val remaining = at - clock()
                    if (remaining <= 0) break
                    delay(remaining.coerceAtMost(TICK_MS))
                }
                // 摘掉自己再回调，回调里的 stop 会 disarm，不能把正在停止的协程取消掉
                val fired = synchronized(this@RunDeadlineGuard) {
                    if (_deadline.value != at) return@synchronized false
                    _deadline.value = null
                    job = null
                    true
                }
                if (fired) onReached()
            }
        }
    }

    fun disarm() {
        synchronized(this) {
            job?.cancel()
            job = null
            _deadline.value = null
        }
    }
}
