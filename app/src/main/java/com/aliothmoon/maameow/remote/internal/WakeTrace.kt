package com.aliothmoon.maameow.remote.internal

import android.os.SystemClock
import com.aliothmoon.maameow.third.Ln

/**
 * 一次唤醒解锁的步骤与耗时，解锁失败时交给 App 写进触发日志
 * 触发日志会随遥测上传：不记 PIN、PIN 位数、轨迹坐标与步数
 */
internal class WakeTrace {
    val startMs = SystemClock.elapsedRealtime()
    private val lines = mutableListOf<String>()

    fun i(msg: String) {
        Ln.i("$TAG: $msg")
        note(msg)
    }

    fun w(msg: String) {
        Ln.w("$TAG: $msg")
        note(msg)
    }

    /** 只进 trace，不重复打日志 */
    fun note(msg: String) {
        if (lines.size < MAX_LINES) lines += "+${SystemClock.elapsedRealtime() - startMs}ms $msg"
    }

    fun render(): String = lines.joinToString("\n")

    private companion object {
        const val TAG = "WakeUnlock"
        const val MAX_LINES = 40
    }
}
