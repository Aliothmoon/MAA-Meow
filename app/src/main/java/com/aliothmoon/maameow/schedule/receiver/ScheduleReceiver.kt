package com.aliothmoon.maameow.schedule.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.service.ScheduleAlarmManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext
import timber.log.Timber

/** 接收定时触发并启动 ScheduleExecutionService。 */
class ScheduleReceiver : BroadcastReceiver() {

    companion object {
        private const val EXECUTION_SERVICE_CLASS =
            "com.aliothmoon.maameow.schedule.service.ScheduleExecutionService"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val strategyId = intent.getStringExtra(ScheduleAlarmManager.EXTRA_STRATEGY_ID) ?: return
        val scheduledTime = intent.getLongExtra(ScheduleAlarmManager.EXTRA_SCHEDULED_TIME, 0L)

        if (intent.action != ScheduleAlarmManager.ACTION_SCHEDULE_TRIGGER) return

        Timber.i("Schedule alarm triggered for strategy: %s", strategyId)
        val serviceIntent = Intent().apply {
            setClassName(context, EXECUTION_SERVICE_CLASS)
            action = ScheduleAlarmManager.ACTION_SCHEDULE_TRIGGER
            putExtra(ScheduleAlarmManager.EXTRA_STRATEGY_ID, strategyId)
            putExtra(ScheduleAlarmManager.EXTRA_SCHEDULED_TIME, scheduledTime)
        }

        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: IllegalStateException) {
            // 正常情况下闹钟由 setExactAndAllowWhileIdle / setAlarmClock 注册，二者均豁免前台服务后台启动
            // 限制，此处不应抛异常。保留防御：万一启动失败（如系统异常），ScheduleExecutionService 不会运行、
            // scheduleNext 也不会被调用，故在 goAsync 协程里补注册下次闹钟，维持调度链不断。
            // 补注册会重新走 ScheduleAlarmManager 的选择逻辑（无精确权限时用 setAlarmClock），下次能真正恢复。
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
                    val loaded = withTimeoutOrNull(5_000L) {
                        repository.isLoaded.filter { it }.first()
                    }
                    if (loaded != null) {
                        val strategy = repository.getById(strategyId)
                        if (strategy != null) {
                            repository.recordExecutionResult(
                                strategyId = strategyId,
                                result = ExecutionResult.FAILED_UI_LAUNCH,
                                message = "前台服务启动失败：${e.message}",
                            )
                            alarmManager.scheduleNext(strategy, scheduledTime)
                            Timber.i(
                                "Rescheduled next alarm for strategy %s after service start failure",
                                strategyId
                            )
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
