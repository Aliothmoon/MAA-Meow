package com.aliothmoon.maameow.schedule.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.model.ScheduleHealthIssue
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import com.aliothmoon.maameow.schedule.model.ScheduleType
import com.aliothmoon.maameow.schedule.service.OemPowerHint
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val scheduleTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val scheduleStartFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@Composable
internal fun scheduleDayChipLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> stringResource(R.string.schedule_day_full_monday)
    DayOfWeek.TUESDAY -> stringResource(R.string.schedule_day_full_tuesday)
    DayOfWeek.WEDNESDAY -> stringResource(R.string.schedule_day_full_wednesday)
    DayOfWeek.THURSDAY -> stringResource(R.string.schedule_day_full_thursday)
    DayOfWeek.FRIDAY -> stringResource(R.string.schedule_day_full_friday)
    DayOfWeek.SATURDAY -> stringResource(R.string.schedule_day_full_saturday)
    DayOfWeek.SUNDAY -> stringResource(R.string.schedule_day_full_sunday)
}

@Composable
internal fun scheduleDaySummaryLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> stringResource(R.string.schedule_day_short_monday)
    DayOfWeek.TUESDAY -> stringResource(R.string.schedule_day_short_tuesday)
    DayOfWeek.WEDNESDAY -> stringResource(R.string.schedule_day_short_wednesday)
    DayOfWeek.THURSDAY -> stringResource(R.string.schedule_day_short_thursday)
    DayOfWeek.FRIDAY -> stringResource(R.string.schedule_day_short_friday)
    DayOfWeek.SATURDAY -> stringResource(R.string.schedule_day_short_saturday)
    DayOfWeek.SUNDAY -> stringResource(R.string.schedule_day_short_sunday)
}

@Composable
internal fun scheduleExecutionResultLabel(result: ExecutionResult): String = when (result) {
    ExecutionResult.STARTED -> stringResource(R.string.schedule_result_started)
    ExecutionResult.FAILED_VALIDATION -> stringResource(R.string.schedule_result_failed_validation)
    ExecutionResult.FAILED_START -> stringResource(R.string.schedule_result_failed_start)
    ExecutionResult.FAILED_UI_LAUNCH -> stringResource(R.string.schedule_result_failed_ui_launch)
    ExecutionResult.SKIPPED_BUSY -> stringResource(R.string.schedule_result_skipped_busy)
    ExecutionResult.SKIPPED_LOCKED -> stringResource(R.string.schedule_result_skipped_locked)
    ExecutionResult.CANCELLED -> stringResource(R.string.schedule_result_cancelled)
}

/**
 * 健康卡文案：陈述现状（「未忽略电池优化」），与 [schedulePermissionActionText] 的动作式对应
 *
 * [backend] 让后端项跟随启动模式显示 Shizuku / Root，与 home_toast_backend_* 写法一致
 */
@Composable
internal fun scheduleHealthIssueText(
    issue: ScheduleHealthIssue,
    backend: RemoteBackend = RemoteBackend.SHIZUKU,
): Pair<String, String> = when (issue) {
    ScheduleHealthIssue.BACKEND ->
        stringResource(R.string.schedule_health_backend, backend.display) to
            stringResource(R.string.schedule_health_backend_desc)

    ScheduleHealthIssue.BATTERY ->
        stringResource(R.string.schedule_health_battery) to
            stringResource(R.string.schedule_health_battery_desc)

    ScheduleHealthIssue.EXACT_ALARM ->
        stringResource(R.string.schedule_exact_alarm_blocked) to
            stringResource(R.string.schedule_exact_alarm_hint)

    ScheduleHealthIssue.NOTIFICATION ->
        stringResource(R.string.schedule_health_notification) to
            stringResource(R.string.schedule_health_notification_desc)

    ScheduleHealthIssue.OVERLAY ->
        stringResource(R.string.schedule_health_overlay) to
            stringResource(R.string.schedule_health_overlay_desc)
}

/** 向导文案：陈述动作（「关闭电池优化」） */
@Composable
internal fun schedulePermissionActionText(issue: ScheduleHealthIssue): Pair<String, String> = when (issue) {
    ScheduleHealthIssue.BATTERY ->
        stringResource(R.string.schedule_permission_tip_battery_optimization) to
            stringResource(R.string.schedule_permission_battery_desc)

    ScheduleHealthIssue.EXACT_ALARM ->
        stringResource(R.string.schedule_permission_tip_exact_alarm) to
            stringResource(R.string.schedule_exact_alarm_hint)

    ScheduleHealthIssue.NOTIFICATION ->
        stringResource(R.string.schedule_permission_tip_notification) to
            stringResource(R.string.schedule_health_notification_desc)

    ScheduleHealthIssue.OVERLAY ->
        stringResource(R.string.schedule_permission_tip_overlay) to
            stringResource(R.string.schedule_health_overlay_desc)

    // 不进向导，回落到卡片文案只为 when 穷尽
    ScheduleHealthIssue.BACKEND -> scheduleHealthIssueText(issue)
}

@Composable
internal fun scheduleOemPowerHintText(hint: OemPowerHint?): String? = when (hint) {
    OemPowerHint.MIUI -> stringResource(R.string.schedule_oem_hint_miui)
    OemPowerHint.HUAWEI -> stringResource(R.string.schedule_oem_hint_huawei)
    OemPowerHint.OPPO -> stringResource(R.string.schedule_oem_hint_oppo)
    OemPowerHint.VIVO -> stringResource(R.string.schedule_oem_hint_vivo)
    OemPowerHint.SAMSUNG -> stringResource(R.string.schedule_oem_hint_samsung)
    OemPowerHint.MEIZU -> stringResource(R.string.schedule_oem_hint_meizu)
    null -> null
}

@Composable
internal fun localizedScheduleStrategySummary(strategy: ScheduleStrategy): String {
    return when (strategy.scheduleType) {
        ScheduleType.FIXED_TIME -> {
            val days = strategy.daysOfWeek.sorted()
                .map { scheduleDaySummaryLabel(it) }
                .joinToString(" ")
            val times = strategy.executionTimes.joinToString(" ") { it.format(scheduleTimeFormatter) }
            listOf(days, times).filter { it.isNotBlank() }.joinToString(" ")
        }

        ScheduleType.INTERVAL -> {
            val totalMinutes = strategy.intervalMinutes ?: 0
            val days = totalMinutes / (24 * 60)
            val hours = (totalMinutes % (24 * 60)) / 60
            val intervalText = when {
                days > 0 && hours > 0 -> {
                    stringResource(R.string.schedule_interval_every_days_hours, days, hours)
                }

                days > 0 -> stringResource(R.string.schedule_interval_every_days, days)
                else -> stringResource(R.string.schedule_interval_every_hours, hours)
            }
            val startText = strategy.startTimeMs?.let { ms ->
                val formatted = Instant.ofEpochMilli(ms)
                    .atZone(ZoneId.systemDefault())
                    .format(scheduleStartFormatter)
                stringResource(R.string.schedule_interval_starts_from, formatted)
            }.orEmpty()
            listOf(intervalText, startText).filter { it.isNotBlank() }.joinToString(" ")
        }
    }
}
