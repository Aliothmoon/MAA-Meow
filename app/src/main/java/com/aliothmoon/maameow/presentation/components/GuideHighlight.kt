package com.aliothmoon.maameow.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 演示引导里可被描边的控件 */
enum class GuideAnchor {
    EDIT_TOGGLE,
    ADD_TASK,
    GALLERY_ITEM,
    NODE,
    STAGE_ITEM,
    WEEKLY_SWITCH,
    WEEKLY_DAY,
    THEME_ADD,
    THEME_FIELD,
}

/** 下一步该点的控件，key 区分同类：类型名 / 任务 id / 关卡代码 / 星期 / 候选序号 */
@Immutable
data class GuideHighlight(val anchor: GuideAnchor, val key: String? = null)

/** 仅演示提供，真实页面为 null */
val LocalGuideHighlight = compositionLocalOf<GuideHighlight?> { null }

@Immutable
data class GuideFrame(val rect: Rect, val corner: Dp)

/** 行位置供手势动画，描边目标供顶层画框 */
@Stable
class GuideDemoHooks {
    val nodeBounds = mutableStateMapOf<String, Rect>()
    val frames = mutableStateMapOf<Any, GuideFrame>()
}

val LocalGuideDemoHooks = staticCompositionLocalOf<GuideDemoHooks?> { null }

@Composable
fun guideHighlighted(anchor: GuideAnchor, key: String? = null): Boolean {
    val highlight = LocalGuideHighlight.current ?: return false
    return highlight.anchor == anchor && (key == null || highlight.key == key)
}

@Composable
fun Modifier.guideHighlight(anchor: GuideAnchor, key: String? = null, corner: Dp = 6.dp, insetTop: Dp = 0.dp): Modifier =
    guideHighlight(guideHighlighted(anchor, key), corner, insetTop)

/**
 * 登记描边目标，框由演示层在顶层统一画，不受祖先裁剪
 *
 * 挂在控件看得见的那层；[insetTop] 扣掉顶部不可见的留白
 */
@Composable
fun Modifier.guideHighlight(active: Boolean, corner: Dp = 6.dp, insetTop: Dp = 0.dp): Modifier {
    val hooks = LocalGuideDemoHooks.current
    if (!active || hooks == null) return this
    val token = remember { Any() }
    val inset = with(LocalDensity.current) { insetTop.toPx() }
    DisposableEffect(hooks, token) {
        onDispose { hooks.frames.remove(token) }
    }
    // boundsInRoot 已按祖先裁剪，滚出可视区的部分不画
    return onGloballyPositioned {
        val bounds = it.boundsInRoot()
        val frame = GuideFrame(bounds.copy(top = minOf(bounds.top + inset, bounds.bottom)), corner)
        if (hooks.frames[token] != frame) hooks.frames[token] = frame
    }
}

/** 同 M3 minimizedLabelHalfHeight：带 label 时外框比顶边低半个 label 行高 */
@Composable
fun outlinedFieldLabelInset(): Dp {
    val lineHeight = MaterialTheme.typography.bodySmall.lineHeight.takeIf { it.isSp } ?: 16.sp
    return with(LocalDensity.current) { lineHeight.toDp() / 2 }
}

/** 上报演示行位置，真实页面不挂监听 */
@Composable
fun Modifier.guideNodeBounds(nodeId: String): Modifier {
    val hooks = LocalGuideDemoHooks.current ?: return this
    DisposableEffect(hooks, nodeId) {
        onDispose { hooks.nodeBounds.remove(nodeId) }
    }
    return onGloballyPositioned {
        val bounds = it.boundsInRoot()
        if (hooks.nodeBounds[nodeId] != bounds) hooks.nodeBounds[nodeId] = bounds
    }
}

private val GuideGap = 3.dp
private val GuideStroke = 2.dp
private const val GUIDE_SPAN = 240f
private const val GUIDE_FLOW_MS = 2_000

@Composable
fun rememberGuidePhase(reduceMotion: Boolean): State<Float> {
    if (reduceMotion) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "guideFrame").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(GUIDE_FLOW_MS, easing = LinearEasing)),
        label = "guidePhase",
    )
}

/** 彩虹流光框，外扩固定间隙，圆角随之外扩 */
fun DrawScope.drawGuideFrame(target: Rect, corner: Dp, phase: Float) {
    if (target.width <= 0f || target.height <= 0f) return
    val stroke = GuideStroke.toPx()
    val pad = GuideGap.toPx() + stroke / 2
    val rect = target.inflate(pad)
    val shift = phase * GUIDE_SPAN * 2f
    val brush = Brush.linearGradient(
        colorStops = RainbowStops,
        start = Offset(rect.left + shift, rect.top),
        end = Offset(rect.left + shift + GUIDE_SPAN, rect.top + GUIDE_SPAN / 4f),
        tileMode = TileMode.Repeated,
    )
    val radius = CornerRadius(corner.toPx() + pad)
    drawRoundRect(brush, rect.topLeft, rect.size, radius, alpha = 0.2f, style = Stroke(stroke * 2.5f))
    drawRoundRect(brush, rect.topLeft, rect.size, radius, style = Stroke(stroke))
}
