package com.aliothmoon.maameow.presentation.view.background

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.presentation.state.PreviewTouchMarker

private val MarkerGreen = Color(0xFF81C784)
private val MarkerAmber = Color(0xFFFFD54F)
private val MarkerRed = Color(0xFFE57373)

@Composable
fun TouchPreviewOverlay(
    markers: List<PreviewTouchMarker>,
    displayResolution: DefaultDisplayConfig.Resolution,
    modifier: Modifier = Modifier,
) {
    val width = displayResolution.width
    val height = displayResolution.height
    Canvas(modifier = modifier) {
        val now = SystemClock.elapsedRealtime()
        val maxX = (width - 1).coerceAtLeast(1)
        val maxY = (height - 1).coerceAtLeast(1)

        markers.forEach { marker ->
            val age = (now - marker.createdAtMs).coerceAtLeast(0L)
            val progress = (age / PreviewTouchMarker.TTL_MS.toFloat()).coerceIn(0f, 1f)
            val alpha = (1f - progress).coerceIn(0f, 1f)
            if (alpha <= 0f) {
                return@forEach
            }

            val center = Offset(
                x = size.width * marker.x.coerceIn(0, maxX) / maxX.toFloat(),
                y = size.height * marker.y.coerceIn(0, maxY) / maxY.toFloat()
            )

            // 外环区分非首指
            if (marker.contact > 0) {
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.7f),
                    radius = 9.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            when (marker.action) {
                MotionEvent.ACTION_DOWN -> {
                    drawCircle(
                        color = MarkerGreen.copy(alpha = alpha * 0.3f),
                        radius = (8.dp.toPx() + (12.dp.toPx() * progress)),
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx() * alpha)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MarkerGreen.copy(alpha = alpha * 0.8f),
                                MarkerGreen.copy(alpha = 0f)
                            ),
                            center = center,
                            radius = 12.dp.toPx()
                        ),
                        radius = 12.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = alpha * 0.9f),
                        radius = 2.5.dp.toPx(),
                        center = center
                    )
                }

                MotionEvent.ACTION_MOVE -> {
                    drawCircle(
                        color = MarkerAmber.copy(alpha = alpha * 0.5f),
                        radius = 5.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = alpha * 0.4f),
                        radius = 1.5.dp.toPx(),
                        center = center
                    )
                }

                MotionEvent.ACTION_UP -> {
                    drawCircle(
                        color = MarkerRed.copy(alpha = alpha * 0.6f),
                        radius = (6.dp.toPx() + (18.dp.toPx() * progress)),
                        center = center,
                        style = Stroke(width = 2.dp.toPx() * alpha)
                    )
                    drawCircle(
                        color = MarkerRed.copy(alpha = alpha * 0.8f),
                        radius = 3.dp.toPx() * (1f - progress),
                        center = center
                    )
                }
            }
        }
    }
}
