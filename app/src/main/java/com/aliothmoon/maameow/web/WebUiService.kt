package com.aliothmoon.maameow.web

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.service.SpecialUseFgsGate
import com.aliothmoon.maameow.schedule.service.mainActivityPendingIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber

/** 只做保活和常驻通知；服务器本身归 [WebUiController] 管，拿不到前台也照样跑 */
class WebUiService : Service() {

    companion object {
        private const val CHANNEL_ID = "webui"
        private const val NOTIFICATION_ID = 9010
        private const val ACTION_TURN_OFF = "com.aliothmoon.maameow.webui.TURN_OFF"

        // 只给 start：停由服务看 [WebUiController.state] 自行 stopSelf
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, WebUiService::class.java))
            } catch (e: IllegalStateException) {
                // 后台拉起前台服务被拒（12+），服务器照跑，只是没保活
                Timber.w(e, "WebUiService: start not allowed")
            }
        }
    }

    private val controller: WebUiController by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 先满足 startForeground 时限，再处理动作
        ensureChannel()
        try {
            SpecialUseFgsGate.startForeground(this, NOTIFICATION_ID, buildNotification())
        } catch (e: SecurityException) {
            Timber.w(e, "WebUiService: startForeground denied, serve without FGS")
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_TURN_OFF) {
            controller.setEnabled(false)
        }
        if (observeJob == null) {
            observeJob = scope.launch {
                controller.state.collect { state ->
                    if (state is WebUiController.ServerState.Running) {
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, buildNotification())
                    } else {
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.webui_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_maa_logo)
        .setContentTitle(getString(R.string.webui_notification_title))
        .setContentText(contentText())
        .setOngoing(true)
        .setSilent(true)
        .setContentIntent(mainActivityPendingIntent(this))
        .addAction(
            0,
            getString(R.string.webui_notification_turn_off),
            PendingIntent.getService(
                this,
                0,
                Intent(this, WebUiService::class.java).setAction(ACTION_TURN_OFF),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    private fun contentText(): String {
        val port = (controller.state.value as? WebUiController.ServerState.Running)?.port
            ?: return getString(R.string.webui_notification_starting)
        val address = LanAddresses.ipv4().firstOrNull()
            ?: return getString(R.string.webui_notification_no_lan)
        return getString(R.string.webui_notification_text, LanAddresses.url(address.host, port))
    }
}
