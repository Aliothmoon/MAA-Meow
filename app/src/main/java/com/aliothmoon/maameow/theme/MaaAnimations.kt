package com.aliothmoon.maameow.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

object MaaAnimations {

    private const val PAGE_DURATION = 380

    /**
     * Ease-out cubic bezier (0.32, 0.72, 0.0, 1.0): fast start, smooth settle.
     * Control-point y stays within [0, 1], so it does not overshoot.
     */
    val springEasing = CubicBezierEasing(0.32f, 0.72f, 0.0f, 1.0f)

    private fun slideEnter(offsetX: (Int) -> Int): EnterTransition =
        slideInHorizontally(
            initialOffsetX = offsetX,
            animationSpec = tween(PAGE_DURATION, easing = springEasing)
        ) +
                fadeIn(animationSpec = tween(PAGE_DURATION, easing = LinearEasing))

    private fun slideExit(offsetX: (Int) -> Int): ExitTransition =
        slideOutHorizontally(
            targetOffsetX = offsetX,
            animationSpec = tween(PAGE_DURATION, easing = springEasing)
        ) +
                fadeOut(animationSpec = tween(PAGE_DURATION, easing = LinearEasing))

    val sharedAxisForwardEnter: EnterTransition = slideEnter { fullWidth -> fullWidth }
    val sharedAxisForwardExit: ExitTransition = slideExit { fullWidth -> -fullWidth / 2 }
    val sharedAxisPopEnter: EnterTransition = slideEnter { fullWidth -> -fullWidth / 2 }
    val sharedAxisPopExit: ExitTransition = slideExit { fullWidth -> fullWidth }
}
