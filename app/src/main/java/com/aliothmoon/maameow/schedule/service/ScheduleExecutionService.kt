package com.aliothmoon.maameow.schedule.service

import android.app.KeyguardManager
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
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import com.aliothmoon.maameow.schedule.model.ScheduledExecutionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import timber.log.Timber
import com.aliothmoon.maameow.schedule.service.ScheduleTriggerLogger

class ScheduleExecutionService : Service() {

    companion object {
        private const val TAG = "ScheduleExec"
        private const val NOTIFICATION_ID = 9001
        private const val RESULT_NOTIFICATION_ID = 9002
        private const val CHANNEL_ID = "schedule_execution"
        private const val DATA_READY_TIMEOUT_MS = 5_000L
    }

    private val repository: ScheduleStrategyRepository by inject()
    private val alarmManager: ScheduleAlarmManager by inject()
    private val triggerLogger: ScheduleTriggerLogger by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ScheduleAlarmManager.ACTION_SCHEDULE_TRIGGER -> {
                val strategyId = intent.getStringExtra(ScheduleAlarmManager.EXTRA_STRATEGY_ID)
                val scheduledTime =
                    intent.getLongExtra(ScheduleAlarmManager.EXTRA_SCHEDULED_TIME, 0L)
                if (strategyId.isNullOrEmpty()) {
                    Timber.w("$TAG: 收到触发指令但缺少 strategyId")
                    shutdownService()
                } else {
                    serviceScope.launch {
                        handleTrigger(strategyId, scheduledTime)
                    }
                }
            }

            else -> {
                Timber.w("$TAG: 未知 action: %s", intent?.action)
                shutdownService()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun handleTrigger(strategyId: String, scheduledTimeMs: Long) {
        ensureNotificationChannel()
        startAsForeground(buildPreparingNotification())

        triggerLogger.append("调度触发，等待策略数据加载...")
        val strategy = awaitStrategy(strategyId)
        if (strategy == null) {
            Timber.w("$TAG: 策略不存在或配置未加载完成: %s", strategyId)
            triggerLogger.append("策略不存在或数据未加载完成，放弃执行")
            triggerLogger.end(ExecutionResult.FAILED_VALIDATION, "策略不存在")
            shutdownService()
            return
        }

        // 策略加载成功，开启正式的触发日志
        triggerLogger.begin(strategy.id, strategy.name, scheduledTimeMs)
        triggerLogger.append("策略加载完成: ${strategy.name}")

        val request = ScheduledExecutionRequest(
            strategyId = strategy.id,
            strategyName = strategy.name,
            profileId = strategy.profileId,
            scheduledTimeMs = scheduledTimeMs,
            forceStart = strategy.forceStart,
        )

        triggerLogger.append("检查锁屏状态...")
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isKeyguardLocked) {
            Timber.i("$TAG: 设备锁屏中，跳过本次定时执行: %s", strategy.name)
            triggerLogger.append("设备锁屏，跳过本次执行")
            triggerLogger.end(ExecutionResult.SKIPPED_LOCKED, "设备处于锁屏状态")
            val lockedMsg = getString(R.string.notification_schedule_device_locked)
            repository.recordExecutionResult(
                strategyId = strategy.id,
                result = ExecutionResult.SKIPPED_LOCKED,
                message = lockedMsg,
            )
            showResultNotification(
                getString(R.string.notification_schedule_skipped),
                getString(R.string.notification_schedule_detail, strategy.name, lockedMsg)
            )
            alarmManager.scheduleNext(strategy, scheduledTimeMs)
            shutdownService()
            return
        }
        triggerLogger.append("设备未锁屏，连接服务...")

        val ctx = this
        val launched = withTimeoutOrNull(10_000L) {
            runCatching {
                RemoteServiceManager.useRemoteService(timeoutMs = 8_000L) {
                    it.startActivity(request.toLaunchIntent(ctx))
                }
            }.getOrElse { error ->
                Timber.w(error, "$TAG: 拉起界面前连接服务失败")
                triggerLogger.append("连接服务失败: ${error.message}")
                false
            }
        } ?: false

        if (!launched) {
            triggerLogger.append("未能拉起界面")
            triggerLogger.end(ExecutionResult.FAILED_UI_LAUNCH, "未能拉起界面")
            recordUiLaunchFailure(strategy, "未能拉起界面", scheduledTimeMs)
            return
        }

        triggerLogger.append("服务连接成功，App待启动")
        alarmManager.scheduleNext(strategy, scheduledTimeMs)
        Timber.i("$TAG: 已将定时请求交给 UI: %s", request.requestId)
        shutdownService()
    }

    private suspend fun awaitStrategy(strategyId: String): ScheduleStrategy? {
        return withTimeoutOrNull(DATA_READY_TIMEOUT_MS) {
            repository.isLoaded.first { it }
            repository.getById(strategyId)
        }
    }

    private suspend fun recordUiLaunchFailure(
        strategy: ScheduleStrategy,
        message: String,
        scheduledTimeMs: Long
    ) {
        repository.recordExecutionResult(
            strategyId = strategy.id,
            result = ExecutionResult.FAILED_UI_LAUNCH,
            message = message,
        )
        showResultNotification(
            getString(R.string.notification_schedule_failed),
            getString(R.string.notification_schedule_detail, strategy.name, message)
        )
        alarmManager.scheduleNext(strategy, scheduledTimeMs)
        shutdownService()
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun buildPreparingNotification(): Notification {
        val contentText = getString(R.string.notification_schedule_preparing)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_schedule_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(buildContentIntent())
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun showResultNotification(title: String, text: String) {
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

    private fun shutdownService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
