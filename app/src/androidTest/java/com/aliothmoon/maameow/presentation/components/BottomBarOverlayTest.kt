package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomBarOverlayTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun firstLayoutAlreadyReservesTheMeasuredBar() {
        val heights = mutableListOf<Int>()
        var expected = 0
        composeRule.setContent {
            expected = with(LocalDensity.current) { 260.dp.roundToPx() }
            BottomBarOverlay(
                showBottomBar = true,
                modifier = Modifier.size(300.dp),
                bottomBar = { Box(Modifier.fillMaxWidth().height(40.dp)) },
            ) { bottom ->
                Box(Modifier.fillMaxSize().padding(bottom = bottom)) {
                    Box(Modifier.fillMaxSize().onSizeChanged { heights.add(it.height) })
                }
            }
        }
        composeRule.runOnIdle { assertEquals(listOf(expected), heights.distinct()) }
    }

    @Test
    fun hidingTheBarPreservesRetainedPageBounds() {
        val shown = mutableStateOf(true)
        val heights = mutableListOf<Int>()
        composeRule.setContent {
            BottomBarOverlay(
                showBottomBar = shown.value,
                modifier = Modifier.size(300.dp),
                bottomBar = { Box(Modifier.fillMaxWidth().height(40.dp)) },
            ) { bottom ->
                Box(Modifier.fillMaxSize().padding(bottom = bottom)) {
                    Box(Modifier.fillMaxSize().onSizeChanged { heights.add(it.height) })
                }
            }
        }
        composeRule.runOnIdle { shown.value = false }
        composeRule.runOnIdle { shown.value = true }
        composeRule.runOnIdle { assertEquals(1, heights.distinct().size) }
    }
}
