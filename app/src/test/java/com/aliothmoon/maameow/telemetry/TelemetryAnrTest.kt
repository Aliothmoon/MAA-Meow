package com.aliothmoon.maameow.telemetry

import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryAnrTest {

    @Test
    fun `按系统记下的原因分类`() {
        assertEquals(
            "input",
            TelemetryAnr.kindOf(
                "Input dispatching timed out (8b7c1d0 com.aliothmoon.maameow/com.aliothmoon.maameow.MainActivity " +
                    "(server) is not responding. Waited 5001ms for MotionEvent)"
            ),
        )
        assertEquals(
            "service",
            TelemetryAnr.kindOf("executing service com.aliothmoon.maameow/.schedule.service.ScheduleExecutionService"),
        )
        assertEquals(
            "fgs_start",
            TelemetryAnr.kindOf(
                "Context.startForegroundService() did not then call Service.startForeground(): " +
                    "ServiceRecord{84b18c7 u0 com.aliothmoon.maameow/.domain.service.TaskExecutionService}"
            ),
        )
        assertEquals(
            "broadcast",
            TelemetryAnr.kindOf(
                "Broadcast of Intent { act=com.aliothmoon.maameow.SCHEDULE_TRIGGER flg=0x10000010 " +
                    "cmp=com.aliothmoon.maameow/.schedule.receiver.ScheduleReceiver (has extras) }"
            ),
        )
        assertEquals("provider", TelemetryAnr.kindOf("ContentProvider not responding"))
    }

    @Test
    fun `用户在弹窗里关掉的照样认出原因`() {
        assertEquals(
            "input",
            TelemetryAnr.kindOf("user request after error: Input dispatching timed out (Application does not have a focused window)"),
        )
    }

    @Test
    fun `没有原因或认不出的分开记`() {
        assertEquals("unknown", TelemetryAnr.kindOf(null))
        assertEquals("unknown", TelemetryAnr.kindOf(" "))
        assertEquals("other", TelemetryAnr.kindOf("user request after error"))
    }
}
