package com.aliothmoon.maameow.data.notification.live

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.notification.LiveBackend
import com.aliothmoon.maameow.domain.notification.LiveCapability
import com.aliothmoon.maameow.domain.notification.LiveSession
import com.aliothmoon.maameow.domain.notification.LiveUpdatePublisher

class LivePublisherRouter(
    context: Context,
    factory: LiveNotificationFactory,
    sequenceStore: FocusSequenceStore,
    xmsfGate: XmsfNetworkGate,
    private val appSettings: AppSettingsManager,
    private val hyperDetector: HyperOsFocusDetector,
    private val promotedDetector: AospPromotedDetector,
) : LiveUpdatePublisher {

    private val appContext = context.applicationContext
    private val hyper = HyperOsFocusPublisher(
        appContext, factory, sequenceStore, xmsfGate, appSettings, promotedDetector,
    )
    private val aosp = AospPromotedPublisher(appContext, factory, promotedDetector)
    private val plain = PlainNotificationPublisher(appContext, factory, promotedDetector)

    override val capability: LiveCapability
        get() = snapshot()

    override fun build(session: LiveSession): Notification = current().build(session)

    override fun publish(session: LiveSession) = current().publish(session)

    override fun prepareProgress() = current().prepareProgress()

    override fun publishForeground(session: LiveSession): Notification =
        current().publishForeground(session)

    override fun cancel(sessionId: String) {
        hyper.cancel(sessionId)
        aosp.cancel(sessionId)
        plain.cancel(sessionId)
    }

    override fun refreshCapability(): LiveCapability {
        hyperDetector.invalidate()
        return snapshot()
    }

    fun snapshot(): LiveCapability {
        val post = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        val focusLikely = hyperDetector.isLikelyDevice()
        val focusGranted = hyperDetector.hasFocusPermission()
        val promoted = promotedDetector.isGranted()
        // 关掉旁路后岛会被云端鉴权摘掉，继续发焦点负载只是白构建，直接退到下一档
        val backend = when {
            hyperDetector.isAvailable() && appSettings.liveIslandXmsfBypass.value ->
                LiveBackend.HYPER_OS_FOCUS

            promoted -> LiveBackend.AOSP_PROMOTED
            else -> LiveBackend.PLAIN
        }
        return LiveCapability(
            backend = backend,
            postNotifications = post,
            promotedAvailable = promotedDetector.isApiSupported(),
            promotedGranted = promoted,
            focusLikely = focusLikely,
            focusGranted = focusGranted,
        )
    }

    private fun current(): LiveUpdatePublisher = when (snapshot().backend) {
        LiveBackend.HYPER_OS_FOCUS -> hyper
        LiveBackend.AOSP_PROMOTED -> aosp
        LiveBackend.PLAIN -> plain
    }
}
