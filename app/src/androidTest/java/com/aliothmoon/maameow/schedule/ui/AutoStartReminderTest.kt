package com.aliothmoon.maameow.schedule.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aliothmoon.maameow.schedule.service.AutoStartTarget
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoStartReminderTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun hiddenPageDoesNotLoadOrConsumeReminder() {
        val active = mutableStateOf(false)
        var loads = 0
        var shown = 0
        composeRule.setContent {
            val target = rememberAutoStartReminder(
                active = active.value,
                enabled = true,
                load = { loads++; AutoStartTarget.AppDetails },
                onShown = { shown++ },
            )
            if (active.value && target.value != null) Text("reminder")
        }
        composeRule.runOnIdle {
            assertEquals(0, loads)
            assertEquals(0, shown)
            active.value = true
        }
        composeRule.onNodeWithText("reminder").assertExists()
        composeRule.runOnIdle { assertEquals(1, shown); active.value = false }
        composeRule.runOnIdle { active.value = true }
        composeRule.runOnIdle { assertEquals(1, shown) }
    }

    @Test
    fun leavingWhileLoadingDoesNotConsumeReminder() {
        val active = mutableStateOf(true)
        val pending = CompletableDeferred<AutoStartTarget>()
        var shown = 0
        composeRule.setContent {
            val target = rememberAutoStartReminder(
                active = active.value,
                enabled = true,
                load = { pending.await() },
                onShown = { shown++ },
            )
            if (active.value && target.value != null) Text("reminder")
        }
        composeRule.runOnIdle { active.value = false }
        composeRule.runOnIdle { pending.complete(AutoStartTarget.AppDetails) }
        composeRule.runOnIdle { assertEquals(0, shown); active.value = true }
        composeRule.onNodeWithText("reminder").assertExists()
        composeRule.runOnIdle { assertEquals(1, shown) }
    }
}
