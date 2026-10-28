package com.aliothmoon.maameow.presentation.components

import android.os.SystemClock
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest

internal val LocalPagerPreloadState = staticCompositionLocalOf<PagerPreloadState?> { null }

/** 同窗分页错开预加载，避免同帧集中组合 */
internal class PagerPreloadState(private val clock: () -> Long = SystemClock::uptimeMillis) {
    private val activity = MutableStateFlow(0L)
    private var touching = false
    private var lastInteraction = clock()
    private var lastPreloadFrame = Long.MIN_VALUE

    fun onPointerInput(pressed: Boolean) {
        touching = pressed
        onScroll()
    }

    fun onScroll() {
        lastInteraction = clock()
        activity.value++
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun awaitIdle(scrolling: Flow<Boolean>) {
        combine(scrolling, activity) { moving, _ -> moving || touching }
            .transformLatest { busy ->
                if (!busy) {
                    val wait = IDLE_DELAY_MS - (clock() - lastInteraction)
                    if (wait > 0) delay(wait)
                    emit(Unit)
                }
            }.first()
    }

    fun tryPreload(frameTimeNanos: Long): Boolean {
        if (touching || clock() - lastInteraction < IDLE_DELAY_MS || lastPreloadFrame == frameTimeNanos) {
            return false
        }
        lastPreloadFrame = frameTimeNanos
        return true
    }

    private companion object {
        const val IDLE_DELAY_MS = 150L
    }
}
