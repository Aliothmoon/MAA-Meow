package com.aliothmoon.maameow.data.notification.live

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.aliothmoon.maameow.domain.notification.LiveBackend
import com.aliothmoon.maameow.domain.notification.LiveCapability
import com.aliothmoon.maameow.domain.notification.LiveNotifyIds
import com.aliothmoon.maameow.domain.notification.LiveSession
import com.aliothmoon.maameow.domain.notification.LiveUpdatePublisher
import timber.log.Timber

class PlainNotificationPublisher(
    context: Context,
    private val factory: LiveNotificationFactory,
    private val promotedDetector: AospPromotedDetector,
) : LiveUpdatePublisher {

    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override val capability: LiveCapability
        get() = LiveCapability(
            backend = LiveBackend.PLAIN,
            postNotifications = NotificationManagerCompat.from(appContext).areNotificationsEnabled(),
            promotedAvailable = promotedDetector.isApiSupported(),
            promotedGranted = promotedDetector.isGranted(),
            focusLikely = false,
            focusGranted = false,
        )

    override fun build(session: LiveSession): Notification =
        factory.build(session, requestPromoted = false)

    override fun publish(session: LiveSession) {
        notifyOrSkip(session.sessionId, build(session))
    }

    override fun publishForeground(session: LiveSession): Notification =
        build(session).also { notifyOrSkip(session.sessionId, it) }

    override fun cancel(sessionId: String) {
        runCatching { manager.cancel(LiveNotifyIds.of(sessionId)) }
    }

    fun notifyOrSkip(sessionId: String, notification: Notification) {
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            Timber.w("Notification disabled, skip live publish %s", sessionId)
            return
        }
        try {
            manager.notify(LiveNotifyIds.of(sessionId), notification)
        } catch (e: SecurityException) {
            Timber.w(e, "notify blocked by POST_NOTIFICATIONS denial")
        }
    }
}
