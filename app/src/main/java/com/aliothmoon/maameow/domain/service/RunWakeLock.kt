package com.aliothmoon.maameow.domain.service

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/** 任务运行期分段续租唤醒锁，不依赖 FGS，防息屏深睡拖死任务 */
class RunWakeLock internal constructor(
    private val ops: WakeLockOps,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val sliceMs: Long = DEFAULT_SLICE_MS,
    private val renewIntervalMs: Long = DEFAULT_RENEW_INTERVAL_MS,
    private val maxHoldMs: Long = DEFAULT_MAX_HOLD_MS,
) {
    constructor(context: Context) : this(AndroidWakeLockOps(context.applicationContext))

    companion object {
        private const val TAG = "MaaMeow:task_run"
        const val DEFAULT_SLICE_MS = 30 * 60_000L
        const val DEFAULT_RENEW_INTERVAL_MS = 20 * 60_000L
        const val DEFAULT_MAX_HOLD_MS = 12 * 60 * 60_000L
    }

    internal interface WakeLockOps {
        fun acquire(timeoutMs: Long)
        fun release()
    }

    private class AndroidWakeLockOps(private val appContext: Context) : WakeLockOps {
        private var wakeLock: PowerManager.WakeLock? = null

        private fun getOrCreate(): PowerManager.WakeLock? {
            wakeLock?.let { return it }
            val pm = appContext.getSystemService(PowerManager::class.java) ?: return null
            return pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG).apply {
                setReferenceCounted(false)
            }.also { wakeLock = it }
        }

        override fun acquire(timeoutMs: Long) {
            getOrCreate()?.acquire(timeoutMs)
        }

        override fun release() {
            val wl = wakeLock ?: return
            if (wl.isHeld) wl.release()
        }
    }

    private val lock = Any()
    private var held = false
    private var generation = 0L
    private var acquiredAt = 0L
    private var renewJob: Job? = null

    fun acquire() {
        synchronized(lock) {
            renewJob?.cancel()
            val gen = ++generation
            acquiredAt = clock()
            held = true
            acquireSliceSafe(sliceMs.coerceAtMost(maxHoldMs))
            renewJob = scope.launch { renewLoop(gen) }
        }
    }

    fun release() {
        synchronized(lock) {
            generation++
            renewJob?.cancel()
            renewJob = null
            if (!held) return
            held = false
            releaseSafe()
        }
    }

    private suspend fun renewLoop(gen: Long) {
        while (true) {
            delay(renewIntervalMs)
            val keepGoing = synchronized(lock) {
                if (!held || generation != gen) return@synchronized false
                val elapsed = clock() - acquiredAt
                if (elapsed >= maxHoldMs) {
                    Timber.w("RunWakeLock reached max hold ceiling (%d ms), releasing", maxHoldMs)
                    held = false
                    renewJob = null
                    releaseSafe()
                    return@synchronized false
                }
                val nextSlice = (maxHoldMs - elapsed).coerceAtMost(sliceMs)
                acquireSliceSafe(nextSlice)
                true
            }
            if (!keepGoing) break
        }
    }

    private fun acquireSliceSafe(timeoutMs: Long) {
        runCatching { ops.acquire(timeoutMs) }
            .onFailure { Timber.w(it, "RunWakeLock acquire failed") }
    }

    private fun releaseSafe() {
        runCatching { ops.release() }
            .onFailure { Timber.w(it, "RunWakeLock release failed") }
    }
}
