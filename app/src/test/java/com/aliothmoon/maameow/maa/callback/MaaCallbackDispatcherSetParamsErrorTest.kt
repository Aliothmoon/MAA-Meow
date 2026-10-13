package com.aliothmoon.maameow.maa.callback

import com.aliothmoon.maameow.maa.AsstMsg
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class MaaCallbackDispatcherSetParamsErrorTest {

    private val errors = SetParamsErrorSignal()
    private val subTaskHandler = mockk<SubTaskHandler>(relaxed = true)
    private val dispatcher = MaaCallbackDispatcher(
        sessionLogger = mockk(relaxed = true),
        stateHolder = mockk(relaxed = true),
        connectionInfoHandler = mockk(relaxed = true),
        taskChainHandler = mockk(relaxed = true),
        subTaskHandler = subTaskHandler,
        notificationCenter = mockk(relaxed = true),
        gameDataReporter = mockk(relaxed = true),
        telemetry = mockk(relaxed = true),
        setParamsErrors = errors,
    )

    @Test
    fun precheckErrorIsHandledBeforeSignalling() {
        val before = errors.current
        every { subTaskHandler.onSubTaskError(any()) } answers {
            assertEquals(before, errors.current)
        }

        dispatcher.onEvent(
            AsstMsg.SubTaskError.value,
            """{"taskid":0,"subtask":"CopilotTask","what":"CopilotFileReadError"}""",
        )

        assertEquals(before + 1, errors.current)
    }

    @Test
    fun runningTaskErrorsAndMissingTaskIdDoNotSignalPrecheckFailure() {
        val before = errors.current

        for (json in listOf("""{"taskid":1}""", "{}", null)) {
            dispatcher.onEvent(AsstMsg.SubTaskError.value, json)
        }

        assertEquals(before, errors.current)
    }
}
