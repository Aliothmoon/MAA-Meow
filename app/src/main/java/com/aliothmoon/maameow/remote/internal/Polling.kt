package com.aliothmoon.maameow.remote.internal

import android.os.SystemClock

/** 亮屏、keyguard 这类交互 100ms 的粒度足够 */
const val DEFAULT_POLL_INTERVAL_MS = 100L

/** 提权进程里的状态变更只能靠轮询确认，唤醒/解锁/录制共用这一份 */
internal inline fun pollUntil(
    timeoutMs: Long,
    intervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    cond: () -> Boolean,
): Boolean {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (SystemClock.elapsedRealtime() < deadline) {
        if (cond()) return true
        Thread.sleep(intervalMs)
    }
    return cond()
}
