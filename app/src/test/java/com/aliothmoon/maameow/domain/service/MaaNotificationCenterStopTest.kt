package com.aliothmoon.maameow.domain.service

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.notification.LiveSession
import com.aliothmoon.maameow.domain.notification.LiveSessionCoordinator
import com.aliothmoon.maameow.domain.service.MaaCompositionService.StopOrigin
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Test

/** 掉线 / 游戏退出、到达时长上限都走停止流程，停止通知不能一律报手动停止 */
class MaaNotificationCenterStopTest {

    private val context = mockk<Context> {
        every { applicationContext } returns this
        every { getString(R.string.notification_event_task_stopped) } returns "stopped"
        every { getString(R.string.notification_event_task_stopped_text) } returns "manual"
        every { getString(R.string.notification_event_task_aborted_text) } returns "aborted"
        every { getString(R.string.notification_event_task_time_limit_text) } returns "limit"
    }
    private val live = mockk<LiveSessionCoordinator>(relaxed = true) {
        every { currentToken() } returns 1L
    }
    private val center = MaaNotificationCenter(
        context = context,
        eventNotifier = mockk(relaxed = true),
        externalService = mockk(relaxed = true),
        settings = mockk(relaxed = true),
        liveCoordinator = live,
        frameSnapshotter = mockk(relaxed = true),
    )

    private fun published(origin: StopOrigin): LiveSession {
        val session = slot<LiveSession>()
        every { live.publishResult(any(), capture(session)) } returns Unit
        center.notifyTaskStopped(origin)
        return session.captured
    }

    @Test
    fun userStop_saysManual() {
        val session = published(StopOrigin.USER)
        assertEquals("manual", session.text)
        assertEquals(15, session.timeoutSec)
    }

    @Test
    fun callbackStop_saysAborted() {
        val session = published(StopOrigin.CALLBACK)
        assertEquals("aborted", session.text)
        // 多半没人看着，留久一点
        assertEquals(120, session.timeoutSec)
    }

    @Test
    fun durationLimit_saysLimitReached() {
        assertEquals("limit", published(StopOrigin.RUN_DURATION_LIMIT).text)
    }
}
