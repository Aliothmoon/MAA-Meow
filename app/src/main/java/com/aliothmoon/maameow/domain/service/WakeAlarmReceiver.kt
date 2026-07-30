package com.aliothmoon.maameow.domain.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * 接收 [WakeAlarmScheduler] 设置的精确唤醒闹钟。
 *
 * 流程：
 * 1. 拿 PARTIAL_WAKE_LOCK 防止进程在 [onReceive] 返回后被杀
 * 2. 从配置读解锁策略 / 密码，调 [WakeUnlockEngine.wakeAndUnlock]
 * 3. 完成后让 [WakeAlarmScheduler.reschedule] 把下一次闹钟排上
 *
 * 注意：[onReceive] 同步阻塞等待唤醒结果，再让 scheduler 重新排期。
 * 避免 AlarmManager 把两次闹钟挤掉。
 */
class WakeAlarmReceiver : BroadcastReceiver(), KoinComponent {

    companion object {
        private const val TAG = "WakeAlarmReceiver"
        private const val WAKELOCK_TAG = "maameow:wake-unlock"
        private const val WAKELOCK_TIMEOUT_MS = 30_000L
    }

    private val engine: WakeUnlockEngine by inject()
    private val scheduler: WakeAlarmScheduler by inject()
    private val settings: AppSettingsManager by inject()

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != WakeAlarmScheduler.ACTION_WAKE) return

        Timber.i("$TAG: wake alarm fired")
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(WAKELOCK_TIMEOUT_MS)
        }

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val enabled = settings.wakeScheduleEnabled.first()
                if (!enabled) {
                    Timber.i("$TAG: feature disabled, skip unlock")
                    return@launch
                }

                val typeKey = settings.wakeUnlockType.first()
                val credential = settings.wakeCredential.first()
                val type = WakeUnlockEngine.UnlockType.fromKey(typeKey)
                val cfg = WakeUnlockEngine.WakeConfig(
                    unlockType = type,
                    credential = credential,
                )
                val ok = engine.wakeAndUnlock(cfg)
                Timber.i("$TAG: wake+unlock result=%b", ok)
            } catch (t: Throwable) {
                Timber.w(t, "$TAG: unlock sequence failed")
            } finally {
                // 无论本次成功失败，都重新排期下一个闹钟
                scheduler.reschedule()
                wl.release()
                pending.finish()
            }
        }
    }
}
