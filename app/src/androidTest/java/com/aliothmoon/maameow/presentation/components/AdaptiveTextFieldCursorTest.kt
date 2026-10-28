package com.aliothmoon.maameow.presentation.components

import android.widget.EditText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aliothmoon.maameow.presentation.LocalFloatingWindowContext
import com.aliothmoon.maameow.presentation.ProvideInputFocusManager
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveTextFieldCursorTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun ordinaryTextFieldPreservesMiddleEditingWithoutReportingSelectionChanges() {
        var value by mutableStateOf("abcd")
        var changes = 0
        composeRule.setContent {
            MaterialTheme {
                ProvideInputFocusManager {
                    ITextField(value, { value = it; changes++ })
                }
            }
        }
        val editor = composeRule.onNode(hasSetTextAction())
        editor.performClick().performTextInputSelection(TextRange(1))
        composeRule.runOnIdle { assertEquals(0, changes) }
        editor.performTextInput("x")
        editor.assertTextEquals("axbcd")
        assertEquals(TextRange(2), editor.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        composeRule.runOnIdle { assertEquals(1, changes) }
    }

    @Test
    fun nativeReplacementMovesToEndOnceAndHandlesSameTextAgain() {
        var value by mutableStateOf("")
        var request by mutableIntStateOf(0)
        composeRule.setContent {
            MaterialTheme {
                ProvideInputFocusManager {
                    CompositionLocalProvider(LocalFloatingWindowContext provides true) {
                        Box(Modifier.size(300.dp, 100.dp)) {
                            ITextField(value, { value = it }, cursorToEndRequest = request)
                        }
                    }
                }
            }
        }
        composeRule.runOnIdle { value = "丰川祥子"; request++ }
        onView(isAssignableFrom(EditText::class.java)).check { view, error ->
            if (error != null) throw error
            val editor = view as EditText
            assertEquals(4, editor.selectionStart)
            editor.setSelection(1)
            editor.text.insert(1, "x")
            assertEquals(2, editor.selectionStart)
        }
        composeRule.runOnIdle { assertEquals("丰x川祥子", value) }
        onView(isAssignableFrom(EditText::class.java)).check { view, _ ->
            assertEquals(2, (view as EditText).selectionStart)
            view.setSelection(0)
        }
        composeRule.runOnIdle { request++ }
        onView(isAssignableFrom(EditText::class.java)).check { view, _ ->
            assertEquals(5, (view as EditText).selectionStart)
        }
        composeRule.runOnIdle { value = "" }
        onView(isAssignableFrom(EditText::class.java)).check { view, _ ->
            assertEquals(0, (view as EditText).selectionStart)
        }
    }

    @Test
    fun nativeEditorTreatsInitialRequestAsAlreadyApplied() {
        var request by mutableIntStateOf(3)
        composeRule.setContent {
            MaterialTheme {
                ProvideInputFocusManager {
                    CompositionLocalProvider(LocalFloatingWindowContext provides true) {
                        Box(Modifier.size(300.dp, 100.dp)) {
                            ITextField("abcd", {}, cursorToEndRequest = request)
                        }
                    }
                }
            }
        }
        lateinit var editor: EditText
        onView(isAssignableFrom(EditText::class.java)).check { view, error ->
            if (error != null) throw error
            editor = view as EditText
        }
        composeRule.runOnIdle { assertEquals(0, editor.selectionStart) }
        composeRule.runOnIdle { request++ }
        composeRule.runOnIdle { assertEquals(4, editor.selectionStart) }
    }
}
