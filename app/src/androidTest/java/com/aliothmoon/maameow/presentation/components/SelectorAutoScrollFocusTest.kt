package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aliothmoon.maameow.data.resource.StageGroup
import com.aliothmoon.maameow.data.resource.StageItem
import com.aliothmoon.maameow.presentation.ProvideInputFocusManager
import com.aliothmoon.maameow.presentation.search.ProvideSettingSearch
import com.aliothmoon.maameow.presentation.search.SettingSearchIndex
import com.aliothmoon.maameow.presentation.search.SettingSearchNavigator
import com.aliothmoon.maameow.presentation.search.SettingSearchTarget
import com.aliothmoon.maameow.presentation.view.panel.common.GroupedStageButtonGroup
import com.aliothmoon.maameow.presentation.view.panel.common.ItemButtonGroup
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectorAutoScrollFocusTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun expandingStageSelectorKeepsEditorFocus() {
        val stages = listOf(StageGroup("group", List(20) { StageItem("STAGE-$it", "Stage $it") }))
        val scroll = showSelector {
            GroupedStageButtonGroup("Stages", "", stages, onItemSelected = {})
        }

        composeRule.onNodeWithText("Stages").performClick()

        assertScrolledWithFocus(scroll)
    }

    @Test
    fun expandingItemSelectorKeepsEditorFocus() {
        val scroll = showSelector {
            ItemButtonGroup(
                label = "Materials",
                selectedValue = "",
                items = List(20) { "item-$it" },
                onItemSelected = {},
            )
        }

        composeRule.onNodeWithText("Materials").performClick()

        assertScrolledWithFocus(scroll)
    }

    @Test
    fun settingSearchTargetRelocationKeepsEditorFocus() {
        val navigator = SettingSearchNavigator()
        val entry = SettingSearchIndex.entries.first()
        val scroll = showSelector {
            ProvideSettingSearch(navigator = navigator) {
                SettingSearchTarget(entry.anchorRes) {
                    Text("Search target", Modifier.height(80.dp))
                }
            }
        }

        composeRule.runOnIdle { navigator.request(entry) }

        assertScrolledWithFocus(scroll)
    }

    private fun showSelector(content: @Composable () -> Unit): ScrollState {
        lateinit var scroll: ScrollState
        composeRule.setContent {
            MaterialTheme {
                ProvideInputFocusManager {
                    scroll = rememberScrollState()
                    Column(Modifier.size(300.dp, 360.dp).clearFocusOnBlankTap()) {
                        BasicTextField(
                            value = "draft",
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth().height(56.dp).testTag("editor"),
                        )
                        Column(Modifier.weight(1f).verticalScroll(scroll)) {
                            Spacer(Modifier.height(240.dp))
                            content()
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag("editor").performTouchInput { click() }
        composeRule.onNodeWithTag("editor").assertIsFocused()
        return scroll
    }

    private fun assertScrolledWithFocus(scroll: ScrollState) {
        composeRule.waitUntil(timeoutMillis = 5_000) { scroll.value > 0 }
        composeRule.mainClock.advanceTimeBy(1_600)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("editor").assertIsFocused()
    }
}
