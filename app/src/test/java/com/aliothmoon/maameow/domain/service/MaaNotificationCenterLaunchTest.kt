package com.aliothmoon.maameow.domain.service

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.domain.notification.LiveAction
import com.aliothmoon.maameow.domain.notification.LiveNotifyIds
import com.aliothmoon.maameow.domain.notification.LiveSession
import com.aliothmoon.maameow.domain.notification.LiveSessionCoordinator
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.utils.i18n.uiTextDynamic
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 定时 / 外部触发没跑起来时的通知：失败外推，跳过不外推，都不设超时 */
class MaaNotificationCenterLaunchTest {

    private val context = mockk<Context> {
        every { applicationContext } returns this
        every { getString(R.string.notification_schedule_failed) } returns "failed"
        every { getString(R.string.notification_schedule_skipped) } returns "skipped"
        every { getString(R.string.notification_schedule_detail, *anyVararg()) } answers {
            val args = secondArg<Array<Any?>>()
            "${args[0]}: ${args[1]}"
        }
    }
    private val external = mockk<ExternalNotificationService>(relaxed = true)
    private val settings = mockk<NotificationSettingsManager> {
        every { sendOnError } returns MutableStateFlow(true)
    }
    private val live = mockk<LiveSessionCoordinator>(relaxed = true)
    private val center = MaaNotificationCenter(
        context = context,
        eventNotifier = mockk(relaxed = true),
        externalService = external,
        settings = settings,
        liveCoordinator = live,
        frameSnapshotter = mockk(relaxed = true),
    )

    private fun published(result: ExecutionResult, reason: String): LiveSession {
        val session = slot<LiveSession>()
        every { live.publishStandalone(capture(session)) } returns Unit
        center.notifyLaunchNotStarted("Daily", result, uiTextDynamic(reason))
        return session.captured
    }

    @Test
    fun failure_notifiesAndPushes() {
        val session = published(ExecutionResult.FAILED_START, "Shizuku down")

        // 独立 ID：下一轮开跑、任务结果都不会把它顶掉
        assertEquals(LiveNotifyIds.LAUNCH_SESSION, session.sessionId)
        assertEquals("failed", session.title)
        assertEquals("Daily: Shizuku down", session.text)
        assertTrue(session.isError)
        // 凌晨失败到早上还得在
        assertNull(session.timeoutSec)
        verify(exactly = 1) { external.send("failed", "Daily: Shizuku down") }
    }

    @Test
    fun skip_notifiesWithoutPush() {
        assertEquals("skipped", published(ExecutionResult.SKIPPED_LOCKED, "locked").title)
        verify(exactly = 0) { external.send(any(), any()) }
    }

    @Test
    fun blacklistSkip_notifiesWithoutPush() {
        val session = published(ExecutionResult.SKIPPED_BLACKLIST, "game")
        assertEquals("skipped", session.title)
        assertFalse(session.isError)
        verify(exactly = 0) { external.send(any(), any()) }
    }

    @Test
    fun failure_respectsPushSwitch() {
        every { settings.sendOnError } returns MutableStateFlow(false)

        center.notifyLaunchNotStarted("Daily", ExecutionResult.FAILED_UI_LAUNCH, uiTextDynamic("x"))

        verify(exactly = 1) { live.publishStandalone(any()) }
        verify(exactly = 0) { external.send(any(), any()) }
    }

    @Test
    fun replacingStartFailure_withdrawsResultFirst() {
        center.notifyLaunchNotStarted(
            "Daily", ExecutionResult.FAILED_START, uiTextDynamic("x"), replacesStartFailure = true,
        )

        verifyOrder {
            live.withdrawResult()
            live.publishStandalone(any())
        }
    }

    // 调用方在流水线收尾，抛出去会把进程带崩
    @Test
    fun publishFailure_doesNotEscape() {
        every { live.publishStandalone(any()) } throws IllegalStateException("boom")

        center.notifyLaunchNotStarted("Daily", ExecutionResult.FAILED_START, uiTextDynamic("x"))
    }

    // 不外推：真到点失败时才推
    @Test
    fun shizukuDown_staysUntilWithdrawnAndOffersOpenShizuku() {
        every { context.getString(R.string.notification_shizuku_down_title) } returns "down"
        every { context.getString(R.string.notification_shizuku_down_after_boot) } returns "after boot"
        val session = slot<LiveSession>()
        every { live.publishStandalone(capture(session)) } returns Unit

        center.notifyShizukuDown(afterBoot = true)
        center.withdrawShizukuDown()

        assertEquals(LiveNotifyIds.SHIZUKU_DOWN_SESSION, session.captured.sessionId)
        assertEquals("after boot", session.captured.text)
        assertNull(session.captured.timeoutSec)
        assertEquals(listOf(LiveAction.OpenShizuku), session.captured.actions)
        verify(exactly = 0) { external.send(any(), any()) }
        verify { live.withdrawStandalone(LiveNotifyIds.SHIZUKU_DOWN_SESSION) }
    }
}
