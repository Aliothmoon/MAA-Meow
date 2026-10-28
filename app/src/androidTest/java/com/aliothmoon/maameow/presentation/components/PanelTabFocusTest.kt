package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aliothmoon.maameow.presentation.ProvideInputFocusManager
import com.aliothmoon.maameow.presentation.view.panel.PanelHeader
import com.aliothmoon.maameow.presentation.view.panel.PanelTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PanelTabFocusTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun taskTabClickCommitsRetainedNumericDraft() {
        var value by mutableIntStateOf(4)
        var selected by mutableStateOf(PanelTab.TASKS)
        composeRule.setContent {
            MaterialTheme {
                ProvideInputFocusManager {
                    Column(Modifier.size(380.dp, 600.dp).clearFocusOnBlankTap()) {
                        PanelHeader(selectedTab = selected, onTabSelected = { selected = it }, showActions = false)
                        INumericField(value, { value = it })
                    }
                }
            }
        }
        composeRule.onNode(hasSetTextAction() and hasText("4"))
            .performClick().performTextReplacement("7")
        composeRule.runOnIdle { assertEquals(4, value) }
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(PanelTab.LOG.labelRes)
        composeRule.onNodeWithText(label).performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(7, value); assertEquals(PanelTab.LOG, selected) }
        composeRule.onNode(hasSetTextAction() and hasText("7")).assertIsNotFocused()
    }
}
