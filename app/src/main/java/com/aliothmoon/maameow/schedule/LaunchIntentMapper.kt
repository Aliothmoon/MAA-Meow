package com.aliothmoon.maameow.schedule

import android.content.Context
import android.content.Intent
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.launch.LaunchRequest
import com.aliothmoon.maameow.domain.launch.LaunchSource
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import com.aliothmoon.maameow.schedule.model.ScheduledExecutionRequest
import java.util.UUID

object LaunchIntentMapper {

    fun fromStrategy(
        strategy: ScheduleStrategy,
        scheduledTimeMs: Long,
        requestId: String = UUID.randomUUID().toString(),
    ): LaunchRequest = LaunchRequest(
        requestId = requestId,
        source = LaunchSource.Schedule,
        profileId = strategy.profileId,
        displayName = strategy.name,
        scheduledTimeMs = scheduledTimeMs,
        forceStart = strategy.forceStart,
        autoSleepAfterTask = strategy.autoSleepAfterTask,
        strategyId = strategy.id,
        countdownSeconds = ScheduledExecutionRequest.COUNTDOWN_SECONDS,
    )

    fun fromExternalIntent(context: Context, intent: Intent?): LaunchRequest? {
        if (intent?.action != ScheduledExecutionRequest.ACTION_LAUNCH_PROFILE) return null
        val profileId = intent.getStringExtra(ScheduledExecutionRequest.EXTRA_PROFILE_ID)
            ?: return null
        return LaunchRequest(
            requestId = UUID.randomUUID().toString(),
            source = LaunchSource.External,
            profileId = profileId,
            displayName = context.getString(R.string.schedule_log_external_name),
            scheduledTimeMs = System.currentTimeMillis(),
            forceStart = intent.getBooleanExtra(
                ScheduledExecutionRequest.EXTRA_FORCE_START,
                false,
            ),
            countdownSeconds = ScheduledExecutionRequest.COUNTDOWN_SECONDS,
        )
    }

    fun toShowIntent(context: Context, request: LaunchRequest): Intent {
        return Intent(context, com.aliothmoon.maameow.MainActivity::class.java).apply {
            action = ScheduledExecutionRequest.ACTION_SHOW_SCHEDULE_EXECUTION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(ScheduledExecutionRequest.EXTRA_REQUEST_ID, request.requestId)
            putExtra(ScheduledExecutionRequest.EXTRA_STRATEGY_ID, request.strategyId)
            putExtra(ScheduledExecutionRequest.EXTRA_STRATEGY_NAME, request.displayName)
            putExtra(ScheduledExecutionRequest.EXTRA_PROFILE_ID, request.profileId)
            putExtra(ScheduledExecutionRequest.EXTRA_SCHEDULED_TIME, request.scheduledTimeMs)
            putExtra(ScheduledExecutionRequest.EXTRA_FORCE_START, request.forceStart)
            putExtra(
                ScheduledExecutionRequest.EXTRA_AUTO_SLEEP_AFTER_TASK,
                request.autoSleepAfterTask,
            )
        }
    }

    fun isShowScheduleIntent(intent: Intent?): Boolean =
        intent?.action == ScheduledExecutionRequest.ACTION_SHOW_SCHEDULE_EXECUTION
}
