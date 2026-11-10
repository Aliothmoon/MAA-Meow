package com.aliothmoon.maameow.presentation.view.background

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.theme.MaaMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val PreviewHandleHeight = 20.dp

/** 展开时预览占页面高度的比例，同改版前 3:7 */
private const val EXPANDED_RATIO = 0.3f

/** 松手速度超过它按方向定，否则看停在哪半边 */
private const val FLING_VELOCITY = 600f

/** 预览收起 / 展开：1 展开、0 收起，拖动把手时跟手 */
@Stable
class PreviewCollapseState(private val scope: CoroutineScope) {
    val fraction = Animatable(1f)

    var expanded by mutableStateOf(true)
        private set

    /** 展开与收起的高度差，布局时更新 */
    internal var rangePx = 1f

    fun drag(deltaPx: Float) {
        scope.launch { fraction.snapTo((fraction.value + deltaPx / rangePx).coerceIn(0f, 1f)) }
    }

    fun settle(velocity: Float, reduceMotion: Boolean) {
        val target = when {
            velocity > FLING_VELOCITY -> true
            velocity < -FLING_VELOCITY -> false
            else -> fraction.value > 0.5f
        }
        animateTo(target, reduceMotion)
    }

    fun toggle(reduceMotion: Boolean) = animateTo(!expanded, reduceMotion)

    private var restoreOnRelease = false

    /** 临时收起，释放时恢复原先的展开状态 */
    fun holdCollapsed(hold: Boolean, reduceMotion: Boolean) {
        if (hold) {
            restoreOnRelease = expanded
            if (expanded) animateTo(false, reduceMotion)
        } else if (restoreOnRelease) {
            restoreOnRelease = false
            if (!expanded) animateTo(true, reduceMotion)
        }
    }

    private fun animateTo(target: Boolean, reduceMotion: Boolean) {
        expanded = target
        scope.launch { fraction.animateTo(if (target) 1f else 0f, MaaMotion.spec(reduceMotion)) }
    }
}

/** 预览区高度只在布局阶段读 fraction，拖动不重组整页；须是 Column 首个子项，拿到的才是整列可用高度 */
fun Modifier.previewHeight(state: PreviewCollapseState): Modifier = layout { measurable, constraints ->
    val collapsed = PreviewCollapsedHeight.toPx()
    val expanded = ((constraints.maxHeight - PreviewHandleHeight.toPx()) * EXPANDED_RATIO).coerceAtLeast(collapsed)
    state.rangePx = (expanded - collapsed).coerceAtLeast(1f)
    val height = (collapsed + (expanded - collapsed) * state.fraction.value).roundToInt()
    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
    layout(placeable.width, height) { placeable.place(0, 0) }
}

/**
 * 预览没停在展开位时，按收起后的最大高度测量、超出部分裁掉
 * 缩放过程中只读位置不重测，免得保留的几个分页每帧整体重排
 */
fun Modifier.holdHeightWhilePreviewMoves(state: PreviewCollapseState): Modifier =
    clipToBounds().layout { measurable, constraints ->
        val fraction = state.fraction.value
        val height = if (fraction >= 1f || !constraints.hasBoundedHeight) {
            constraints.maxHeight
        } else {
            constraints.maxHeight + (state.rangePx * fraction).roundToInt()
        }
        val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
        layout(placeable.width, constraints.maxHeight) { placeable.place(0, 0) }
    }

/** 上下拖动收起 / 展开；点击不受影响，移动超过阈值才算拖 */
@Composable
fun Modifier.previewDrag(state: PreviewCollapseState, reduceMotion: Boolean): Modifier = draggable(
    state = rememberDraggableState { state.drag(it) },
    orientation = Orientation.Vertical,
    onDragStopped = { velocity -> state.settle(velocity, reduceMotion) },
)

/** 预览与面板之间的把手：箭头指向可收的方向，拖动时跟着转 */
@Composable
fun PreviewHandle(state: PreviewCollapseState, reduceMotion: Boolean) {
    val label = stringResource(if (state.expanded) R.string.bg_preview_collapse else R.string.bg_preview_expand)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PreviewHandleHeight)
            .previewDrag(state, reduceMotion)
            .clickable(
                interactionSource = null,
                indication = null,
                onClickLabel = label,
            ) { state.toggle(reduceMotion) },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = null,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .size(16.dp)
                    .graphicsLayer { rotationZ = 180f * (1f - state.fraction.value) },
            )
        }
    }
}
