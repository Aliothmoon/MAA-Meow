package com.aliothmoon.maameow.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

// 传 State 避免页码变化重组整页
val LocalPageVisible = staticCompositionLocalOf<State<Boolean>> { AlwaysVisible }

private val AlwaysVisible: State<Boolean> = derivedStateOf { true }

@Composable
fun rememberPageVisible(pagerState: PagerState, page: Int): State<Boolean> {
    val parent = LocalPageVisible.current
    return remember(pagerState, page, parent) {
        derivedStateOf { parent.value && (page == pagerState.currentPage || page == pagerState.targetPage) }
    }
}

/** 避免常驻的离屏页抢返回键 */
@Composable
fun PageBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val visible by LocalPageVisible.current
    BackHandler(enabled = enabled && visible, onBack = onBack)
}

/** 离屏暂停订阅，保留最后一次值 */
@Composable
fun <T> StateFlow<T>.collectWhilePageVisible(): State<T> {
    val visible by LocalPageVisible.current
    val source = remember(this, visible) { if (visible) this else emptyFlow() }
    val initial = remember { value }
    return source.collectAsStateWithLifecycle(initial)
}

private const val STAGE_START_DELAY_MS = 400L

/** 分帧预加载，避免首屏集中组合 */
@Composable
fun rememberStagedBeyondViewportCount(pagerState: PagerState, max: Int): Int {
    var count by remember { mutableIntStateOf(0) }
    val preload = LocalPagerPreloadState.current ?: remember { PagerPreloadState() }
    LaunchedEffect(pagerState, max, preload) {
        delay(STAGE_START_DELAY_MS)
        while (count < max) {
            preload.awaitIdle(snapshotFlow { pagerState.isScrollInProgress })
            withFrameNanos { frame ->
                if (!pagerState.isScrollInProgress && preload.tryPreload(frame)) count++
            }
        }
    }
    return count.coerceAtMost(max)
}
