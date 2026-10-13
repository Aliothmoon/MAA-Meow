package com.aliothmoon.maameow.presentation.components

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageBackHandlerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hiddenPageLeavesBackToEarlierHandler() {
        val visible = mutableStateOf(true)
        var pageBacks = 0
        var fallbackBacks = 0
        // 先注册的优先级更低，代表主导航等外层处理
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    fallbackBacks++
                }
            })
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalPageVisible provides visible) {
                PageBackHandler { pageBacks++ }
            }
        }

        back()
        composeRule.runOnIdle {
            assertEquals(1, pageBacks)
            assertEquals(0, fallbackBacks)
        }

        visible.value = false
        composeRule.waitForIdle()
        back()
        composeRule.runOnIdle {
            assertEquals(1, pageBacks)
            assertEquals(1, fallbackBacks)
        }

        visible.value = true
        composeRule.waitForIdle()
        back()
        composeRule.runOnIdle { assertEquals(2, pageBacks) }
    }

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }
}
