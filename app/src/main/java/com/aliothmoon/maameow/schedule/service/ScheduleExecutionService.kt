package com.aliothmoon.maameow.schedule.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aliothmoon.maameow.MainActivity
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.launch.LaunchPipeline
import com.aliothmoon.maameow.schedule.LaunchIntentMapper
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

/** 定时触发 FGS：构造 [LaunchRequest] → [LaunchPipeline.execute] join → scheduleNext */
class ScheduleExecutionService : Service() {

    companion object {
        private const val TAG = "ScheduleExec"
        private const val NOTIFICATION_ID = 9001
        private const val CHANNEL_ID = "schedule_execution"
        private const val DATA_READY_TIMEOUT_MS = 5_000L
    }

    private val repository: ScheduleStrategyRepository by inject()
    private val alarmManager: ScheduleAlarmManager by inject()
    private val triggerLogger: ScheduleTriggerLogger by inject()
    private val launchPipeline: LaunchPipeline by inject()
    private val appSettings: AppSettingsManager by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Service 生命周期跟在途触发数绑定，不跟最后一个 startId */
    private val inFlight = AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val strategyId = intent?.getStringExtra(ScheduleAlarmManager.EXTRA_STRATEGY_ID)
        if (intent?.action != ScheduleAlarmManager.ACTION_SCHEDULE_TRIGGER
            || strategyId.isNullOrEmpty()
        ) {
            Timber.w("$TAG: bad start intent: action=%s", intent?.action)
            stopIfIdle()
            return START_NOT_STICKY
        }

        // 5 秒内必须 startForeground，不能等协程调度
        ensureNotificationChannel()
        startAsForeground(buildPreparingNotification())

        val scheduledTime = intent.getLongExtra(ScheduleAlarmManager.EXTRA_SCHEDULED_TIME, 0L)
        // 须先于 launch：协程调度前计数仍是 0，会被并发触发的收尾停掉
        inFlight.incrementAndGet()
        serviceScope.launch {
            try {
                handleTrigger(strategyId, scheduledTime)
            } finally {
                inFlight.decrementAndGet()
                stopIfIdle()
            }
        }
        return START_NOT_STICKY
    }

    /** 有在途触发就不摘 FGS、不停服务，否则会连带取消其他触发 */
    private fun stopIfIdle() {
        val remaining = inFlight.get()
        if (remaining > 0) {
            Timber.i("$TAG: keep alive, %d trigger(s) in flight", remaining)
            return
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun handleTrigger(strategyId: String, scheduledTimeMs: Long) {
        val strategy = awaitStrategy(strategyId)
        if (strategy == null) {
            Timber.w("$TAG: strategy missing: %s", strategyId)
            val msg = uiTextOf(R.string.schedule_log_strategy_missing)
            // 独立文件，不碰其他触发的 Session
            triggerLogger.writeClosed(
                strategyId = strategyId,
                strategyName = strategyId,
                scheduledTimeMs = scheduledTimeMs,
                result = ExecutionResult.FAILED_VALIDATION,
                message = msg,
                runMode = appSettings.runMode.value.name,
            )
            repository.recordExecutionResult(
                strategyId = strategyId,
                result = ExecutionResult.FAILED_VALIDATION,
                message = triggerLogger.resolveMessage(msg),
            )
            return
        }

        val request = LaunchIntentMapper.fromStrategy(strategy, scheduledTimeMs)
        try {
            launchPipeline.execute(request).join()
        } finally {
            alarmManager.scheduleNext(strategy, scheduledTimeMs)
            Timber.i("$TAG: pipeline finished for %s", request.requestId)
        }
    }

    private suspend fun awaitStrategy(strategyId: String): ScheduleStrategy? {
        return withTimeoutOrNull(DATA_READY_TIMEOUT_MS) {
            repository.isLoaded.first { it }
            repository.getById(strategyId)
        }
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_schedule),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.notification_channel_schedule_desc)
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

    private fun buildContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun buildPreparingNotification(): Notification {
        val contentText = getString(R.string.notification_schedule_preparing)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_maa_logo)
            .setContentTitle(getString(R.string.notification_schedule_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(buildContentIntent())
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
