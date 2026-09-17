package com.aliothmoon.maameow.data.notification.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.aliothmoon.maameow.MainActivity
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.notification.LiveCategory
import com.aliothmoon.maameow.domain.notification.LiveNotifyIds
import com.aliothmoon.maameow.domain.notification.LiveSession
import java.util.concurrent.atomic.AtomicBoolean

class LiveNotificationFactory(
    context: Context,
    private val style: LiveUpdateStyle,
    private val trackerIcons: TrackerIconStore,
) {

    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelsReady = AtomicBoolean(false)

    fun ensureChannels() {
        // 通道/分组创建每进程一次；每次通知都跑一遍是纯系统往返开销
        if (!channelsReady.compareAndSet(false, true)) return

        migrateLegacyChannels()

        manager.createNotificationChannel(
            NotificationChannel(
                LiveNotifyIds.CHANNEL_PROGRESS,
                appContext.getString(R.string.notification_channel_task_execution_live),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description =
                    appContext.getString(R.string.notification_channel_task_execution_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )

        // 岛走独立 HIGH 无声通道：HyperOS 焦点渲染要求
        manager.createNotificationChannelGroup(
            NotificationChannelGroup(
                LiveNotifyIds.GROUP_ISLAND,
                appContext.getString(R.string.notification_section_live),
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                LiveNotifyIds.CHANNEL_ISLAND,
                appContext.getString(R.string.notification_channel_task_execution_island),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description =
                    appContext.getString(R.string.notification_channel_task_execution_island_desc)
                group = LiveNotifyIds.GROUP_ISLAND
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )

        // 结果通道按内部通知级别分静默/弹出，和 MaaEventNotifier 的分级保持一致
        manager.createNotificationChannel(
            NotificationChannel(
                LiveNotifyIds.CHANNEL_RESULT,
                appContext.getString(R.string.notification_channel_task_execution_result),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description =
                    appContext.getString(R.string.notification_channel_task_execution_result_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                LiveNotifyIds.CHANNEL_RESULT_ALERT,
                appContext.getString(R.string.notification_channel_task_execution_result_alert),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description =
                    appContext.getString(R.string.notification_channel_task_execution_result_desc)
                setShowBadge(false)
            }
        )
    }

    /**
     * 旧 id 一次性清理：岛分组沿用了第三方命名且与进度通道重名，结果通道建在了默认提示音上
     *
     * 通道属性建后不可改，只能换 id 重开；删除重建不会清掉用户设置，故不能用来抬 importance
     */
    private fun migrateLegacyChannels() {
        runCatching {
            manager.deleteNotificationChannelGroup(LiveNotifyIds.LEGACY_GROUP_ISLAND)
            manager.deleteNotificationChannel(LiveNotifyIds.LEGACY_CHANNEL_RESULT)
        }
    }

    fun build(
        session: LiveSession,
        requestPromoted: Boolean,
        extras: android.os.Bundle? = null,
        hyperIsland: Boolean = false,
    ): Notification {
        ensureChannels()
        // 岛进度走 HIGH 无声专属通道；通知级 setSilent 会压掉浮出
        val channelId = when {
            hyperIsland && session.category == LiveCategory.PROGRESS -> LiveNotifyIds.CHANNEL_ISLAND
            session.category == LiveCategory.PROGRESS -> LiveNotifyIds.CHANNEL_PROGRESS
            session.alert -> LiveNotifyIds.CHANNEL_RESULT_ALERT
            else -> LiveNotifyIds.CHANNEL_RESULT
        }
        val notifyId = LiveNotifyIds.of(session.sessionId)
        val barColor = style.color(
            isError = session.isError,
            isCompleted = session.category == LiveCategory.RESULT,
        )

        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_maa_logo)
            .setColor(barColor)
            .setContentTitle(session.title)
            .setContentText(session.text)
            .setContentIntent(contentIntent(notifyId))
            .setOngoing(session.ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(!hyperIsland && session.category == LiveCategory.PROGRESS)
            .setAutoCancel(!session.ongoing)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setTicker(session.title)

        if (hyperIsland) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            builder.setCategory(categoryOf(session))
            // 岛上状态栏图标跟随用户设置（原生 Live Updates 保持应用默认图标不变）
            trackerIcons.bitmapOrNull()?.let { builder.setSmallIcon(IconCompat.createWithBitmap(it)) }
            if (session.category == LiveCategory.PROGRESS) {
                val percent = session.progressPercent()
                if (percent == null) {
                    builder.setProgress(0, 0, true)
                } else {
                    builder.setProgress(100, percent, false)
                }
            } else {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(session.text))
            }
        } else {
            if (session.capsuleText.isNotBlank()) {
                builder.setShortCriticalText(session.capsuleText)
            } else if (session.capsuleHidden) {
                builder.setShortCriticalText("")
            }
            builder.setCategory(categoryOf(session))
            builder.setStyle(
                if (session.category == LiveCategory.PROGRESS) {
                    progressStyle(session)
                } else {
                    NotificationCompat.BigTextStyle().bigText(session.text)
                }
            )
        }

        if (requestPromoted && session.ongoing && !hyperIsland) {
            builder.setRequestPromotedOngoing(true)
        }
        extras?.let { builder.addExtras(it) }
        return builder.build()
    }

    fun contentIntent(notifyId: Int): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            notifyId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun categoryOf(session: LiveSession): String =
        if (session.category == LiveCategory.PROGRESS) {
            NotificationCompat.CATEGORY_PROGRESS
        } else {
            NotificationCompat.CATEGORY_STATUS
        }

    private fun progressStyle(session: LiveSession): NotificationCompat.ProgressStyle {
        val max = session.progressMax ?: LiveNotifyIds.PROGRESS_STYLE_MAX
        val current = session.progressCurrent ?: 0
        val color = style.color(isError = session.isError, isCompleted = false)
        val progressStyle = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressIndeterminate(session.progressMax == null || session.progressMax == 0)
            .setProgressTrackerIcon(trackerIconCompat())
            .addProgressSegment(
                NotificationCompat.ProgressStyle.Segment(max.coerceAtLeast(1)).setColor(color)
            )
        if (session.progressMax != null && session.progressMax > 0) {
            progressStyle.setProgress(current.coerceIn(0, max))
        }
        return progressStyle
    }

    private fun trackerIconCompat(): IconCompat =
        trackerIcons.bitmapOrNull()?.let { IconCompat.createWithBitmap(it) }
            ?: IconCompat.createWithResource(appContext, R.drawable.ic_progress_tracker)
}
