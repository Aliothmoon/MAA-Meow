package com.aliothmoon.maameow.telemetry

import android.content.Context
import androidx.core.content.edit
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.serialization.Serializable

/**
 * 提权进程被杀，凶手只记在系统日志里，死者自己读不了
 *
 * 死亡事件发出时记下事件 ID、死亡时刻与 pid，下次连上新进程时让它翻那段日志，挂回原事件；落盘是因为 App 进程未必活到那时
 */
internal class ServiceDeathForensics(
    private val store: Store,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    @Serializable
    data class Pending(
        val eventId: String,
        val reason: String,
        val runId: String?,
        val attributes: Map<String, String>,
        val traceId: String?,
        val spanId: String?,
        val diedAtMs: Long,
        val pid: Int,
    ) {
        val sinceMs: Long get() = diedAtMs - LOOK_BACK_MS
        val untilMs: Long get() = diedAtMs + LOOK_AHEAD_MS

        fun logContext(delayMs: Long) = DiagnosticLogContext(
            eventId = eventId,
            reason = reason,
            runId = runId,
            attributes = attributes + ("forensics.delay_s" to (delayMs / 1000).toString()),
            traceId = traceId,
            spanId = spanId,
        )
    }

    interface Store {
        fun load(): Pending?
        fun save(pending: Pending)
        fun clear()
    }

    /** 只留最近一次，连死几次多是同一个原因 */
    @Synchronized
    fun remember(pending: Pending) = store.save(pending)

    /** 同一个 pid 说明它没死（进程被替换也会报死亡）；隔太久系统日志早被冲掉 */
    @Synchronized
    fun take(connectedPid: Int?): Pending? {
        val pending = store.load() ?: return null
        if (connectedPid == null) return null
        store.clear()
        if (pending.pid == connectedPid) return null
        if (clock() - pending.diedAtMs > MAX_AGE_MS) return null
        return pending
    }

    class PrefsStore(context: Context) : Store {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override fun load(): Pending? = prefs.getString(KEY_PENDING, null)?.let {
            runCatching { JsonUtils.common.decodeFromString(Pending.serializer(), it) }.getOrNull()
        }

        override fun save(pending: Pending) = prefs.edit {
            putString(KEY_PENDING, JsonUtils.common.encodeToString(Pending.serializer(), pending))
        }

        override fun clear() = prefs.edit { remove(KEY_PENDING) }
    }

    private companion object {
        /** 往回多看一分钟：内存吃紧、冻结这类前兆也在里面 */
        const val LOOK_BACK_MS = 60_000L
        const val LOOK_AHEAD_MS = 15_000L
        const val MAX_AGE_MS = 24 * 60 * 60_000L
        const val PREFS_NAME = "telemetry_forensics"
        const val KEY_PENDING = "pending_service_death"
    }
}
