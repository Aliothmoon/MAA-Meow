package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Dp

/** 底栏隐藏时仍保留尺寸，避免常驻页重测 */
@Composable
internal fun BottomBarOverlay(
    showBottomBar: Boolean,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit,
    content: @Composable BoxScope.(Dp) -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val bar = subcompose("bar", bottomBar).map {
            it.measure(constraints.copy(minHeight = 0))
        }
        val barHeight = bar.maxOfOrNull { it.height } ?: 0
        val bottomPadding = barHeight.toDp()
        val body = subcompose("body") {
            Box(Modifier.fillMaxSize()) { content(bottomPadding) }
        }.single().measure(constraints)
        layout(body.width, body.height) {
            body.placeRelative(0, 0)
            if (showBottomBar) {
                bar.forEach { it.placeRelative(0, body.height - it.height) }
            }
        }
    }
}
