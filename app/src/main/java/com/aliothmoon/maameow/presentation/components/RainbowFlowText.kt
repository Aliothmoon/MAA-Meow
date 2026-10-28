package com.aliothmoon.maameow.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.currentCoroutineContext

// 渐变跨度略大于常见标题宽，配合 phase 形成流光
private const val SPAN = 320f
private const val FLOW_DURATION_MS = 4_000

/**
 * 炫彩文案
 */
@Composable
fun RainbowFlowText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = FontWeight.ExtraBold,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val pageVisible = LocalPageVisible.current
    val inViewport = remember { mutableStateOf(false) }
    val phase = remember { Animatable(0f) }

    // 可见性只在协程里读，离屏时停住
    LaunchedEffect(pageVisible) {
        runRainbowFlow(phase) { pageVisible.value && inViewport.value }
    }

    Text(
        text = text,
        modifier = modifier
            .onGloballyPositioned { inViewport.value = !it.boundsInWindow().isEmpty }
            // SrcIn 需要独立图层
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithCache {
                val brush = Brush.linearGradient(
                    colorStops = RainbowStops,
                    start = Offset.Zero,
                    end = Offset(SPAN, 0f),
                    tileMode = TileMode.Repeated,
                )
                onDrawWithContent {
                    drawContent()
                    // phase 只在绘制阶段读，每帧只重绘
                    val shift = phase.value * SPAN * 2f
                    translate(left = shift) {
                        drawRect(
                            brush = brush,
                            topLeft = Offset(-shift, 0f),
                            size = size,
                            blendMode = BlendMode.SrcIn,
                        )
                    }
                }
            },
        color = Color.White,
        style = style,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
    )
}

internal suspend fun runRainbowFlow(
    phase: Animatable<Float, AnimationVector1D>,
    isVisible: () -> Boolean,
) {
    val durationScale = currentCoroutineContext()[MotionDurationScale]
    // 首读会写 state 起监听，不能放进 snapshotFlow 的只读快照
    durationScale?.scaleFactor
    snapshotFlow { isVisible() && (durationScale?.scaleFactor ?: 1f) > 0f }
        .collectLatest { active ->
            if (!active) return@collectLatest
            while (true) {
                val remaining = ((1f - phase.value) * FLOW_DURATION_MS).toInt()
                phase.animateTo(1f, tween(remaining, easing = LinearEasing))
                phase.snapTo(0f)
            }
        }
}

private val RainbowStops: Array<Pair<Float, Color>> = arrayOf(
    0.000f to Color.Red,
    0.143f to Color(0xFFFFA500), // Orange
    0.286f to Color(0xFFFFD700), // Gold
    0.429f to Color(0xFF008000), // Green
    0.571f to Color(0xFF1E90FF), // DodgerBlue
    0.714f to Color(0xFF8A2BE2), // BlueViolet
    0.857f to Color.Magenta,
    1.000f to Color.Red,
)
