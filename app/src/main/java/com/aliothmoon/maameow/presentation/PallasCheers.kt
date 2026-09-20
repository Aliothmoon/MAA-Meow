package com.aliothmoon.maameow.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.achievement.PallasSpeak
import com.aliothmoon.maameow.theme.LocalReduceMotion
import kotlin.math.roundToInt
import kotlin.random.Random

private const val MAX_CHEERS = 16
private const val CHEER_DURATION_MS = 900
private val CHEER_RISE = 80.dp
private val CHEER_DRIFT = 28.dp

data class PallasCheer(val id: Long, val text: String, val at: Offset, val drift: Float)

val LocalPallasCheers = compositionLocalOf<PallasCheerController?> { null }

class PallasCheerController {

    val cheers = mutableStateListOf<PallasCheer>()
    private var nextId = 0L

    fun cheer(at: Offset) {
        if (!at.isValid()) return
        if (cheers.size >= MAX_CHEERS) cheers.removeAt(0)
        cheers += PallasCheer(
            id = nextId++,
            text = PallasSpeak.random(1, 10),
            at = at,
            drift = Random.nextFloat() * 2f - 1f,
        )
    }

    fun forget(id: Long) {
        cheers.removeAll { it.id == id }
    }
}

fun Modifier.pallasCheerTaps(
    enabled: Boolean,
    controller: PallasCheerController,
): Modifier = if (!enabled) this else pointerInput(controller) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type != PointerEventType.Press) continue
            event.changes.firstOrNull()?.let { controller.cheer(it.position) }
        }
    }
}

@Composable
fun PallasCheerLayer(controller: PallasCheerController) {
    Box(modifier = Modifier.fillMaxSize()) {
        controller.cheers.forEach { cheer ->
            key(cheer.id) {
                PallasCheerGlyph(cheer) { controller.forget(cheer.id) }
            }
        }
    }
}

@Composable
private fun PallasCheerGlyph(cheer: PallasCheer, onFinished: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(cheer.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(CHEER_DURATION_MS, easing = LinearEasing),
        )
        onFinished()
    }

    Text(
        text = cheer.text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .offset { IntOffset(cheer.at.x.roundToInt(), cheer.at.y.roundToInt()) }
            .wrapContentSize(unbounded = true)
            .graphicsLayer {
                val p = progress.value
                alpha = 1f - p * p
                translationX = -size.width / 2f
                translationY = -size.height / 2f
                if (!reduceMotion) {
                    translationY -= CHEER_RISE.toPx() * p
                    translationX += CHEER_DRIFT.toPx() * cheer.drift * p
                }
            },
    )
}
