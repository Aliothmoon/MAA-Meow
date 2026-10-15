package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.presentation.components.BottomBarOverlay
import com.aliothmoon.maameow.presentation.components.LocalPageVisible
import com.aliothmoon.maameow.presentation.components.collectWhilePageVisible
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogPanelAutoScrollTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 会话日志满上限后从头丢旧条，条数不变，仍要跟到最新一条 */
    @Test
    fun keepsFollowingAfterLogCap() {
        val all = List(CAP + 50) { LogItem(content = "log-$it") }
        var logs by mutableStateOf(all.subList(0, CAP).toList())

        composeRule.setContent {
            LogPanel(logs = logs, onClearLogs = {})
        }
        composeRule.onNodeWithText("log-${CAP - 1}").assertIsDisplayed()

        composeRule.runOnIdle { logs = all.subList(50, CAP + 50).toList() }
        composeRule.onNodeWithText("log-${CAP + 49}").assertIsDisplayed()
    }

    @Test
    fun hiddenPageStopsCollecting() {
        val source = MutableStateFlow(0)
        val visible = mutableStateOf(false)

        composeRule.setContent {
            CompositionLocalProvider(LocalPageVisible provides visible) {
                val value by source.collectWhilePageVisible()
                Text("value=$value")
            }
        }
        composeRule.onNodeWithText("value=0").assertExists()

        composeRule.runOnIdle { source.value = 1 }
        composeRule.onNodeWithText("value=0").assertExists()

        composeRule.runOnIdle { visible.value = true }
        composeRule.onNodeWithText("value=1").assertExists()
    }

    /** 不追加日志也要在缩小视口后跟随；新增底栏不遮挡末行。 */
    @Test
    fun resizingWithBottomBarKeepsLatestLogVisibleAndFollowing() {
        val all = List(101) { LogItem(content = "resize-log-$it") }
        val logs = mutableStateOf(all.take(100))
        val height = mutableStateOf(460.dp)
        lateinit var listState: LazyListState
        lateinit var scope: CoroutineScope
        composeRule.setContent {
            listState = rememberLazyListState()
            scope = rememberCoroutineScope()
            BottomBarOverlay(
                showBottomBar = true,
                modifier = Modifier.width(320.dp).height(height.value),
                bottomBar = { Box(Modifier.fillMaxWidth().height(56.dp)) { Text("actions") } },
            ) { bottom ->
                LogPanel(
                    modifier = Modifier.fillMaxSize().padding(bottom = bottom),
                    logs = logs.value,
                    onClearLogs = {},
                    listState = listState,
                )
            }
        }
        composeRule.onNodeWithText("resize-log-99").assertIsDisplayed()
        // 模拟重新布局前保留了中间锚点；程序定位不关闭跟随。
        // 不追加日志，确保只有视口变化能使末行重新出现。
        composeRule.runOnIdle { scope.launch { listState.scrollToItem(50) } }
        composeRule.runOnIdle {
            assertEquals(50, listState.firstVisibleItemIndex)
            assertTrue(listState.canScrollForward)
        }
        composeRule.runOnIdle { height.value = 240.dp }
        composeRule.runOnIdle { assertFalse(listState.canScrollForward) }
        val latest = composeRule.onNodeWithText("resize-log-99").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val bar = composeRule.onNodeWithText("actions").fetchSemanticsNode().boundsInRoot
        assertTrue(latest.bottom <= bar.top)

        // 尺寸调整的程序性定位不能把自动跟随关掉。
        composeRule.runOnIdle { logs.value = all }
        composeRule.onNodeWithText("resize-log-100").assertIsDisplayed()
    }

    @Test
    fun manualReadingSurvivesResizeAndResumeButtonWorks() {
        val height = mutableStateOf(460.dp)
        val logs = mutableStateOf(List(100) { LogItem(content = "reading-log-$it") })
        composeRule.setContent {
            LogPanel(
                modifier = Modifier.width(320.dp).height(height.value),
                logs = logs.value,
                onClearLogs = {},
            )
        }
        val list = composeRule.onNode(hasScrollAction())
        list.performTouchInput { swipeDown(durationMillis = 1500) }
        composeRule.waitForIdle()
        val listNode = list.fetchSemanticsNode()
        val anchor = listNode.children.asSequence()
            .filter { it.boundsInRoot.bottom > listNode.boundsInRoot.top && it.config.contains(SemanticsProperties.Text) }
            .flatMap { it.config[SemanticsProperties.Text].asSequence() }
            .first { it.text.startsWith("reading-log-") }.text
        val before = composeRule.onNodeWithText(anchor).fetchSemanticsNode().boundsInRoot.top
        composeRule.runOnIdle {
            height.value = 240.dp
            logs.value = logs.value + LogItem(content = "reading-newest")
        }
        val after = composeRule.onNodeWithText(anchor).fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after, 1f)
        val resumeLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.panel_log_resume_auto_scroll)
        composeRule.onNodeWithContentDescription(resumeLabel).assertIsDisplayed().performClick()
        composeRule.onNodeWithText("reading-newest").assertIsDisplayed()
    }

    private companion object {
        const val CAP = 750
    }
}
