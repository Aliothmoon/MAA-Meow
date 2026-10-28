package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.domain.notification.LiveRunGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskExecutionServiceRestartTest {

    @Test
    fun freshProcessTokenIsSystemRestart() {
        assertTrue(TaskExecutionService.isSystemRestart(LiveRunGate().current()))
    }

    @Test
    fun tokenAfterBeginRunIsNotSystemRestart() {
        val gate = LiveRunGate()
        val runToken = gate.beginRun()
        assertFalse(TaskExecutionService.isSystemRestart(runToken))
        assertFalse(TaskExecutionService.isSystemRestart(gate.current()))
    }
}
