package com.aliothmoon.maameow.data.notification.live

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.aliothmoon.maameow.manager.RemoteServiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 通过已连接的提权进程断/复 com.xiaomi.xmsf；失败仍发岛
 *
 * 规则落在系统侧 netd，进程死亡不会自动失效，所以断网即落持久化标记、恢复成功才清，
 * 并在提权服务每次重连时补一次——否则提权进程先死就会永久残留
 */
class XmsfNetworkGate(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val worker = HandlerThread("xmsf-gate").apply { start() }
    private val workerHandler = Handler(worker.looper)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var holds = 0

    init {
        scope.launch {
            RemoteServiceManager.state
                .filterIsInstance<RemoteServiceManager.ServiceState.Connected>()
                .collect { repairIfPending() }
        }
    }

    /** 含跨进程 shell 往返，调用方须在非主线程 */
    fun acquire() {
        synchronized(lock) {
            if (holds++ == 0) apply(enabled = false)
        }
    }

    fun release() {
        synchronized(lock) {
            if (holds <= 0) return
            if (--holds == 0) workerHandler.post { restoreIfIdle() }
        }
    }

    /** 断网窗口需盖住云端鉴权失败路径，恢复延迟起步 1.5s */
    fun pulse(block: () -> Unit) {
        acquire()
        try {
            block()
        } finally {
            workerHandler.postDelayed({ release() }, RESTORE_DELAY_MS)
        }
    }

    /** 上一次断网没能恢复（提权进程先死）时，重连后补一刀 */
    private fun repairIfPending() {
        if (synchronized(lock) { holds != 0 }) return
        if (!prefs.getBoolean(KEY_CUT, false)) return
        Timber.w("xmsf cut outstanding from a previous session, repairing")
        workerHandler.post { restoreIfIdle() }
    }

    private fun restoreIfIdle() {
        if (synchronized(lock) { holds != 0 }) return
        apply(enabled = true)
    }

    private fun apply(enabled: Boolean) {
        val service = RemoteServiceManager.getInstanceOrNull()
        if (service == null) {
            if (enabled) {
                // 标记留着，等提权服务重连时 repairIfPending 补
                Timber.w("xmsf restore deferred: remote service not connected")
            } else {
                Timber.d("xmsf cut skipped: remote service not connected")
            }
            return
        }
        // 先落标记再下发：调用途中进程被杀也不会丢失「断过网」这个事实
        if (!enabled) markCut(true)
        val result = runCatching { service.setPackageNetworkingEnabled(XMSF_PACKAGE, enabled) }
            .onFailure { Timber.w(it, "xmsf networking toggle failed enabled=%s", enabled) }
        val ok = result.getOrDefault(false)
        Timber.i("xmsf networking enabled=%s result=%s", enabled, ok)
        when {
            enabled && ok -> markCut(false)
            enabled -> Timber.w("xmsf restore failed, will retry on next remote connect")
            // 断网明确失败时提权侧已自清；抛异常则可能已生效，标记必须留着
            result.isSuccess && !ok -> markCut(false)
        }
    }

    private fun markCut(cut: Boolean) {
        prefs.edit().putBoolean(KEY_CUT, cut).commit()
    }

    private companion object {
        const val XMSF_PACKAGE = "com.xiaomi.xmsf"
        const val RESTORE_DELAY_MS = 1_500L
        const val PREFS_NAME = "live_xmsf_gate"
        const val KEY_CUT = "cut_outstanding"
    }
}
