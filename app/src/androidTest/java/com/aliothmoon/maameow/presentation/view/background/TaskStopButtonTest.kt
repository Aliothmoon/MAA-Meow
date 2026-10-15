package com.aliothmoon.maameow.presentation.view.background

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskStopButtonTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun onlyRunningCanStopAndBoundsStayStable() {
        val state = mutableStateOf(MaaExecutionState.IDLE)
        var stops = 0
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.task_btn_stop)
        composeRule.setContent {
            MaterialTheme {
                TaskStopButton(state.value, { stops++ }, Modifier.width(180.dp))
            }
        }
        val button = composeRule.onNodeWithText(label)
        val bounds = button.fetchSemanticsNode().boundsInRoot
        for (current in MaaExecutionState.entries) {
            composeRule.runOnIdle { state.value = current }
            if (current == MaaExecutionState.RUNNING) {
                button.assertIsEnabled().performClick()
            } else {
                button.assertIsNotEnabled().performClick()
            }
            assertEquals(bounds, button.fetchSemanticsNode().boundsInRoot)
        }
        composeRule.runOnIdle { assertEquals(1, stops) }
    }
}
