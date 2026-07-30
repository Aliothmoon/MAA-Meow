package com.aliothmoon.maameow.domain.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar

/**
 * 定时唤醒调度器。基于 [AlarmManager.setExactAndAllowWhileIdle] 实现精确唤醒，
 * 绕过 Doze 让设备在屏幕熄灭状态下也能被唤醒执行任务。
 *
 * 每次只维护「下一个最近的」闹钟。闹钟触发后由 [WakeAlarmReceiver] 负责再调度一次。
 * 用户修改配置后需调用 [reschedule] 让新时间生效。
 */
class WakeAlarmScheduler(
    private val context: Context,
    private val appSettingsManager: AppSettingsManager,
) {
    companion object {
        const val ACTION_WAKE = "com.aliothmoon.maameow.action.WAKE_ALARM"
        private const val REQUEST_CODE = 0xCAFE
        private const val TAG = "WakeAlarmScheduler"
    }

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + kotlinx.coroutines.CoroutineExceptionHandler { _, t ->
            // 兜底：不让未捕获异常冒到 CrashHandler 杀进程。
            // reschedule / cancelAll 都是 best-effort，任何异常都只记日志。
            Timber.w(t, "$TAG: unhandled coroutine exception (swallowed)")
        }
    )
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * 读取当前配置并设置下一个闹钟。若开关关闭或无合法时间点则取消全部闹钟。
     * 所有异常内部消化，只记日志，不抛到外层（避免协程未捕获异常触发 CrashHandler 杀进程）。
     */
    fun reschedule() {
        scope.launch {
            runCatching {
                val enabled = appSettingsManager.wakeScheduleEnabled.first()
                if (!enabled) {
                    cancelAll()
                    Timber.i("$TAG: disabled, alarms cancelled")
                    return@runCatching
                }
                val raw = appSettingsManager.wakeScheduleTimesCsv.first()
                val times = parseTimes(raw)
                if (times.isEmpty()) {
                    cancelAll()
                    Timber.i("$TAG: no valid times in '$raw', alarms cancelled")
                    return@runCatching
                }
                val nextMs = computeNextTriggerMs(times)
                arm(nextMs)
            }.onFailure { t ->
                Timber.w(t, "$TAG: reschedule failed (non-fatal)")
            }
        }
    }

    /**
     * 取消所有唤醒闹钟。
     * 安全语义：即使系统里根本没有已注册的 PendingIntent，也不抛异常。
     */
    fun cancelAll() {
        runCatching {
            // Android 12+ 强制要求 FLAG_IMMUTABLE / FLAG_MUTABLE，与 arm 时保持一致，
            // 否则 FLAG_NO_CREATE 单独使用会抛 IllegalArgumentException 直接崩溃。
            val lookupFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_NO_CREATE
            }
            // FLAG_NO_CREATE 在「系统中没有匹配 PendingIntent」时返回 null，这是合法状态，
            // 此时不需要做任何事，直接返回即可。
            val pending = buildPendingIntent(flag = lookupFlag) ?: run {
                Timber.i("$TAG: no existing alarm PendingIntent, nothing to cancel")
                return
            }
            pending.cancel()
            alarmManager.cancel(pending)
            Timber.i("$TAG: all alarms cancelled")
        }.onFailure { t ->
            Timber.w(t, "$TAG: cancelAll failed (non-fatal)")
        }
    }

    /**
     * 解析 `HH:mm` 逗号分隔字符串，过滤非法项，返回有效对列表。
     */
    internal fun parseTimes(csv: String): List<Pair<Int, Int>> =
        csv.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { token ->
                val parts = token.split(':')
                if (parts.size != 2) return@mapNotNull null
                val h = parts[0].toIntOrNull() ?: return@mapNotNull null
                val m = parts[1].toIntOrNull() ?: return@mapNotNull null
                if (h !in 0..23 || m !in 0..59) null else h to m
            }

    /**
     * 给定 (hour, minute) 列表，计算相对「现在」下一个最近的触发时刻（毫秒）。
     * 列表非空时才调用。
     */
    internal fun computeNextTriggerMs(times: List<Pair<Int, Int>>): Long {
        val now = Calendar.getInstance()
        val candidates = times.map { (h, m) ->
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
            }.timeInMillis
        }
        return candidates.min()
    }

    private fun arm(triggerMs: Long) {
        runCatching {
            val baseFlag = PendingIntent.FLAG_UPDATE_CURRENT
            val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                baseFlag or PendingIntent.FLAG_IMMUTABLE
            } else {
                baseFlag
            }
            val pending = buildPendingIntent(flag = flag)
                ?: error("arm: buildPendingIntent returned null (unexpected)")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pending)
            } else {
                @Suppress("DEPRECATION")
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pending)
            }
            val cal = Calendar.getInstance().apply { timeInMillis = triggerMs }
            Timber.i(
                "$TAG: armed alarm for %02d:%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                cal.get(Calendar.SECOND)
            )
        }.onFailure { t ->
            // SecurityException = 用户未授权 SCHEDULE_EXACT_ALARM，非致命；
            // 其他异常同样只记日志，不冒泡。
            Timber.w(t, "$TAG: arm() failed (non-fatal). Check SCHEDULE_EXACT_ALARM permission if SecurityException.")
        }
    }

    private fun buildPendingIntent(flag: Int): PendingIntent? {
        val intent = Intent(context, WakeAlarmReceiver::class.java).apply {
            action = ACTION_WAKE
        }
        // getBroadcast 在 FLAG_NO_CREATE 且系统中无匹配 PendingIntent 时返回 null。
        // 返回类型声明为 nullable，调用方必须处理 null。
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flag)
    }

    /** 工具：查询当前是否持有「忽略电池优化」豁免（Doze 白名单）。 */
    fun isIgnoringBatteryOptimizations(packageName: String = context.packageName): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }
}
