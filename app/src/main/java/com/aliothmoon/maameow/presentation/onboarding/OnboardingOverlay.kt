package com.aliothmoon.maameow.presentation.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.presentation.components.DialogIconBadge
import com.aliothmoon.maameow.presentation.components.consumeAllPointerEvents
import com.aliothmoon.maameow.theme.LocalReduceMotion
import com.aliothmoon.maameow.theme.OpaqueTheme
import kotlinx.coroutines.flow.collectLatest

/** 比普通弹窗遮罩更深，聚光灯要靠明暗对比 */
private const val ScrimAlpha = 0.72f
private val HolePadding = 6.dp
private val HoleCorner = 16.dp
private val CardGap = 12.dp
private val CardMargin = 16.dp
private val CardMaxWidth = 400.dp

/** 聚光灯覆盖层：遮罩挖洞 + 讲解卡片，吞掉全部指针、只有卡片按钮可点 */
@Composable
fun OnboardingOverlay(state: OnboardingState, modifier: Modifier = Modifier) {
    if (!state.active) return
    val step = state.currentStep
    val reduceMotion = LocalReduceMotion.current
    val density = LocalDensity.current

    BackHandler {
        if (state.stepIndex > 0) state.previous() else state.finish()
    }

    // 靶点上报的是 root 坐标，换算到覆盖层自身坐标
    var origin by remember { mutableStateOf(Offset.Zero) }
    val holePadding = with(density) { HolePadding.toPx() }

    // 靶点随滚动 / 切页每帧在动：snapshotFlow 派生免整层重组，spring 保留速度不跳变
    val animatedHole = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var holeShown by remember { mutableStateOf(false) }
    LaunchedEffect(state, reduceMotion, holePadding) {
        snapshotFlow {
            state.currentStep.target
                ?.let { state.bounds[it] }
                ?.translate(-origin)
                ?.inflate(holePadding)
        }.collectLatest { hole ->
            if (hole == null) {
                holeShown = false
                return@collectLatest
            }
            if (!holeShown || reduceMotion) {
                animatedHole.snapTo(hole)
                holeShown = true
            } else {
                animatedHole.animateTo(hole, spring(stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = ScrimAlpha)
    val holeCorner = with(density) { HoleCorner.toPx() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .consumeAllPointerEvents()
            // Clear 混合需要离屏图层，否则挖出来的是黑洞而不是透明
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                drawRect(scrimColor)
                if (holeShown) {
                    val rect = animatedHole.value
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        cornerRadius = CornerRadius(holeCorner),
                        blendMode = BlendMode.Clear,
                    )
                }
            },
    ) {
        val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
        val layoutDirection = LocalLayoutDirection.current
        Layout(
            content = { OnboardingCard(state = state, step = step) },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val margin = CardMargin.roundToPx()
            val gap = CardGap.roundToPx()
            val area = IntRect(
                left = safeInsets.calculateLeftPadding(layoutDirection).roundToPx() + margin,
                top = safeInsets.calculateTopPadding().roundToPx() + margin,
                right = constraints.maxWidth -
                    safeInsets.calculateRightPadding(layoutDirection).roundToPx() - margin,
                bottom = constraints.maxHeight -
                    safeInsets.calculateBottomPadding().roundToPx() - margin,
            )
            val cardWidth = minOf(area.width, CardMaxWidth.roundToPx()).coerceAtLeast(0)
            val placeable = measurables.first().measure(
                Constraints(maxWidth = cardWidth, maxHeight = area.height.coerceAtLeast(0)),
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                // 洞的动画值只在摆放阶段读，弹簧每帧只重摆不重测
                val offset = OnboardingPlacement.resolve(
                    area = area,
                    hole = if (holeShown) animatedHole.value else null,
                    cardWidth = placeable.width,
                    cardHeight = placeable.height,
                    gap = gap,
                )
                placeable.place(offset)
            }
        }
    }
}

@Composable
private fun OnboardingCard(state: OnboardingState, step: OnboardingStep) {
    val isLast = state.stepIndex == state.steps.lastIndex
    val bodyScroll = rememberScrollState()
    LaunchedEffect(step) { bodyScroll.scrollTo(0) }

    // 遮罩之上的卡片必须不透明，自定义背景的玻璃配色会透出底下的黑
    OpaqueTheme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DialogIconBadge(icon = step.icon)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(step.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(
                                R.string.onboarding_step_counter,
                                state.stepIndex + 1,
                                state.steps.size,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 横屏 / 大字号下正文可滚，按钮永远留在卡片里
                Text(
                    text = stringResource(step.bodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(bodyScroll),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!isLast) {
                        TextButton(onClick = { state.finish() }) {
                            Text(stringResource(R.string.onboarding_skip))
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (state.stepIndex > 0) {
                        TextButton(onClick = { state.previous() }) {
                            Text(stringResource(R.string.onboarding_previous))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Button(
                        onClick = { state.next() },
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(
                            stringResource(
                                if (isLast) R.string.onboarding_done else R.string.onboarding_next
                            )
                        )
                    }
                }
            }
        }
    }
}
