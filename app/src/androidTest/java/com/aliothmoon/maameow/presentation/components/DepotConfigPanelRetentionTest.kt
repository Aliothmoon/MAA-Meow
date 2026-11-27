package com.aliothmoon.maameow.presentation.components

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
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.DepotMaintainConfig
import com.aliothmoon.maameow.data.model.DepotMaintainPlan
import com.aliothmoon.maameow.presentation.LocalFloatingWindowContext
import com.aliothmoon.maameow.presentation.ProvideInputFocusManager
import com.aliothmoon.maameow.presentation.view.panel.depot.DepotMaintainConfigPanel
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 悬浮窗仍是内联 PlanCard + 失焦提交的数字框，主界面改走抽屉后只剩这条路径 */
@RunWith(AndroidJUnit4::class)
class DepotConfigPanelRetentionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun floatingLongPlanListIsLazyAndScrollingCommitsNumericDraft() {
        var config by mutableStateOf(DepotMaintainConfig(
            plans = List(30) { DepotMaintainPlan(stage = "PLAN-$it", dropCount = it + 1) },
        ))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val lastTitle = "PLAN-29 · ${context.getString(R.string.panel_depot_not_selected)} x30"
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalFloatingWindowContext provides true) {
                    ProvideInputFocusManager {
                        Box(Modifier.size(380.dp, 600.dp).clearFocusOnBlankTap()) {
                            DepotMaintainConfigPanel(config, onConfigChange = { config = it })
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithContentDescription(lastTitle).assertDoesNotExist()
        val listId = composeRule.onNode(
            hasScrollToIndexAction() and
                    hasAnyDescendant(hasText(context.getString(R.string.panel_depot_summary_title))) and
                    SemanticsMatcher("竖向列表") {
                        it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null
                    }
        ).fetchSemanticsNode().id
        val list = composeRule.onNode(SemanticsMatcher("库存计划列表") { it.id == listId })
        // 概览 + 操作区 + 30 条计划，末条在 31
        list.performScrollToIndex(31)
        composeRule.onNodeWithContentDescription(lastTitle).performClick()
        composeRule.waitForIdle()
        // 悬浮窗走原生 ExtractModeEditText，只报 1px 可见区，需直接操作 EditText
        withNativeEditor("30") { editor ->
            editor.performClick()
            assertTrue(editor.hasFocus())
            editor.setText("31")
        }
        composeRule.runOnIdle { assertEquals(30, config.plans.last().dropCount) }
        list.performTouchInput { swipeDown(durationMillis = 500) }
        composeRule.runOnIdle { assertEquals(31, config.plans.last().dropCount) }
    }

    // 断言放 Espresso 外，失败截图会拖崩进程
    private fun withNativeEditor(text: String, block: (EditText) -> Unit) {
        lateinit var editor: EditText
        onView(allOf(isAssignableFrom(EditText::class.java), withText(text))).check { view, error ->
            if (error != null) throw error
            editor = view as EditText
        }
        composeRule.runOnIdle { block(editor) }
    }
}
