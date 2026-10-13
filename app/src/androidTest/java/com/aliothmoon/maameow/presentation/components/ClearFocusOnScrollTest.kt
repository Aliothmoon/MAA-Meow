package com.aliothmoon.maameow.presentation.components

import android.widget.EditText
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aliothmoon.maameow.presentation.InputFocusManager
import com.aliothmoon.maameow.presentation.LocalInputFocusManager
import com.aliothmoon.maameow.presentation.ProvideInputFocusManager
import com.aliothmoon.maameow.presentation.view.panel.common.bringIntoViewOnExpand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClearFocusOnScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clearsOncePerDragAndResetsForNextGesture() {
        val focus = CountingFocusManager()
        val listState = showList(focus)

        composeRule.onNodeWithTag("list").performTouchInput { swipeUp(durationMillis = 500) }
        composeRule.runOnIdle {
            assertTrue(listState.firstVisibleItemIndex > 0)
            assertEquals(1, focus.clearCount)
        }

        composeRule.onNodeWithTag("list").performTouchInput { swipeUp(durationMillis = 500) }
        composeRule.runOnIdle { assertEquals(2, focus.clearCount) }
    }

    @Test
    fun quickFlickReleasedBeforeDragDispatchStillClears() {
        val focus = CountingFocusManager()
        val listState = showList(focus)

        composeRule.onNodeWithTag("list").performTouchInput {
            down(center)
            moveBy(Offset(0f, -viewConfiguration.touchSlop * 5))
            up()
        }

        composeRule.runOnIdle {
            assertTrue(listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
            assertEquals(1, focus.clearCount)
        }
    }

    @Test
    fun programmaticScrollDoesNotClearFocus() {
        val focus = CountingFocusManager()
        showList(focus)

        composeRule.onNodeWithTag("list").performScrollToIndex(20)

        composeRule.runOnIdle { assertEquals(0, focus.clearCount) }
    }

    @Test
    fun blankTapsClearForEachGesture() {
        val focus = CountingFocusManager()
        showList(focus)

        repeat(2) {
            composeRule.onNodeWithTag("list").performTouchInput { click() }
            composeRule.runOnIdle { assertEquals(it + 1, focus.clearCount) }
        }
    }

    @Test
    fun composeEditorKeepsFocusOnProgrammaticScrollAndClearsOnDrag() {
        composeRule.setContent {
            ProvideInputFocusManager {
                Column(Modifier.size(300.dp, 400.dp).clearFocusOnBlankTap()) {
                    BasicTextField(
                        value = "text",
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("editor"),
                    )
                    LazyColumn(Modifier.weight(1f).testTag("list")) {
                        items(100) { Text("row-$it", Modifier.height(48.dp)) }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("editor").performTouchInput { click() }
        composeRule.onNodeWithTag("editor").assertIsFocused()
        composeRule.onNodeWithTag("list").performScrollToIndex(20)
        composeRule.onNodeWithTag("editor").assertIsFocused()
        composeRule.onNodeWithTag("list").performTouchInput { swipeUp(durationMillis = 500) }
        composeRule.onNodeWithTag("editor").assertIsNotFocused()
    }

    @Test
    fun bringIntoViewKeepsFocusUntilUserDrags() {
        assertFocusSurvivesBringIntoView(holdPointer = false)

        composeRule.onNodeWithTag("list").performTouchInput { swipeDown(durationMillis = 500) }
        composeRule.onNodeWithTag("editor").assertIsNotFocused()
    }

    @Test
    fun bringIntoViewKeepsFocusWhilePointerIsDown() {
        assertFocusSurvivesBringIntoView(holdPointer = true)

        composeRule.onNodeWithTag("list").performTouchInput { up() }
    }

    @Test
    fun expandingSelectorKeepsFocusThroughDelayedRelocation() {
        assertFocusSurvivesBringIntoView(holdPointer = false, useExpansionHelper = true)
    }

    @Test
    fun mouseWheelClearsFocus() {
        val focus = CountingFocusManager()
        val listState = showList(focus)

        composeRule.onNodeWithTag("list").performMouseInput { scroll(100f) }

        composeRule.runOnIdle {
            assertTrue(listState.firstVisibleItemScrollOffset > 0 || listState.firstVisibleItemIndex > 0)
            assertEquals(1, focus.clearCount)
        }
    }

    @Test
    fun nativeEditorKeepsFocusInsideAndClearsBeforeOutsideButtonClick() {
        lateinit var editor: EditText
        composeRule.setContent {
            ProvideInputFocusManager {
                Column(Modifier.size(300.dp, 400.dp).clearFocusOnBlankTap()) {
                    AndroidView(
                        factory = { context ->
                            EditText(context).apply {
                                editor = this
                                setText("text")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("editor"),
                    )
                    Button(onClick = {}, modifier = Modifier.testTag("outside")) { Text("outside") }
                }
            }
        }

        composeRule.onNodeWithTag("editor").performTouchInput { click() }
        composeRule.runOnIdle { assertTrue(editor.hasFocus()) }
        composeRule.onNodeWithTag("editor").performTouchInput { click() }
        composeRule.runOnIdle { assertTrue(editor.hasFocus()) }
        composeRule.onNodeWithTag("outside").performTouchInput { click() }
        composeRule.runOnIdle { assertFalse(editor.hasFocus()) }
    }

    @Test
    fun nativeEditorKeepsFocusOnlyWithinItsInputGroup() {
        lateinit var editor: EditText
        composeRule.setContent {
            ProvideInputFocusManager {
                Column(Modifier.size(300.dp, 400.dp).clearFocusOnBlankTap()) {
                    Column(Modifier.inputFocusGroup()) {
                        AndroidView(
                            factory = { context -> EditText(context).also { editor = it } },
                            modifier = Modifier.fillMaxWidth().height(56.dp).testTag("editor"),
                        )
                        Button(onClick = {}, modifier = Modifier.testTag("sameGroup")) { Text("same") }
                    }
                    Button(onClick = {}, modifier = Modifier.inputFocusGroup().testTag("otherGroup")) {
                        Text("other")
                    }
                }
            }
        }

        composeRule.onNodeWithTag("editor").performTouchInput { click() }
        composeRule.runOnIdle { assertTrue(editor.hasFocus()) }
        composeRule.onNodeWithTag("sameGroup").performTouchInput { click() }
        composeRule.runOnIdle { assertTrue(editor.hasFocus()) }
        composeRule.onNodeWithTag("otherGroup").performTouchInput { click() }
        composeRule.runOnIdle { assertFalse(editor.hasFocus()) }
    }

    private fun assertFocusSurvivesBringIntoView(
        holdPointer: Boolean,
        useExpansionHelper: Boolean = false,
    ) {
        var expanded by mutableStateOf(false)
        var relocated by mutableStateOf(false)
        lateinit var scroll: ScrollState
        composeRule.setContent {
            ProvideInputFocusManager {
                val requester = remember { BringIntoViewRequester() }
                scroll = rememberScrollState()
                LaunchedEffect(expanded) {
                    if (expanded && !useExpansionHelper) {
                        withFrameNanos { }
                        withFrameNanos { }
                        requester.bringIntoView()
                        relocated = true
                    }
                }
                Box(Modifier.size(300.dp, 260.dp).clearFocusOnBlankTap()) {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("list")) {
                        Spacer(Modifier.height(140.dp))
                        Column(
                            if (useExpansionHelper) Modifier.bringIntoViewOnExpand(expanded)
                            else Modifier.bringIntoViewRequester(requester)
                        ) {
                            BasicTextField(
                                value = "text",
                                onValueChange = {},
                                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("editor"),
                            )
                            if (expanded) Spacer(Modifier.height(140.dp))
                        }
                        Spacer(Modifier.height(100.dp))
                    }
                }
            }
        }

        composeRule.onNodeWithTag("editor").performTouchInput { click() }
        composeRule.onNodeWithTag("editor").assertIsFocused()
        if (holdPointer) {
            composeRule.onNodeWithTag("list").performTouchInput { down(Offset(4f, 4f)) }
        }
        composeRule.runOnIdle { expanded = true }
        if (useExpansionHelper) {
            composeRule.waitUntil(timeoutMillis = 5_000) { scroll.value > 0 }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.waitForIdle()
        } else {
            composeRule.waitUntil(timeoutMillis = 5_000) { relocated }
        }

        composeRule.runOnIdle { assertTrue(scroll.value > 0) }
        composeRule.onNodeWithTag("editor").assertIsFocused()
    }

    private fun showList(focus: InputFocusManager): LazyListState {
        lateinit var listState: LazyListState
        composeRule.setContent {
            listState = rememberLazyListState()
            CompositionLocalProvider(LocalInputFocusManager provides focus) {
                Box(Modifier.size(300.dp, 400.dp).clearFocusOnBlankTap()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag("list"),
                    ) {
                        items(100) { Text("row-$it", modifier = Modifier.height(48.dp)) }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return listState
    }

    private class CountingFocusManager : InputFocusManager {
        var clearCount = 0
            private set

        override fun clear(force: Boolean) {
            clearCount++
        }
    }
}
