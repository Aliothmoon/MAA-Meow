package com.aliothmoon.maameow.schedule.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aliothmoon.maameow.MainActivity
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.schedule.model.ScheduleType
import com.aliothmoon.maameow.schedule.model.ScheduledExecutionRequest
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import timber.log.Timber
import java.time.Instant
import java.time.ZonedDateTime
import java.time.ZoneId

class ScheduleAlarmManager(
    private val context: Context,
    private val appSettingsManager: AppSettingsManager,
) {

    companion object {
        const val ACTION_SCHEDULE_TRIGGER = "com.aliothmoon.maameow.SCHEDULE_TRIGGER"
        const val EXTRA_STRATEGY_ID = "strategy_id"
        const val EXTRA_SCHEDULED_TIME = "scheduled_time"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * 为策略注册下一个闹钟。
     * 后台：提前 [ScheduledExecutionRequest.COUNTDOWN_SECONDS] 触发，留给倒计时弹窗
     * 前台：准时触发（无倒计时）
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
        val leadSec = if (appSettingsManager.runMode.value == RunMode.FOREGROUND) {
            0
        } else {
            ScheduledExecutionRequest.COUNTDOWN_SECONDS
        }
        val triggerMs = scheduledTimeMs - leadSec * 1000L

        val pendingIntent = buildPendingIntent(strategy.id, scheduledTimeMs)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // 没有精确闹钟权限时不能退化成 setAndAllowWhileIdle：inexact 闹钟发出的广播在 12+
            // 不在前台服务后台启动的豁免清单里，服务起不来，定时会永久空转
            // setAlarmClock 不要 SCHEDULE_EXACT_ALARM、强制脱 Doze 投递，且同属 exact 而享有豁免
            // 代价只是状态栏多一个闹钟图标
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerMs, buildShowIntent()),
                pendingIntent,
            )
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
        }

        Timber.i(
            "已为策略 [%s] 注册闹钟，触发时间: %s（提前 %ds）",
            strategy.id,
            nextTrigger,
            leadSec,
        )
    }

    /** 取消策略的闹钟 */
    fun cancel(strategyId: String) {
        val pendingIntent = buildPendingIntent(strategyId, 0L)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Timber.i("已取消策略 [%s] 的闹钟", strategyId)
    }

    /** API 31 起用户可单独关掉精确闹钟；关了仍能定时（走 setAlarmClock），只是状态栏多个图标 */
    fun canScheduleExact(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        return ExactAlarmSettings.isAllowed(Build.VERSION.SDK_INT, granted)
    }

    /** 31 以下没有那个系统开关页，入口要藏掉，否则点了什么也不会发生 */
    fun hasExactAlarmToggle(): Boolean = ExactAlarmSettings.hasToggle(Build.VERSION.SDK_INT)

    /** 先撤后立：禁用与删除都会留下孤儿闹钟，重排时必须把已启用的整批过一遍 */
    fun rescheduleAll(strategies: List<ScheduleStrategy>) {
        strategies.forEach { strategy ->
            cancel(strategy.id)
            if (strategy.enabled) scheduleNext(strategy)
        }
    }

    fun computeNextTrigger(strategy: ScheduleStrategy, afterEpochMs: Long = 0L): ZonedDateTime? {
        return when (strategy.scheduleType) {
            ScheduleType.FIXED_TIME -> computeNextFixedTime(strategy, afterEpochMs)
            ScheduleType.INTERVAL -> computeNextInterval(strategy, afterEpochMs)
        }
    }

    /**
     * [FIXED_TIME] 扫描未来 7 天，匹配 dayOfWeek + executionTimes。
     */
    private fun computeNextFixedTime(strategy: ScheduleStrategy, afterEpochMs: Long): ZonedDateTime? {
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

    /**
     * [INTERVAL] 从 startTimeMs 起，每隔 intervalMinutes 触发一次。
     * 计算公式: next = startTime + ceil((baseline - startTime) / interval) * interval
     */
    private fun computeNextInterval(strategy: ScheduleStrategy, afterEpochMs: Long): ZonedDateTime? {
        val startMs = strategy.startTimeMs ?: return null
        val intervalMs = (strategy.intervalMinutes ?: return null) * 60_000L
        if (intervalMs <= 0) return null

        val now = System.currentTimeMillis()
        val baseline = maxOf(now, afterEpochMs)

        val nextMs = if (startMs > baseline) {
            startMs
        } else {
            val elapsed = baseline - startMs
            val n = elapsed / intervalMs + 1
            startMs + n * intervalMs
        }

        return Instant.ofEpochMilli(nextMs).atZone(ZoneId.systemDefault())
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

    /** setAlarmClock 要求的展示 Intent：用户点击状态栏闹钟图标时打开主界面。所有策略共用一个即可。 */
    private fun buildShowIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCode(strategyId: String): Int = strategyId.hashCode() and 0x7FFFFFFF
}
