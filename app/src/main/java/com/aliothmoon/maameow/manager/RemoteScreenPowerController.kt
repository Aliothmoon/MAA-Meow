package com.aliothmoon.maameow.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.domain.service.ScreenPowerController
import timber.log.Timber

class RemoteScreenPowerController(context: Context) : ScreenPowerController {

    private class Mark(val origin: ScreenPowerController.Origin, val service: RemoteService)

    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(PowerManager::class.java)

    @Volatile
    private var mark: Mark? = null
    private var listening = false

    // 系统休眠或被唤醒，面板都已归系统管
    private val systemTookOver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = clear()
    }

    // 提权进程退出时 PowerController 兜底亮屏，换了实例即作废
    override val poweredOff: ScreenPowerController.Origin?
        get() = mark
            ?.takeIf { it.service === RemoteServiceManager.getInstanceOrNull() && powerManager.isInteractive }
            ?.origin

    override fun powerOff(origin: ScreenPowerController.Origin): Boolean {
        val service = RemoteServiceManager.getInstanceOrNull()
        if (service == null) {
            Timber.w("powerOff skipped: remote service unavailable")
            return false
        }
        val ok = runCatching { service.setDisplayPower(false) }
            .onFailure { Timber.e(it, "powerOff failed") }
            .getOrDefault(false)
        // 远端没关成就不记，否则屏幕亮着也被当成已熄屏
        if (ok) mark(Mark(origin, service)) else Timber.w("powerOff rejected by remote")
        return ok
    }

    @Synchronized
    private fun mark(value: Mark) {
        mark = value
        if (listening) return
        ContextCompat.registerReceiver(
            appContext,
            systemTookOver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        listening = true
    }

    @Synchronized
    private fun clear() {
        mark = null
        if (!listening) return
        appContext.unregisterReceiver(systemTookOver)
        listening = false
    }
}
