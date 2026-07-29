package com.aliothmoon.maameow.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

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
    val transition = rememberInfiniteTransition(label = "rainbowFlow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rainbowPhase",
    )

    // 渐变跨度略大于常见标题宽，配合 phase 形成流光
    val span = 320f
    val startX = phase * span * 2f
    val brush = Brush.linearGradient(
        colorStops = RainbowStops,
        start = Offset(startX, 0f),
        end = Offset(startX + span, 0f),
        tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
    )

    Text(
        text = text,
        modifier = modifier,
        style = style.copy(brush = brush),
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
    )
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
