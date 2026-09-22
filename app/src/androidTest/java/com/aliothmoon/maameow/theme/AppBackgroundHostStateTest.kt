package com.aliothmoon.maameow.theme

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 背景图开关会让 [AppBackgroundHost] 在「直接透传」与「玻璃配色 + 背景层」两个组合位置之间切换。
 *
 * 内容子树必须带着状态搬过去：它包着 MainScreen 的 pager 与整棵 NavHost，
 * 一旦被当成两处分别组合，切换瞬间所有 remember 都会丢（表现为 Tab 悄悄跳回首页）。
 */
@RunWith(AndroidJUnit4::class)
class AppBackgroundHostStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentKeepsStateAcrossBackgroundToggle() {
        var image by mutableStateOf<ImageBitmap?>(null)
        val parallax = mutableFloatStateOf(0f)
        // 从组合内取出 setter，用来改只存在于组合树里的状态
        var advance: (() -> Unit)? = null

        composeRule.setContent {
            AppBackgroundHost(
                image = image,
                imageAlpha = 1f,
                scrimAlpha = 0f,
                blurRadius = 0.dp,
                monetFromWallpaper = false,
                parallax = parallax,
            ) {
                // 代替 rememberPagerState：只活在组合树里，被拆掉就归零
                var page by remember { mutableIntStateOf(0) }
                advance = { page++ }
                Text("page=$page")
            }
        }

        composeRule.onNodeWithText("page=0").assertExists()
        composeRule.runOnIdle { advance!!() }
        composeRule.onNodeWithText("page=1").assertExists()

        // 开启背景：透传分支 → 玻璃背景分支
        composeRule.runOnIdle { image = ImageBitmap(1, 1) }
        composeRule.onNodeWithText("page=1").assertExists()

        // 关闭背景：玻璃背景分支 → 透传分支
        composeRule.runOnIdle { image = null }
        composeRule.onNodeWithText("page=1").assertExists()
    }
}
