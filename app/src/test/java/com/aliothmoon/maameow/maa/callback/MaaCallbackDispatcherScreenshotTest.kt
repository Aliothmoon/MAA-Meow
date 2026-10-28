package com.aliothmoon.maameow.maa.callback

import com.aliothmoon.maameow.domain.models.NotificationImage
import com.aliothmoon.maameow.domain.service.MaaNotificationCenter
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.maa.AsstMsg
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test

class MaaCallbackDispatcherScreenshotTest {

    private val stateHolder: MaaExecutionStateHolder = mockk(relaxed = true)
    private val taskChainHandler: TaskChainHandler = mockk(relaxed = true)
    private val notificationCenter: MaaNotificationCenter = mockk(relaxed = true)
    private val screenshot = NotificationImage(byteArrayOf(1, 2, 3))

    private val dispatcher = MaaCallbackDispatcher(
        sessionLogger = mockk(relaxed = true),
        stateHolder = stateHolder,
        connectionInfoHandler = mockk(relaxed = true),
        taskChainHandler = taskChainHandler,
        subTaskHandler = mockk(relaxed = true),
        notificationCenter = notificationCenter,
        gameDataReporter = mockk(relaxed = true),
        telemetry = mockk(relaxed = true),
        setParamsErrors = SetParamsErrorSignal(),
    )

    private fun completeWhile(state: MaaExecutionState, captured: NotificationImage?) {
        every { stateHolder.currentRunState() } returns state
        every { notificationCenter.captureCompletionScreenshot() } returns captured
        dispatcher.onEvent(AsstMsg.AllTasksCompleted.value, "{}")
    }

    @Test
    fun capturesBeforeReportingIdle() {
        completeWhile(MaaExecutionState.RUNNING, screenshot)
        // IDLE 会触发关游戏、息屏，截图必须在它之前
        verifyOrder {
            notificationCenter.captureCompletionScreenshot()
            stateHolder.reportRunState(MaaExecutionState.IDLE)
            taskChainHandler.onAllTasksCompleted(asStopped = false, screenshot = screenshot)
        }
    }

    @Test
    fun captureFailureStillCompletesWithTextOnly() {
        completeWhile(MaaExecutionState.RUNNING, captured = null)
        verify { taskChainHandler.onAllTasksCompleted(asStopped = false, screenshot = null) }
    }

    @Test
    fun skipsCaptureWhenStopping() {
        completeWhile(MaaExecutionState.STOPPING, screenshot)
        verify(exactly = 0) { notificationCenter.captureCompletionScreenshot() }
        verify { taskChainHandler.onAllTasksCompleted(asStopped = true, screenshot = null) }
    }
}
