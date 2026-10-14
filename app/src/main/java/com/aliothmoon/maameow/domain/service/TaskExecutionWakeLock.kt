package com.aliothmoon.maameow.domain.service

import android.content.Context
import android.os.PowerManager
import com.aliothmoon.maameow.domain.models.RunDurationLimit

/** 任务运行期间阻止设备进入 suspend；服务终止时显式释放，超时仅作异常兜底。 */
internal class TaskExecutionWakeLock(private val context: Context) {
    companion object {
        private const val SAFETY_MARGIN_MINUTES = 60L
        internal const val MAX_HOLD_MS =
            (RunDurationLimit.MAX_MINUTES + SAFETY_MARGIN_MINUTES) * 60_000L
        private const val TAG = "MaaMeow:task-execution"
    }

    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquire() {
        if (wakeLock?.isHeld == true) return
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG).apply {
            setReferenceCounted(false)
            acquire(MAX_HOLD_MS)
        }
    }

    @Synchronized
    fun release() {
        val lock = wakeLock ?: return
        wakeLock = null
        if (lock.isHeld) lock.release()
    }
}
