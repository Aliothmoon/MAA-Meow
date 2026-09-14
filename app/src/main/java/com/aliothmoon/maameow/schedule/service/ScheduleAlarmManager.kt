package com.aliothmoon.maameow.schedule.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import com.aliothmoon.maameow.schedule.model.ScheduleType
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleAlarmManager(
    private val context: Context,
) {

    companion object {
        const val ACTION_SCHEDULE_TRIGGER = "com.aliothmoon.maameow.SCHEDULE_TRIGGER"
        const val EXTRA_STRATEGY_ID = "strategy_id"
        const val EXTRA_SCHEDULED_TIME = "scheduled_time"
        const val EXTRA_RETRY_COUNT = "retry_count"

        /** 配置持续不可读（如文件损坏）时不能无限拉起 FGS */
        const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 60_000L
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val showIntent: PendingIntent by lazy { mainActivityPendingIntent(context) }

    /**
     * 按设定时间注册下一个闹钟，后台倒计时在触发后开始
     */
    fun scheduleNext(strategy: ScheduleStrategy, afterEpochMs: Long = 0L): Boolean {
        if (!strategy.enabled) {
            Timber.d("策略 [%s] 已禁用，跳过注册闹钟", strategy.id)
            return false
        }

        val nextTrigger = computeNextTrigger(strategy, afterEpochMs)
        if (nextTrigger == null) {
            Timber.d("策略 [%s] 未找到下一个触发时间，跳过注册闹钟", strategy.id)
            return false
        }

        val scheduledTimeMs = nextTrigger.toInstant().toEpochMilli()
        if (!register(strategy.id, scheduledTimeMs, scheduledTimeMs)) return false

        Timber.i(
            "已为策略 [%s] 注册闹钟，触发时间: %s",
            strategy.id,
            nextTrigger,
        )
        return true
    }

    /** 配置暂不可读时保留本次触发，稍后重试；[retryCount] 为已重试次数，超限放弃 */
    fun scheduleRetry(strategyId: String, scheduledTimeMs: Long, retryCount: Int = 0): Boolean {
        if (retryCount >= MAX_RETRY_COUNT) {
            Timber.w("策略 [%s] 已重试 %d 次，放弃本次触发", strategyId, retryCount)
            return false
        }
        return register(
            strategyId,
            scheduledTimeMs,
            System.currentTimeMillis() + RETRY_DELAY_MS,
            retryCount = retryCount + 1,
        )
    }

    private fun register(
        strategyId: String,
        scheduledTimeMs: Long,
        triggerMs: Long,
        retryCount: Int = 0,
    ): Boolean {
        // setAlarmClock 也要精确闹钟权限，等待授权后统一恢复
        if (!canScheduleExact()) {
            Timber.w("策略 [%s] 未注册：缺少精确闹钟权限", strategyId)
            return false
        }
        return try {
            // 强制脱 Doze 准时投递，代价是状态栏常驻闹钟图标
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerMs, showIntent),
                buildPendingIntent(strategyId, scheduledTimeMs, retryCount),
            )
            true
        } catch (e: SecurityException) {
            // 系统可能在检查与注册之间撤销权限
            Timber.w(e, "策略 [%s] 注册失败：精确闹钟权限已撤销", strategyId)
            false
        }
    }

    /** 取消策略的闹钟 */
    fun cancel(strategyId: String) {
        val pendingIntent = buildPendingIntent(strategyId, 0L)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Timber.i("已取消策略 [%s] 的闹钟", strategyId)
    }

    /** API 31 起注册精确闹钟前须检查授权 */
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
     * 扫描未来 7 天，匹配 dayOfWeek + executionTimes。
     */
    private fun computeNextFixedTime(
        strategy: ScheduleStrategy,
        afterEpochMs: Long
    ): ZonedDateTime? {
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
     * 从 startTimeMs 起，每隔 intervalMinutes 触发一次。
     * 计算公式: next = startTime + ceil((baseline - startTime) / interval) * interval
     */
    private fun computeNextInterval(
        strategy: ScheduleStrategy,
        afterEpochMs: Long
    ): ZonedDateTime? {
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

    private fun buildPendingIntent(
        strategyId: String,
        scheduledTimeMs: Long,
        retryCount: Int = 0,
    ): PendingIntent {
        val intent = Intent(ACTION_SCHEDULE_TRIGGER).apply {
            setClassName(context, "com.aliothmoon.maameow.schedule.receiver.ScheduleReceiver")
            putExtra(EXTRA_STRATEGY_ID, strategyId)
            putExtra(EXTRA_SCHEDULED_TIME, scheduledTimeMs)
            putExtra(EXTRA_RETRY_COUNT, retryCount)
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
