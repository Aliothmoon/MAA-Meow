package com.aliothmoon.maameow.schedule.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.service.ScheduleAlarmManager
import com.aliothmoon.maameow.schedule.service.ScheduleFailureReporter
import com.aliothmoon.maameow.schedule.service.ScheduleWakeLock
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.core.context.GlobalContext
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

/** 接收定时触发并启动 ScheduleExecutionService。 */
class ScheduleReceiver : BroadcastReceiver() {

    companion object {
        private const val EXECUTION_SERVICE_CLASS =
            "com.aliothmoon.maameow.schedule.service.ScheduleExecutionService"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScheduleAlarmManager.ACTION_SCHEDULE_TRIGGER) return

        val strategyId = intent.getStringExtra(ScheduleAlarmManager.EXTRA_STRATEGY_ID) ?: return
        val scheduledTime = intent.getLongExtra(ScheduleAlarmManager.EXTRA_SCHEDULED_TIME, 0L)
        val retryCount = intent.getIntExtra(ScheduleAlarmManager.EXTRA_RETRY_COUNT, 0)

        val delayMs = if (scheduledTime > 0L) System.currentTimeMillis() - scheduledTime else 0L
        Timber.i(
            "Schedule alarm triggered: %s, delay=+%dms, retry=%d",
            strategyId,
            delayMs,
            retryCount,
        )
        val serviceIntent = Intent().apply {
            setClassName(context, EXECUTION_SERVICE_CLASS)
            action = ScheduleAlarmManager.ACTION_SCHEDULE_TRIGGER
            putExtra(ScheduleAlarmManager.EXTRA_STRATEGY_ID, strategyId)
            putExtra(ScheduleAlarmManager.EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(ScheduleAlarmManager.EXTRA_RETRY_COUNT, retryCount)
        }

        // onStartCommand 与 onReceive 同在主线程，返回前 Service 拿不到自己的锁
        // 故接力锁不在成功路径释放，只靠 10 秒超时兜底
        val handoffWakeLock = ScheduleWakeLock.acquire(context, 10_000L)
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            // 服务未启动也要续排，包含系统拒绝启动与权限异常
            Timber.e(
                e,
                "startForegroundService failed for strategy %s, rescheduling next alarm",
                strategyId
            )
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val koin = GlobalContext.get()
                    val repository: ScheduleStrategyRepository = koin.get()
                    val alarmManager: ScheduleAlarmManager = koin.get()
                    val failureReporter: ScheduleFailureReporter = koin.get()
                    val strategy = try {
                        withTimeout(5_000L.milliseconds) { repository.getById(strategyId) }
                    } catch (restoreError: Exception) {
                        Timber.w(restoreError, "Schedule fallback failed: %s", strategyId)
                        alarmManager.scheduleRetry(strategyId, scheduledTime, retryCount)
                        return@launch
                    }
                    if (strategy != null && strategy.enabled) {
                        // 先续排，结果写盘失败也不丢闹钟
                        alarmManager.scheduleNext(strategy, scheduledTime)
                        failureReporter.report(
                            strategyId = strategyId,
                            strategyName = strategy.name.ifEmpty { strategyId },
                            scheduledTimeMs = scheduledTime,
                            result = ExecutionResult.FAILED_UI_LAUNCH,
                            message = uiTextOf(
                                R.string.schedule_log_service_start_failed,
                                e.message ?: e.javaClass.simpleName,
                            ),
                        )
                    }
                } catch (restoreError: Exception) {
                    Timber.e(restoreError, "Cannot restore schedule: %s", strategyId)
                } finally {
                    ScheduleWakeLock.release(handoffWakeLock)
                    pendingResult.finish()
                }
            }
        }
    }
}
