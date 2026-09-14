package com.aliothmoon.maameow.schedule.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.aliothmoon.maameow.MaaApplication
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.service.SpecialUseFgsGate
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.android.ext.android.inject
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

/** 定时触发 FGS，持锁等待启动流程完成 */
class ScheduleExecutionService : Service() {

    companion object {
        private const val TAG = "ScheduleExec"
        private const val NOTIFICATION_ID = 9001
        private const val CHANNEL_ID = "schedule_execution"
        private const val STARTUP_WAKE_TIMEOUT_MS = 5 * 60_000L
    }

    private val triggerHandler: ScheduleTriggerHandler by inject()
    private val scheduleAlarmManager: ScheduleAlarmManager by inject()
    private val failureReporter: ScheduleFailureReporter by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Service 生命周期跟在途触发数绑定，不跟最后一个 startId */
    private var inFlight = 0
    private var latestStartId = 0
    private val wakeLocks = mutableSetOf<PowerManager.WakeLock>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
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
        val retryCount = intent.getIntExtra(ScheduleAlarmManager.EXTRA_RETRY_COUNT, 0)
        Timber.i("$TAG: started: %s, scheduled=%d, retry=%d", strategyId, scheduledTime, retryCount)
        // 须先于 launch：协程调度前计数仍是 0，会被并发触发的收尾停掉
        val wakeLock = ScheduleWakeLock.acquire(this, STARTUP_WAKE_TIMEOUT_MS)
        wakeLocks.add(wakeLock)
        inFlight++
        serviceScope.launch {
            var startupReady = false
            try {
                // 通知和唤醒锁已就位，挂起等待不会阻塞主线程
                withTimeout(STARTUP_WAKE_TIMEOUT_MS.milliseconds) {
                    (application as MaaApplication).awaitReady()
                }
                startupReady = true
                Timber.i("$TAG: app ready: %s", strategyId)
                withContext(Dispatchers.IO) {
                    triggerHandler.handle(strategyId, scheduledTime, retryCount)
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                if (!startupReady) {
                    scheduleAlarmManager.scheduleRetry(strategyId, scheduledTime, retryCount)
                    // 应用未就绪，拿不到策略名
                    withContext(Dispatchers.IO) {
                        failureReporter.report(
                            strategyId = strategyId,
                            strategyName = strategyId,
                            scheduledTimeMs = scheduledTime,
                            result = ExecutionResult.FAILED_START,
                            message = uiTextOf(
                                R.string.schedule_log_app_init_failed,
                                e.message ?: e.javaClass.simpleName,
                            ),
                        )
                    }
                }
                Timber.e(e, "$TAG: trigger failed: %s", strategyId)
            } finally {
                wakeLocks.remove(wakeLock)
                ScheduleWakeLock.release(wakeLock)
                inFlight--
                stopIfIdle()
            }
        }
        return START_NOT_STICKY
    }

    /** 有在途触发就不摘 FGS、不停服务，否则会连带取消其他触发 */
    private fun stopIfIdle() {
        val remaining = inFlight
        if (remaining > 0) {
            Timber.i("$TAG: keep alive, %d trigger(s) in flight", remaining)
            return
        }
        if (stopSelfResult(latestStartId)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
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
        try {
            SpecialUseFgsGate.startForeground(this, NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // specialUse 被系统拒绝：尽力继续触发，uid 转空闲后服务可能被系统停掉
            Timber.w(e, "$TAG: startForeground denied, continue without FGS")
        }
    }

    private fun buildPreparingNotification(): Notification {
        val contentText = getString(R.string.notification_schedule_preparing)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_maa_logo)
            .setContentTitle(getString(R.string.notification_schedule_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(mainActivityPendingIntent(this))
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        wakeLocks.forEach(ScheduleWakeLock::release)
        wakeLocks.clear()
        serviceScope.cancel()
        super.onDestroy()
    }
}
