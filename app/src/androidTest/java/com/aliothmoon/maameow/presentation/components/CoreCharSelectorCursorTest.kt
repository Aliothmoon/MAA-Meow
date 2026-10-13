package com.aliothmoon.maameow.presentation.components

import android.content.ContextWrapper
import android.widget.EditText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.presentation.LocalFloatingWindowContext
import com.aliothmoon.maameow.presentation.ProvideInputFocusManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File

@RunWith(AndroidJUnit4::class)
class CoreCharSelectorCursorTest {
    @get:Rule val composeRule = createComposeRule()
    @get:Rule val files = TemporaryFolder(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val editor get() = composeRule.onNode(hasSetTextAction())

    @Test
    fun selectingCandidatePlacesCursorAtEnd() {
        showSelector()
        choose("丰川祥子")
        assertEditor("丰川祥子", 4)
    }

    @Test
    fun selectingSameCandidateAgainPlacesCursorAtEnd() {
        showSelector()
        choose("丰川祥子")
        editor.performTextInputSelection(TextRange(1))

        choose("丰川祥子")

        assertEditor("丰川祥子", 4)
    }

    @Test
    fun middleEditingAndClearKeepTheirCursorPositions() {
        showSelector()
        choose("丰川祥子")
        editor.performTextInputSelection(TextRange(1))
        editor.performTextInput("x")
        assertEditor("丰x川祥子", 2)

        composeRule.onNodeWithContentDescription(context.getString(R.string.common_clear)).performClick()
        assertEditor("", 0)
        composeRule.onNodeWithText("W").performClick()
        assertEditor("W", 1)
    }

    @Test
    fun floatingWindowCandidateKeepsFocusAndPlacesCursorAtEnd() {
        showSelector(floating = true)
        // ExtractModeEditText 只报 1px 可见区，Espresso click 不可用
        withNativeEditor { it.performClick() }
        withNativeEditor { assertTrue(it.hasFocus()) }

        choose("丰川祥子")
        assertNativeEditor("丰川祥子", 4)
        withNativeEditor { it.setSelection(1) }

        choose("丰川祥子")
        assertNativeEditor("丰川祥子", 4)
    }

    private fun showSelector(floating: Boolean = false) {
        val isolatedContext = object : ContextWrapper(context) {
            override fun getExternalFilesDir(type: String?): File = files.root
        }
        val paths = MaaPathConfig(isolatedContext, GlobalContext.get().get())
        File(paths.resourceDir, "roguelike/cursor-test/recruitment.json").apply {
            parentFile!!.mkdirs()
            writeText("""{"priority":[{"opers":[{"name":"丰川祥子","is_start":true},{"name":"W","is_start":true}]}]}""")
        }
        val resources = ResourceDataManager(paths)
        runBlocking { resources.load() }
        var value by mutableStateOf("")
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalFloatingWindowContext provides floating) {
                    ProvideInputFocusManager {
                        Box(Modifier.size(360.dp, 500.dp).clearFocusOnBlankTap()) {
                            CoreCharSelector(value, { value = it }, "cursor-test", resources)
                        }
                    }
                }
            }
        }
        if (!floating) editor.performClick()
    }

    // 断言放 Espresso 外，失败截图会拖崩进程
    private fun withNativeEditor(block: (EditText) -> Unit) {
        lateinit var editor: EditText
        onView(isAssignableFrom(EditText::class.java)).check { view, error ->
            if (error != null) throw error
            editor = view as EditText
        }
        composeRule.runOnIdle { block(editor) }
    }

    private fun assertNativeEditor(text: String, cursor: Int) = withNativeEditor {
        assertTrue(it.hasFocus())
        assertEquals(text, it.text.toString())
        assertEquals(cursor, it.selectionStart)
    }

    private fun choose(name: String) {
        composeRule.onNodeWithContentDescription(context.getString(R.string.core_char_selector_recommended))
            .performClick()
        composeRule.onNode(hasText(name) and !hasSetTextAction(), useUnmergedTree = true).performClick()
    }

    private fun assertEditor(text: String, cursor: Int) {
        editor.assertIsFocused()
        val semantics = editor.fetchSemanticsNode().config
        assertEquals(text, semantics[SemanticsProperties.EditableText].text)
        assertEquals(TextRange(cursor), semantics[SemanticsProperties.TextSelectionRange])
    }
}
