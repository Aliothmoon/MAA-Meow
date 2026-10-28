package com.aliothmoon.maameow.presentation.components

import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.WeeklyScheduled
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility
import java.time.DayOfWeek

@Composable
fun dayOfWeekLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> stringResource(R.string.schedule_day_full_monday)
    DayOfWeek.TUESDAY -> stringResource(R.string.schedule_day_full_tuesday)
    DayOfWeek.WEDNESDAY -> stringResource(R.string.schedule_day_full_wednesday)
    DayOfWeek.THURSDAY -> stringResource(R.string.schedule_day_full_thursday)
    DayOfWeek.FRIDAY -> stringResource(R.string.schedule_day_full_friday)
    DayOfWeek.SATURDAY -> stringResource(R.string.schedule_day_full_saturday)
    DayOfWeek.SUNDAY -> stringResource(R.string.schedule_day_full_sunday)
}

@Composable
fun WeeklyScheduleSection(
    config: WeeklyScheduled,
    onEnabledChange: (Boolean) -> Unit,
    onScheduleChange: (Map<String, Boolean>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CheckBoxWithExpandableTip(
            checked = config.useWeeklySchedule,
            onCheckedChange = onEnabledChange,
            label = stringResource(R.string.panel_weekly_schedule),
            tipText = stringResource(R.string.panel_weekly_schedule_tip)
        )
        MaaAnimatedVisibility(
            visible = config.useWeeklySchedule,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 4.dp)
            ) {
                DayOfWeek.entries.forEach { day ->
                    val key = day.name
                    val selected = config.weeklySchedule[key] != false
                    Surface(
                        onClick = {
                            val updated = config.weeklySchedule.toMutableMap()
                            updated[key] = !selected
                            onScheduleChange(updated)
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = if (selected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Text(
                            text = dayOfWeekLabel(day),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
