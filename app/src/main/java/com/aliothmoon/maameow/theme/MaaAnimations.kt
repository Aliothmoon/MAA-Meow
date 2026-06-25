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

/**
 * Shared-axis style page transitions inspired by Material motion spec.
 * Uses spring-like cubic bezier for a subtle bounce feel.
 */
object MaaAnimations {

    private const val PAGE_DURATION = 380

    /**
     * Spring-like cubic bezier (0.32, 0.72, 0.0, 1.0) -- produces a subtle
     * overshoot and settle-back feel for smooth transitions.
     */
    val springEasing = CubicBezierEasing(0.32f, 0.72f, 0.0f, 1.0f)
    private val pageEasing = springEasing

    fun sharedAxisForwardEnter(): EnterTransition =
        slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(PAGE_DURATION, easing = pageEasing)
        ) + fadeIn(
            animationSpec = tween(PAGE_DURATION, easing = LinearEasing)
        )

    fun sharedAxisForwardExit(): ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { fullWidth -> -fullWidth / 2 },
            animationSpec = tween(PAGE_DURATION, easing = pageEasing)
        ) + fadeOut(
            animationSpec = tween(PAGE_DURATION, easing = LinearEasing)
        )

    fun sharedAxisPopEnter(): EnterTransition =
        slideInHorizontally(
            initialOffsetX = { fullWidth -> -fullWidth / 2 },
            animationSpec = tween(PAGE_DURATION, easing = pageEasing)
        ) + fadeIn(
            animationSpec = tween(PAGE_DURATION, easing = LinearEasing)
        )

    fun sharedAxisPopExit(): ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(PAGE_DURATION, easing = pageEasing)
        ) + fadeOut(
            animationSpec = tween(PAGE_DURATION, easing = LinearEasing)
        )
}