package com.aliothmoon.maameow.schedule

import com.aliothmoon.maameow.schedule.model.ExecutionFixMapping
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.model.ScheduleFixAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExecutionFixMappingTest {

    @Test
    fun `skipped locked maps to unlock credential settings`() {
        assertEquals(
            ScheduleFixAction.UNLOCK_CREDENTIAL,
            ExecutionFixMapping.fixActionFor(ExecutionResult.SKIPPED_LOCKED),
        )
    }

    @Test
    fun `ui launch and start failures map to backend readiness`() {
        assertEquals(
            ScheduleFixAction.BACKEND_READY,
            ExecutionFixMapping.fixActionFor(ExecutionResult.FAILED_UI_LAUNCH),
        )
        assertEquals(
            ScheduleFixAction.BACKEND_READY,
            ExecutionFixMapping.fixActionFor(ExecutionResult.FAILED_START),
        )
    }

    @Test
    fun `other results have no fix entry`() {
        assertNull(ExecutionFixMapping.fixActionFor(ExecutionResult.STARTED))
        assertNull(ExecutionFixMapping.fixActionFor(ExecutionResult.FAILED_VALIDATION))
        assertNull(ExecutionFixMapping.fixActionFor(ExecutionResult.SKIPPED_BUSY))
        assertNull(ExecutionFixMapping.fixActionFor(ExecutionResult.CANCELLED))
    }
}
