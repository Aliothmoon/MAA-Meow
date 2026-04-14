package com.aliothmoon.maameow.domain.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aliothmoon.maameow.MainActivity
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.AppSettingsManager.EventNotificationLevel
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

class MaaEventNotifier(
    context: Context,
    private val appSettingsManager: AppSettingsManager,
) {
    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_DEFAULT = "maa_events_low"
        private const val CHANNEL_HIGH = "maa_events_high"

        private const val ID_TASK_STATUS = 9001
        private val eventIdGenerator = AtomicInteger(9100)
    }

    init {
        ensureChannels()
    }

    private fun string(@StringRes resId: Int, vararg args: Any): String =
        appContext.getString(resId, *args)

    private fun ensureChannels() {
        val channels = listOf(
            NotificationChannel(
                CHANNEL_DEFAULT,
                string(R.string.notification_event_channel_silent_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = string(R.string.notification_event_channel_silent_desc)
                setSound(null, null)
                enableVibration(false)
            },
            NotificationChannel(
                CHANNEL_HIGH,
                string(R.string.notification_event_channel_popup_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = string(R.string.notification_event_channel_popup_desc)
            }
        )
        manager.createNotificationChannels(channels)
    }

    fun notifyAllTasksCompleted(summary: String) {
        send(R.string.notification_event_all_tasks_completed, summary, ID_TASK_STATUS)
    }

    fun notifyTaskError(taskName: String) {
        send(R.string.notification_event_task_error, taskName, ID_TASK_STATUS, isError = true)
    }

    fun notifyRecruitSpecialTag(tag: String) {
        send(
            R.string.notification_event_recruit_tip,
            string(R.string.notification_event_recruit_special_tag, tag),
            eventIdGenerator.getAndIncrement()
        )
    }

    fun notifyRecruitRobotTag(tag: String) {
        send(
            R.string.notification_event_recruit_tip,
            string(R.string.notification_event_recruit_robot_tag, tag),
            eventIdGenerator.getAndIncrement()
        )
    }

    fun notifyRecruitHighRarity(level: Int) {
        send(
            R.string.notification_event_recruit_tip,
            string(R.string.notification_event_recruit_high_rarity, level),
            eventIdGenerator.getAndIncrement()
        )
    }

    fun notifySubTaskFailure(message: String) {
        send(
            R.string.notification_event_subtask_failure,
            message,
            eventIdGenerator.getAndIncrement(),
            isError = true
        )
    }

    private fun send(
        @StringRes titleRes: Int,
        text: String,
        notifyId: Int,
        isError: Boolean = false,
    ) {
        val level = appSettingsManager.eventNotificationLevel.value
        if (level == EventNotificationLevel.OFF) return

        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            Timber.w("Notification is disabled or missing POST_NOTIFICATIONS permission")
            return
        }

        val channelId = if (level == EventNotificationLevel.HIGH) CHANNEL_HIGH else CHANNEL_DEFAULT
        val priority = if (level == EventNotificationLevel.HIGH) {
            NotificationCompat.PRIORITY_HIGH
        } else {
            NotificationCompat.PRIORITY_LOW
        }

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notifyId, // 使用 notifyId 作为 requestCode 避免不同通知的 Intent 互相覆盖
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = string(titleRes)
        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setPriority(priority)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .apply {
                if (level == EventNotificationLevel.HIGH) {
                    setDefaults(NotificationCompat.DEFAULT_ALL)
                }
                if (isError) {
                    color = 0xFFD32F2F.toInt()
                }
            }
            .build()

        try {
            manager.notify(notifyId, notification)
        } catch (e: Exception) {
            Timber.e(e, "Failed to notify: $title")
        }
    }
}
