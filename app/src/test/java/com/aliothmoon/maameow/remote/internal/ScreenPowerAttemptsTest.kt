package com.aliothmoon.maameow.remote.internal

import com.aliothmoon.maameow.remote.internal.ScreenPowerAttempts.SleepAction
import com.aliothmoon.maameow.remote.internal.ScreenPowerAttempts.WakeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenPowerAttemptsTest {

    @Test
    fun alreadyOnSkipsActions() {
        val performed = mutableListOf<WakeAction>()
        val ok = ScreenPowerAttempts.run(
            actions = ScreenPowerAttempts.wakeActions,
            alreadyDone = { true },
            perform = { performed += it },
            pollAfter = { error("should not poll") },
        )
        assertTrue(ok)
        assertTrue(performed.isEmpty())
    }

    @Test
    fun binderSuccessDoesNotFallBackToKeys() {
        val performed = mutableListOf<WakeAction>()
        var on = false
        val ok = ScreenPowerAttempts.run(
            actions = ScreenPowerAttempts.wakeActions,
            alreadyDone = { on },
            perform = {
                performed += it
                if (it == WakeAction.BINDER) on = true
            },
            pollAfter = { on },
        )
        assertTrue(ok)
        assertEquals(listOf(WakeAction.BINDER), performed)
    }

    @Test
    fun clientSideBinderFailureThenKeyWakeupSucceeds() {
        val performed = mutableListOf<WakeAction>()
        var on = false
        val ok = ScreenPowerAttempts.run(
            actions = ScreenPowerAttempts.wakeActions,
            alreadyDone = { on },
            perform = {
                performed += it
                if (it == WakeAction.KEY_WAKEUP) on = true
            },
            pollAfter = { on },
        )
        assertTrue(ok)
        assertEquals(listOf(WakeAction.BINDER, WakeAction.KEY_WAKEUP), performed)
    }

    @Test
    fun allWakeAttemptsFail() {
        val performed = mutableListOf<WakeAction>()
        val ok = ScreenPowerAttempts.run(
            actions = ScreenPowerAttempts.wakeActions,
            alreadyDone = { false },
            perform = { performed += it },
            pollAfter = { false },
        )
        assertFalse(ok)
        assertEquals(ScreenPowerAttempts.wakeActions, performed)
    }

    @Test
    fun sleepFallsBackToKeysThenSucceeds() {
        val performed = mutableListOf<SleepAction>()
        var off = false
        val ok = ScreenPowerAttempts.run(
            actions = ScreenPowerAttempts.sleepActions,
            alreadyDone = { off },
            perform = {
                performed += it
                if (it == SleepAction.KEY_SLEEP) off = true
            },
            pollAfter = { off },
        )
        assertTrue(ok)
        assertEquals(listOf(SleepAction.BINDER, SleepAction.KEY_SLEEP), performed)
    }
}
