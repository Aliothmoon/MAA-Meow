package com.aliothmoon.maameow.telemetry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryFgsTest {

    @Test
    fun `只认任务服务的前台超时`() {
        assertTrue(
            TelemetryFgs.isTaskServiceTimeout(
                "Context.startForegroundService() did not then call Service.startForeground(): " +
                    "ServiceRecord{ca7301f u0 com.aliothmoon.maameow/.domain.service.TaskExecutionService c:com.aliothmoon.maameow}"
            )
        )
        assertFalse(
            TelemetryFgs.isTaskServiceTimeout(
                "Context.startForegroundService() did not then call Service.startForeground(): " +
                    "ServiceRecord{18b964b u0 com.aliothmoon.maameow/.schedule.service.ScheduleExecutionService}"
            )
        )
        assertFalse(TelemetryFgs.isTaskServiceTimeout("TaskExecutionService crashed"))
        assertFalse(TelemetryFgs.isTaskServiceTimeout(null))
    }
}
