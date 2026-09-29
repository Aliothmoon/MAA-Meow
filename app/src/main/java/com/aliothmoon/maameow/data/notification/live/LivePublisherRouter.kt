package com.aliothmoon.maameow.data.notification.live

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.notification.LiveBackend
import com.aliothmoon.maameow.domain.notification.LiveCapability
import com.aliothmoon.maameow.domain.notification.LiveSession
import com.aliothmoon.maameow.domain.notification.LiveUpdatePublisher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop

class LivePublisherRouter(
    context: Context,
    factory: LiveNotificationFactory,
    sequenceStore: FocusSequenceStore,
    xmsfGate: XmsfNetworkGate,
    private val appSettings: AppSettingsManager,
    private val hyperDetector: HyperOsFocusDetector,
    private val promotedDetector: AospPromotedDetector,
    style: LiveUpdateStyle,
    trackerIcons: TrackerIconStore,
) : LiveUpdatePublisher {

    private val appContext = context.applicationContext
    private val hyper = HyperOsFocusPublisher(
        appContext, factory, sequenceStore, xmsfGate, promotedDetector,
        style, trackerIcons,
    )
    private val aosp = AospPromotedPublisher(appContext, factory, promotedDetector)
    private val plain = PlainNotificationPublisher(appContext, factory, promotedDetector)

    @Volatile
    private var active: LiveUpdatePublisher? = null

    override val renderChanges: Flow<Unit> =
        combine(
            listOf(
                appSettings.liveBackendPreference,
                appSettings.liveUpdateChipContent,
                appSettings.liveUpdateColorScheme,
                appSettings.liveUpdateCustomColor,
                trackerIcons.icon,
            )
        ) { }.drop(1)

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
        // 从首选档往下取第一档可用的：超级岛 > 实时更新 > 普通通知
        val preferred = appSettings.liveBackendPreference.value
            ?: if (appSettings.liveIslandXmsfBypass.value) LiveBackend.HYPER_OS_FOCUS
            else LiveBackend.AOSP_PROMOTED
        val backend = when {
            preferred == LiveBackend.HYPER_OS_FOCUS && hyperDetector.isAvailable() ->
                LiveBackend.HYPER_OS_FOCUS

            preferred != LiveBackend.PLAIN && promoted -> LiveBackend.AOSP_PROMOTED
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

    private fun current(): LiveUpdatePublisher {
        val next = when (snapshot().backend) {
            LiveBackend.HYPER_OS_FOCUS -> hyper
            LiveBackend.AOSP_PROMOTED -> aosp
            LiveBackend.PLAIN -> plain
        }
        // 切走时让旧后端释放资源（岛的断网闸门），通知由新后端同 id 覆盖
        active?.takeIf { it !== next }?.onDeactivated()
        active = next
        return next
    }
}
