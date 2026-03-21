package com.aliothmoon.maameow.schedule.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aliothmoon.maameow.schedule.model.ScheduledExecutionRequest
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import timber.log.Timber
import java.time.Instant
import java.time.ZonedDateTime
import java.time.ZoneId

class ScheduleAlarmManager(private val context: Context) {

    companion object {
        const val ACTION_SCHEDULE_TRIGGER = "com.aliothmoon.maameow.SCHEDULE_TRIGGER"
        const val EXTRA_STRATEGY_ID = "strategy_id"
        const val EXTRA_SCHEDULED_TIME = "scheduled_time"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * 为策略注册下一个闹钟。
     * 计算 computeNextTrigger，然后提前 COUNTDOWN_LEAD_SECONDS 触发（留给倒计时弹窗）。
     * 如果没有下一个触发时间（策略禁用或无匹配日期），则不注册。
     */
    fun scheduleNext(strategy: ScheduleStrategy, afterEpochMs: Long = 0L) {
        if (!strategy.enabled) {
            Timber.d("策略 [%s] 已禁用，跳过注册闹钟", strategy.id)
            return
        }

        val nextTrigger = computeNextTrigger(strategy, afterEpochMs)
        if (nextTrigger == null) {
            Timber.d("策略 [%s] 未找到下一个触发时间，跳过注册闹钟", strategy.id)
            return
        }

        val scheduledTimeMs = nextTrigger.toInstant().toEpochMilli()
        val triggerMs = scheduledTimeMs - ScheduledExecutionRequest.COUNTDOWN_SECONDS * 1000L

        val pendingIntent = buildPendingIntent(strategy.id, scheduledTimeMs)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
        }

        Timber.i(
            "已为策略 [%s] 注册闹钟，触发时间: %s（提前 %ds）",
            strategy.id,
            nextTrigger,
            ScheduledExecutionRequest.COUNTDOWN_SECONDS
        )
    }

    /** 取消策略的闹钟 */
    fun cancel(strategyId: String) {
        val pendingIntent = buildPendingIntent(strategyId, 0L)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Timber.i("已取消策略 [%s] 的闹钟", strategyId)
    }

    /** 重新调度所有启用的策略 */
    fun rescheduleAll(strategies: List<ScheduleStrategy>) {
        strategies.filter { it.enabled }.forEach { scheduleNext(it) }
    }

    /**
     * 计算策略的下一个触发时间。
     *
     * 逻辑:
     * 1. 获取当前时间 (ZonedDateTime.now())
     * 2. 从今天开始，检查未来7天
     * 3. 对于每天，如果 dayOfWeek in strategy.daysOfWeek:
     *    - 遍历 strategy.executionTimes（已排序）
     *    - 组合成 ZonedDateTime
     *    - 如果在当前时间之后，返回这个时间
     * 4. 如果7天内无匹配，返回 null
     */
    fun computeNextTrigger(strategy: ScheduleStrategy, afterEpochMs: Long = 0L): ZonedDateTime? {
        if (strategy.daysOfWeek.isEmpty() || strategy.executionTimes.isEmpty()) {
            return null
        }

        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val baseline = if (afterEpochMs > 0L) {
            val afterTime = Instant.ofEpochMilli(afterEpochMs).atZone(ZoneId.systemDefault())
            maxOf(now, afterTime)
        } else {
            now
        }

        for (dayOffset in 0..7) {
            val candidate = baseline.toLocalDate().plusDays(dayOffset.toLong())
            if (candidate.dayOfWeek !in strategy.daysOfWeek) continue

            for (time in strategy.executionTimes) {
                val trigger = ZonedDateTime.of(candidate, time, ZoneId.systemDefault())
                if (trigger.isAfter(baseline)) {
                    return trigger
                }
            }
        }

        return null
    }

    private fun buildPendingIntent(strategyId: String, scheduledTimeMs: Long): PendingIntent {
        val intent = Intent(ACTION_SCHEDULE_TRIGGER).apply {
            setClassName(context, "com.aliothmoon.maameow.schedule.receiver.ScheduleReceiver")
            putExtra(EXTRA_STRATEGY_ID, strategyId)
            putExtra(EXTRA_SCHEDULED_TIME, scheduledTimeMs)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(strategyId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(strategyId: String): Int = strategyId.hashCode() and 0x7FFFFFFF
}
