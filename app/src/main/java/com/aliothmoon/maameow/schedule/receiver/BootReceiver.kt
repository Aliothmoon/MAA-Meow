package com.aliothmoon.maameow.schedule.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.service.ScheduleAlarmManager
import com.aliothmoon.maameow.schedule.service.ShizukuDownMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext
import timber.log.Timber

/**
 * 设备重启、应用更新、时区变更或重试用尽后恢复所有定时任务闹钟
 *
 * 不收 TIME_SET：闹钟是绝对时刻，调时钟不改变它对应的本地时刻；
 * 往前调时到点的闹钟会立刻补发，这时重排反而会把它换成下一次
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESTORE_ACTIONS) return
        Timber.i("Schedule restore triggered by: %s", intent.action)
        val pendingResult = goAsync()

        val repository: ScheduleStrategyRepository = GlobalContext.get().get()
        val alarmManager: ScheduleAlarmManager = GlobalContext.get().get()
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // adb 模式的 Shizuku 重启即失效
            GlobalContext.get().get<ShizukuDownMonitor>().armAfterBoot()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val loaded = withTimeoutOrNull(5_000L) {
                    repository.isLoaded.filter { it }.first()
                }
                if (loaded == null) {
                    // 读不到配置也要留个后手，否则要等用户手动打开 App 才能恢复
                    Timber.w("Schedule restore: DataStore load timeout, resync later")
                    alarmManager.scheduleResync()
                    return@launch
                }
                val strategies = repository.strategies.value
                alarmManager.rescheduleAll(strategies)
                Timber.i("Schedule restore complete: %d strategies", strategies.size)
            } catch (e: Exception) {
                Timber.e(e, "Schedule restore failed")
                runCatching { alarmManager.scheduleResync() }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val RESTORE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            ScheduleAlarmManager.ACTION_SCHEDULE_RESYNC,
        )
    }
}
