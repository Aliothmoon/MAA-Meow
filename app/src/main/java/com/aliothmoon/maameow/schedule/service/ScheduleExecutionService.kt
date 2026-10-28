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
import com.aliothmoon.maameow.domain.launch.LaunchPipeline
import com.aliothmoon.maameow.domain.launch.LaunchPresentation
import com.aliothmoon.maameow.domain.launch.LaunchRequest
import com.aliothmoon.maameow.domain.launch.LaunchSession
import com.aliothmoon.maameow.domain.service.SpecialUseFgsGate
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.receiver.ScheduleCountdownActionReceiver
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
    private val launchPipeline: LaunchPipeline by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Service 生命周期跟在途触发数绑定，不跟最后一个 startId */
    private var inFlight = 0
    private var latestStartId = 0
    private val wakeLocks = mutableSetOf<PowerManager.WakeLock>()

    private var observingSession = false

    /** 静默启动的倒计时；并发触发重发前台通知时沿用，免得把按钮盖掉 */
    private var silentCountdown: SilentCountdown? = null

    private data class SilentCountdown(val request: LaunchRequest, val deadlineMs: Long)

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
        startAsForeground(currentNotification())

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
                observeSilentStart()
                withContext(Dispatchers.IO) {
                    triggerHandler.handle(strategyId, scheduledTime, retryCount)
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                if (!startupReady) {
                    val retrying = scheduleAlarmManager.scheduleRetry(strategyId, scheduledTime, retryCount)
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
                            notify = !retrying,
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

    private fun observeSilentStart() {
        if (observingSession) return
        observingSession = true
        serviceScope.launch { launchPipeline.session.collect(::renderSilentStart) }
    }

    private fun renderSilentStart(session: LaunchSession) {
        val current = session as? LaunchSession.InFlight
        val silent = current?.presentation == LaunchPresentation.NOTIFICATION
        val phase = current?.phase
        if (silent && phase is LaunchSession.Phase.Counting) {
            if (silentCountdown?.request?.requestId == current.request.requestId) return
            silentCountdown = SilentCountdown(
                request = current.request,
                deadlineMs = System.currentTimeMillis() + phase.remainingSeconds * 1000L,
            )
            post(currentNotification())
            return
        }
        if (silentCountdown == null) return
        silentCountdown = null
        val starting = silent && phase != LaunchSession.Phase.DevicePrep
        post(
            if (starting) {
                buildNotification(getString(R.string.notification_task_starting)).build()
            } else {
                buildPreparingNotification()
            }
        )
    }

    private fun post(notification: Notification) {
        // 收尾后再发就成了摘不掉的常驻通知
        if (inFlight == 0) return
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
    }

    private fun currentNotification(): Notification =
        silentCountdown?.let(::buildCountdownNotification) ?: buildPreparingNotification()

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

    private fun buildNotification(contentText: String): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_maa_logo)
            .setContentTitle(getString(R.string.notification_schedule_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(mainActivityPendingIntent(this))
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setSilent(true)

    private fun buildPreparingNotification(): Notification =
        buildNotification(getString(R.string.notification_schedule_preparing)).build()

    private fun buildCountdownNotification(countdown: SilentCountdown): Notification {
        val request = countdown.request
        return buildNotification(
            getString(R.string.notification_schedule_silent_countdown, request.displayName),
        )
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .setWhen(countdown.deadlineMs)
            .addAction(
                0,
                getString(R.string.common_cancel),
                ScheduleCountdownActionReceiver.pendingIntent(
                    this, ScheduleCountdownActionReceiver.ACTION_CANCEL, request.requestId,
                ),
            )
            .addAction(
                0,
                getString(R.string.schedule_countdown_start_now),
                ScheduleCountdownActionReceiver.pendingIntent(
                    this, ScheduleCountdownActionReceiver.ACTION_START_NOW, request.requestId,
                ),
            )
            .build()
    }

    override fun onDestroy() {
        wakeLocks.forEach(ScheduleWakeLock::release)
        wakeLocks.clear()
        serviceScope.cancel()
        // FGS 被拒时这条是普通常驻通知，stopForeground 摘不掉
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }
}
