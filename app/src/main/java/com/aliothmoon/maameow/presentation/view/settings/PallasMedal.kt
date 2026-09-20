package com.aliothmoon.maameow.presentation.view.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.aliothmoon.maameow.presentation.LocalPallasCheers
import kotlin.math.min

private val PallasGold = Color(0xFFFFD700)

@Composable
fun PallasMedal(
    debugActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    medalSize: Dp = 128.dp,
) {
    val normalColor = MaterialTheme.colorScheme.onSurfaceVariant
    val medalColor = if (debugActive) PallasGold else normalColor
    val path = remember {
        runCatching {
            PathParser.createPathFromPathData(HANGOVER_PATH_DATA).asComposePath()
        }.getOrElse { Path() }
    }

    // 对齐 WPF 勋章上的 FloatingBlock：飘字交给根部的全局层，不受这里的布局边界约束
    val cheers = LocalPallasCheers.current
    var center by remember { mutableStateOf(Offset.Unspecified) }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
            .size(medalSize)
            .clip(CircleShape)
            .border(1.5.dp, medalColor, CircleShape)
            .onGloballyPositioned {
                center = it.positionInRoot() +
                    Offset(it.size.width / 2f, it.size.height / 2f)
            }
            .clickable(role = Role.Button) {
                if (center.isSpecified) cheers?.cheer(center)
                onClick()
            },
    ) {
        // 略减内边距，让矢量在圆内更满
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bounds = path.getBounds()
                if (bounds.width <= 0f || bounds.height <= 0f) return@Canvas
                val canvasW = this.size.width
                val canvasH = this.size.height
                val scale = min(canvasW / bounds.width, canvasH / bounds.height)
                val dx = (canvasW - bounds.width * scale) / 2f - bounds.left * scale
                val dy = (canvasH - bounds.height * scale) / 2f - bounds.top * scale
                withTransform({
                    translate(dx, dy)
                    scale(scale, scale, pivot = Offset.Zero)
                }) {
                    drawPath(path = path, color = medalColor)
                }
            }
        }
    }
}
