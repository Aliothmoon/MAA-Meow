package com.aliothmoon.maameow.theme

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * App 动效语言：短、先快后稳、展开用纵向、换页用共享轴
 * 系统关闭动画时全部瞬间到位
 */
object MaaMotion {

    const val Fast = 160
    const val Medium = 220
    const val Page = 300

    val Emphasized: Easing = CubicBezierEasing(0.32f, 0.72f, 0.0f, 1.0f)
    val Linear: Easing = LinearEasing

    fun reduceMotion(context: android.content.Context): Boolean {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        return scale < 0.01f
    }

    fun duration(reduceMotion: Boolean, millis: Int): Int =
        if (reduceMotion) 0 else millis

    fun <T> spec(
        reduceMotion: Boolean,
        millis: Int = Medium,
        easing: Easing = Emphasized,
    ): FiniteAnimationSpec<T> {
        val d = duration(reduceMotion, millis)
        return if (d <= 0) snap() else tween(d, easing = easing)
    }

    fun pagerDuration(pageDistance: Int, reduceMotion: Boolean): Int {
        val distance = pageDistance.coerceAtLeast(1)
        return duration(reduceMotion, Medium + Fast * (distance - 1))
    }

    fun expandIn(reduceMotion: Boolean): EnterTransition {
        if (reduceMotion) return EnterTransition.None
        return fadeIn(spec(false, Fast, Linear)) +
                expandVertically(
                    animationSpec = spec(false, Medium),
                    expandFrom = Alignment.Top,
                )
    }

    fun expandOut(reduceMotion: Boolean): ExitTransition {
        if (reduceMotion) return ExitTransition.None
        return fadeOut(spec(false, Fast, Linear)) +
                shrinkVertically(
                    animationSpec = spec(false, Medium),
                    shrinkTowards = Alignment.Top,
                )
    }

    fun fadeIn(reduceMotion: Boolean): EnterTransition {
        if (reduceMotion) return EnterTransition.None
        return fadeIn(spec(false, Fast, Linear))
    }

    fun fadeOut(reduceMotion: Boolean): ExitTransition {
        if (reduceMotion) return ExitTransition.None
        return fadeOut(spec(false, Fast, Linear))
    }

    fun dialogIn(reduceMotion: Boolean): EnterTransition {
        if (reduceMotion) return EnterTransition.None
        return fadeIn(spec(false, Fast, Linear)) +
                scaleIn(initialScale = 0.94f, animationSpec = spec(false, Medium))
    }

    fun dialogOut(reduceMotion: Boolean): ExitTransition {
        if (reduceMotion) return ExitTransition.None
        return fadeOut(spec(false, Fast, Linear)) +
                scaleOut(targetScale = 0.94f, animationSpec = spec(false, Fast))
    }

    fun pageEnter(forward: Boolean, reduceMotion: Boolean): EnterTransition {
        if (reduceMotion) return EnterTransition.None
        val from = if (forward) { w: Int -> w } else { w: Int -> -w / 2 }
        return slideInHorizontally(spec(false, Page), from) +
                fadeIn(spec(false, Page, Linear))
    }

    fun pageExit(forward: Boolean, reduceMotion: Boolean): ExitTransition {
        if (reduceMotion) return ExitTransition.None
        val to = if (forward) { w: Int -> -w / 2 } else { w: Int -> w }
        return slideOutHorizontally(spec(false, Page), to) +
                fadeOut(spec(false, Page, Linear))
    }
}

val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { MaaMotion.reduceMotion(context) }
}

@Composable
fun ProvideMaaMotion(content: @Composable () -> Unit) {
    val reduce = rememberReduceMotion()
    CompositionLocalProvider(LocalReduceMotion provides reduce, content = content)
}

@Composable
fun MaaAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition? = null,
    exit: ExitTransition? = null,
    label: String = "MaaAnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val reduce = LocalReduceMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter ?: MaaMotion.expandIn(reduce),
        exit = exit ?: MaaMotion.expandOut(reduce),
        label = label,
        content = content,
    )
}
