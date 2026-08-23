package com.aliothmoon.maameow.domain.notification

import android.app.Notification
import android.os.Handler
import android.os.Looper
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.AppSettingsManager.EventNotificationLevel

class LiveSessionCoordinator(
    private val publisher: LiveUpdatePublisher,
    private val appSettings: AppSettingsManager,
) {
    private val gate = LiveRunGate()
    private val slot = LiveSessionSlot()
    private val handler = Handler(Looper.getMainLooper())
    private val resultTimeouts = mutableMapOf<String, Runnable>()
    private val lock = Any()

    val capability: LiveCapability
        get() = publisher.capability

    val progressNotifyId: Int
        get() = LiveNotifyIds.PROGRESS

    fun beginRun(): Long = synchronized(lock) {
        cancelAllResultTimeoutsLocked()
        slot.endProgress()
        slot.clearResult()
        publisher.cancel(LiveNotifyIds.PROGRESS_SESSION)
        publisher.cancel(LiveNotifyIds.RESULT_SESSION)
        gate.beginRun()
    }

    fun currentToken(): Long = gate.current()

    /** 进入 STARTING 时调用，避开 FGS 那条主线程路径；调用方须保证不在主线程 */
    fun prepareProgress(runToken: Long) {
        if (!gate.isCurrent(runToken)) return
        publisher.prepareProgress()
    }

    fun isCurrent(runToken: Long): Boolean = gate.isCurrent(runToken)

    fun fallbackBuild(session: LiveSession): Notification = publisher.build(session)

    /** FGS 路径：单次构建，先 notify 再随 startForeground 复用同一实例 */
    fun postProgressForeground(runToken: Long, session: LiveSession): Notification? {
        synchronized(lock) {
            if (!gate.isCurrent(runToken)) return null
            val next = if (slot.progressActive) {
                slot.updateProgress(session.copy(firstFloat = false))
                    ?: slot.currentProgress()
                    ?: session
            } else {
                slot.beginProgress(session)
            }
            return publisher.publishForeground(next)
        }
    }

    fun publishProgress(runToken: Long, session: LiveSession) {
        synchronized(lock) {
            if (!gate.isCurrent(runToken)) return
            if (!slot.progressActive) {
                publisher.publish(slot.beginProgress(session))
                return
            }
            val next = slot.updateProgress(session) ?: return
            publisher.publish(next)
        }
    }

    fun cancelProgress(runToken: Long) {
        val pending: LiveSession?
        synchronized(lock) {
            if (!gate.isCurrent(runToken)) return
            publisher.cancel(LiveNotifyIds.PROGRESS_SESSION)
            pending = slot.endProgress()
        }
        if (pending != null) {
            publishResultNow(runToken, pending)
        }
    }

    fun publishResult(runToken: Long, session: LiveSession) {
        val styled = withAlertLevel(session) ?: return
        val ready: LiveSession?
        synchronized(lock) {
            if (!gate.tryClaimResult(runToken)) return
            ready = slot.offerResult(styled)
        }
        if (ready != null) {
            publishResultNow(runToken, ready)
        }
    }

    /** 独立事件（服务异常）：空闲期也可能发生，不能占用 per-run 的一次性结果闸门 */
    fun publishEvent(session: LiveSession) {
        val styled = withAlertLevel(session) ?: return
        val ready: LiveSession?
        synchronized(lock) {
            ready = slot.offerResult(styled)
        }
        if (ready != null) {
            publisher.publish(ready)
            scheduleResultTimeout(ready)
        }
    }

    /** OFF 直接不发；HIGH 走弹出通道，对齐 MaaEventNotifier 的分级 */
    private fun withAlertLevel(session: LiveSession): LiveSession? =
        when (appSettings.eventNotificationLevel.value) {
            EventNotificationLevel.OFF -> null
            EventNotificationLevel.HIGH -> session.copy(alert = true)
            EventNotificationLevel.DEFAULT -> session.copy(alert = false)
        }

    fun publishTest(title: String, text: String) {
        val session = LiveSession(
            sessionId = LiveNotifyIds.TEST_SESSION,
            category = LiveCategory.RESULT,
            title = title,
            text = text,
            capsuleText = title,
            ongoing = false,
            firstFloat = true,
            timeoutSec = 15,
        )
        publisher.publish(session)
        scheduleResultTimeout(session)
    }

    private fun publishResultNow(runToken: Long, session: LiveSession) {
        if (!gate.isCurrent(runToken)) return
        publisher.publish(session)
        scheduleResultTimeout(session)
    }

    private fun scheduleResultTimeout(session: LiveSession) {
        val timeoutSec = session.timeoutSec ?: return
        synchronized(lock) {
            cancelResultTimeoutLocked(session.sessionId)
            val runnable = Runnable {
                publisher.cancel(session.sessionId)
                synchronized(lock) {
                    resultTimeouts.remove(session.sessionId)
                    slot.clearResult()
                }
            }
            resultTimeouts[session.sessionId] = runnable
            handler.postDelayed(runnable, timeoutSec * 1000L)
        }
    }

    private fun cancelResultTimeoutLocked(sessionId: String) {
        resultTimeouts.remove(sessionId)?.let { handler.removeCallbacks(it) }
    }

    private fun cancelAllResultTimeoutsLocked() {
        resultTimeouts.values.forEach { handler.removeCallbacks(it) }
        resultTimeouts.clear()
    }
}
