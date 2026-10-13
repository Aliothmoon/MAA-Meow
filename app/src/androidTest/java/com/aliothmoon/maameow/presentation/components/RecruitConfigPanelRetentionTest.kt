package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.RecruitConfig
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.presentation.ProvideInputFocusManager
import com.aliothmoon.maameow.presentation.view.panel.RecruitConfigPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class RecruitConfigPanelRetentionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var config by mutableStateOf(RecruitConfig())
    private lateinit var tags: Map<String, Pair<String, String>>

    @Before
    fun showPanel() {
        val resources = GlobalContext.get().get<ResourceDataManager>()
        runBlocking(Dispatchers.IO) { resources.load("Official", "zh-cn") }
        tags = resources.recruitTags.value
        check(tags.containsKey("近战位")) { "需先部署内置 MAA 资源" }
        composeRule.setContent {
            MaterialTheme {
                ProvideInputFocusManager {
                    Box(Modifier.size(380.dp, 600.dp).clearFocusOnBlankTap()) {
                        RecruitConfigPanel(config, onConfigChange = { config = it })
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
    }

    @Test
    fun changingTabCommitsNumericDraftAndClearsFocus() {
        composeRule.onNode(hasSetTextAction() and hasText("4"))
            .performClick().performTextReplacement("7")
        composeRule.runOnIdle { assertEquals(4, config.maxRecruitTimes) }

        tab(R.string.common_tab_advanced)

        composeRule.runOnIdle { assertEquals(7, config.maxRecruitTimes) }
        tab(R.string.common_tab_general)
        composeRule.onNode(hasSetTextAction() and hasText("7")).assertIsNotFocused()
    }

    @Test
    fun tagSelectionSurvivesLazyItemRecycling() {
        tab(R.string.common_tab_advanced)
        val (display, client) = tags.getValue("近战位")
        composeRule.onNodeWithText(display).performClick()
        composeRule.runOnIdle { assertEquals(listOf(client), config.autoRecruitFirstList) }
        val title = text(R.string.panel_recruit_level3_preference)
        val listId = composeRule.onNode(
            hasScrollToIndexAction() and hasAnyDescendant(hasText(title)) and
                    SemanticsMatcher("竖向列表") {
                        it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null
                    }
        ).fetchSemanticsNode().id
        val list = composeRule.onNode(SemanticsMatcher("公招高级列表") { it.id == listId })

        list.performScrollToIndex(8)
        list.performScrollToIndex(0)

        composeRule.onNodeWithText(display).assertIsSelected()
        composeRule.runOnIdle { assertEquals(listOf(client), config.autoRecruitFirstList) }
    }

    private fun tab(id: Int) {
        composeRule.onNodeWithText(text(id)).performTouchInput { click() }
        composeRule.waitForIdle()
    }

    private fun text(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
