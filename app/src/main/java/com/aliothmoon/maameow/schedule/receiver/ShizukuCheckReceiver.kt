package com.aliothmoon.maameow.schedule.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aliothmoon.maameow.MaaApplication
import com.aliothmoon.maameow.schedule.service.ShizukuDownMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.core.context.GlobalContext
import timber.log.Timber

/** [ShizukuDownMonitor] 排的检查闹钟 */
class ShizukuCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val afterBoot = intent.getBooleanExtra(EXTRA_AFTER_BOOT, false)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // goAsync 只给 10 秒
                withTimeout(CHECK_TIMEOUT_MS) {
                    (context.applicationContext as MaaApplication).awaitReady()
                    GlobalContext.get().get<ShizukuDownMonitor>().check(afterBoot)
                }
            } catch (e: Exception) {
                Timber.w(e, "Shizuku check failed")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_AFTER_BOOT = "after_boot"
        private const val CHECK_TIMEOUT_MS = 9_000L
    }
}
