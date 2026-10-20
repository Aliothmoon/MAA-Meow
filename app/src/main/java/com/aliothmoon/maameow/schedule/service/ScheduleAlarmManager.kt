package com.aliothmoon.maameow.schedule.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
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
        const val ACTION_SCHEDULE_RESYNC = "com.aliothmoon.maameow.SCHEDULE_RESYNC"
        const val EXTRA_STRATEGY_ID = "strategy_id"
        const val EXTRA_SCHEDULED_TIME = "scheduled_time"
        const val EXTRA_RETRY_COUNT = "retry_count"

        /** 配置持续不可读（如文件损坏）时不能无限拉起 FGS */
        const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 60_000L

        /** 重试用尽后只做轻量重排，不拉 FGS */
        const val RESYNC_DELAY_MS = 30 * 60_000L
        private const val RESYNC_REQUEST_CODE = 0x5C4E

        /** 固定时间策略认上次触发点的回拨窗口 */
        const val FIXED_RESUME_WINDOW_MS = 24 * 60 * 60_000L

        private const val FIRED_PREFS = "schedule_fired"

        /** 不能并入 FIRED_PREFS：重排会清掉非策略 id 的键 */
        private const val ALARM_PREFS = "schedule_alarm"
        private const val KEY_USE_ALARM_CLOCK = "use_alarm_clock"

        /**
         * 重排起点：上次触发点仍在「未来」说明时钟往回拨过，从它之后排，免得同一时段再跑一遍
         * 回拨超过一个周期（固定时间按 24 小时）视为时钟纠错，回到按当前时间算
         */
        fun resumeAfter(strategy: ScheduleStrategy, lastFiredMs: Long?, nowMs: Long): Long {
            if (lastFiredMs == null || lastFiredMs <= nowMs) return 0L
            val window = when (strategy.scheduleType) {
                ScheduleType.FIXED_TIME -> FIXED_RESUME_WINDOW_MS
                ScheduleType.INTERVAL -> (strategy.intervalMinutes ?: 0) * 60_000L
            }
            return if (lastFiredMs - nowMs <= window) lastFiredMs else 0L
        }
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val showIntent: PendingIntent by lazy { mainActivityPendingIntent(context) }

    private val firedPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(FIRED_PREFS, Context.MODE_PRIVATE)
    }

    private val alarmPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(ALARM_PREFS, Context.MODE_PRIVATE)
    }

    /** 不放 DataStore：开机重排时它可能还没读完 */
    var useAlarmClock: Boolean
        get() = alarmPrefs.getBoolean(KEY_USE_ALARM_CLOCK, true)
        set(value) = alarmPrefs.edit { putBoolean(KEY_USE_ALARM_CLOCK, value) }

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

    /**
     * 配置暂不可读时保留本次触发，稍后重试；[retryCount] 为已重试次数
     * 重试与下次触发各占一个槽，重排不会冲掉在等的重试；用尽后转为定期重排，定时链不断
     */
    fun scheduleRetry(strategyId: String, scheduledTimeMs: Long, retryCount: Int = 0): Boolean {
        if (retryCount >= MAX_RETRY_COUNT) {
            Timber.w("策略 [%s] 已重试 %d 次，放弃本次触发，改为稍后重排", strategyId, retryCount)
            scheduleResync()
            return false
        }
        return register(
            strategyId,
            scheduledTimeMs,
            System.currentTimeMillis() + RETRY_DELAY_MS,
            retryCount = retryCount + 1,
        )
    }

    /** 稍后从持久化配置整批重排，由 BootReceiver 处理 */
    fun scheduleResync(): Boolean {
        if (!canScheduleExact()) {
            Timber.w("重排闹钟未注册：缺少精确闹钟权限")
            return false
        }
        return try {
            setExact(System.currentTimeMillis() + RESYNC_DELAY_MS, buildResyncPendingIntent())
            Timber.i("已注册 %d 分钟后重排闹钟", RESYNC_DELAY_MS / 60_000L)
            true
        } catch (e: SecurityException) {
            Timber.w(e, "重排闹钟注册失败：精确闹钟权限已撤销")
            false
        }
    }

    /** 记下本次触发的时段，重排时据此防止时钟回拨导致重复执行 */
    fun markFired(strategyId: String, scheduledTimeMs: Long) {
        if (scheduledTimeMs <= 0L) return
        firedPrefs.edit(commit = true) { putLong(strategyId, scheduledTimeMs) }
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
            setExact(triggerMs, buildPendingIntent(strategyId, scheduledTimeMs, retryCount))
            true
        } catch (e: SecurityException) {
            // 系统可能在检查与注册之间撤销权限
            Timber.w(e, "策略 [%s] 注册失败：精确闹钟权限已撤销", strategyId)
            false
        }
    }

    private fun setExact(triggerMs: Long, operation: PendingIntent) {
        if (useAlarmClock) {
            // 强制脱 Doze 准时投递，代价是状态栏常驻闹钟图标
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMs, showIntent), operation)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, operation)
        }
    }

    /**
     * 取消策略的闹钟；[keepRetry] 为 true 时只撤下次触发
     * 编辑保存只换下次触发，在等的重试照常补跑
     */
    fun cancel(strategyId: String, keepRetry: Boolean = false) {
        cancelSlot(buildPendingIntent(strategyId, 0L))
        if (!keepRetry) cancelSlot(buildPendingIntent(strategyId, 0L, retryCount = 1))
        Timber.i("已取消策略 [%s] 的闹钟，保留重试=%s", strategyId, keepRetry)
    }

    private fun cancelSlot(pendingIntent: PendingIntent) {
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /** API 31 起注册精确闹钟前须检查授权 */
    fun canScheduleExact(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
        return ExactAlarmSettings.isAllowed(Build.VERSION.SDK_INT, granted)
    }

    /** 31 以下没有那个系统开关页，入口要藏掉，否则点了什么也不会发生 */
    fun hasExactAlarmToggle(): Boolean = ExactAlarmSettings.hasToggle(Build.VERSION.SDK_INT)

    /**
     * 先撤后立：禁用的连重试一起撤，启用的只换下次触发，在等的重试保留
     * 起点见 [resumeAfter]
     */
    fun rescheduleAll(strategies: List<ScheduleStrategy>) {
        val now = System.currentTimeMillis()
        val fired = firedPrefs.all
        strategies.forEach { strategy ->
            if (!strategy.enabled) {
                cancel(strategy.id)
                return@forEach
            }
            cancel(strategy.id, keepRetry = true)
            scheduleNext(strategy, resumeAfter(strategy, fired[strategy.id] as? Long, now))
        }
        cancelSlot(buildResyncPendingIntent())

        val ids = strategies.mapTo(HashSet()) { it.id }
        val stale = fired.keys.filterNot { it in ids }
        if (stale.isNotEmpty()) firedPrefs.edit { stale.forEach(::remove) }
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

    /** 下次触发与重试按 requestCode 分槽，[retryCount] > 0 即重试槽 */
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
            requestCode(strategyId, retry = retryCount > 0),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildResyncPendingIntent(): PendingIntent {
        val intent = Intent(ACTION_SCHEDULE_RESYNC).apply {
            setClassName(context, "com.aliothmoon.maameow.schedule.receiver.BootReceiver")
        }
        return PendingIntent.getBroadcast(
            context,
            RESYNC_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 下次触发沿用旧 requestCode，升级前挂好的闹钟仍能被撤 */
    private fun requestCode(strategyId: String, retry: Boolean): Int =
        (if (retry) "$strategyId#retry" else strategyId).hashCode() and 0x7FFFFFFF
}
