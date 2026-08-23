package com.aliothmoon.maameow.data.notification.live

import android.content.Context
import com.aliothmoon.maameow.domain.notification.FocusSequence

/** 岛更新序号，跟随墙上时钟；进度 1Hz 刷新不逐次落盘，只为兜住时钟回拨才周期性持久化 */
class FocusSequenceStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cache = mutableMapOf<Int, Long>()
    private val persisted = mutableMapOf<Int, Long>()

    @Synchronized
    fun next(notifyId: Int, nowMs: Long = System.currentTimeMillis()): Long {
        val key = keyOf(notifyId)
        val last = cache[notifyId] ?: prefs.getLong(key, 0L)
        val seq = FocusSequence.next(last, nowMs)
        cache[notifyId] = seq
        val lastWritten = persisted[notifyId] ?: prefs.getLong(key, 0L)
        if (seq - lastWritten >= PERSIST_STEP_MS) {
            persisted[notifyId] = seq
            prefs.edit().putLong(key, seq).apply()
        }
        return seq
    }

    private fun keyOf(notifyId: Int): String = "seq_$notifyId"

    private companion object {
        const val PREFS_NAME = "live_focus_seq"
        const val PERSIST_STEP_MS = 30_000L
    }
}
