package com.aliothmoon.maameow.domain.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aliothmoon.maameow.MainActivity
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber

class TaskExecutionService : Service() {

    companion object {
        private const val CHANNEL_ID = "task_execution"
        private const val NOTIFICATION_ID = 9003
        private const val RESULT_NOTIFICATION_ID = 9004
        private const val MIN_UPDATE_INTERVAL_MS = 1000L

        fun start(context: Context) {
            val intent = Intent(context, TaskExecutionService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TaskExecutionService::class.java))
        }
    }

    private val compositionService: MaaCompositionService by inject()
    private val sessionLogger: MaaSessionLogger by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startAsForeground(buildNotification(getString(R.string.notification_task_starting)))
        observeProgress()
    }

    private fun observeProgress() {
        observeJob = serviceScope.launch {
            var lastUpdateTime = 0L
            combine(
                compositionService.state,
                sessionLogger.logs
            ) { state, logs ->
                Pair(state, logs.lastOrNull()?.content)
            }.collectLatest { (state, latestLog) ->
                when (state) {
                    MaaExecutionState.IDLE, MaaExecutionState.ERROR -> {
                        Timber.i("TaskExecutionService: state=$state, stopping")
                        val title = getString(
                            if (state == MaaExecutionState.IDLE) R.string.notification_task_completed
                            else R.string.notification_task_error
                        )
                        showResultNotification(title, latestLog ?: "")
                        stopSelf()
                    }

                    MaaExecutionState.STARTING -> {
                        updateNotification(getString(R.string.notification_task_starting))
                    }

                    MaaExecutionState.STOPPING -> {
                        updateNotification(getString(R.string.notification_task_stopping))
                    }

                    MaaExecutionState.RUNNING -> {
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime >= MIN_UPDATE_INTERVAL_MS) {
                            lastUpdateTime = now
                            updateNotification(latestLog ?: getString(R.string.notification_task_running))
                        } else {
                            delay(MIN_UPDATE_INTERVAL_MS - (now - lastUpdateTime))
                            lastUpdateTime = System.currentTimeMillis()
                            updateNotification(latestLog ?: getString(R.string.notification_task_running))
                        }
                    }
                }
            }
        }
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_task_execution),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_task_execution_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(contentText)
            .setContentText(getString(R.string.notification_task_running))
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(buildContentIntent())
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun buildContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun showResultNotification(title: String, text: String) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(buildContentIntent())
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(RESULT_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        observeJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
