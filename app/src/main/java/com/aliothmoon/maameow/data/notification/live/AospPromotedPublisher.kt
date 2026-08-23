package com.aliothmoon.maameow.data.notification.live

import android.app.Notification
import android.content.Context
import com.aliothmoon.maameow.domain.notification.LiveBackend
import com.aliothmoon.maameow.domain.notification.LiveCapability
import com.aliothmoon.maameow.domain.notification.LiveSession
import com.aliothmoon.maameow.domain.notification.LiveUpdatePublisher

class AospPromotedPublisher(
    context: Context,
    private val factory: LiveNotificationFactory,
    promotedDetector: AospPromotedDetector,
) : LiveUpdatePublisher {

    private val plain = PlainNotificationPublisher(context, factory, promotedDetector)

    override val capability: LiveCapability
        get() = plain.capability.copy(
            backend = LiveBackend.AOSP_PROMOTED,
            promotedGranted = true,
        )

    override fun build(session: LiveSession): Notification =
        factory.build(session, requestPromoted = session.ongoing)

    override fun publish(session: LiveSession) {
        plain.notifyOrSkip(session.sessionId, build(session))
    }

    override fun publishForeground(session: LiveSession): Notification =
        build(session).also { plain.notifyOrSkip(session.sessionId, it) }

    override fun cancel(sessionId: String) = plain.cancel(sessionId)
}
