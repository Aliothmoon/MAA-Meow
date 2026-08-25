package com.aliothmoon.maameow.presentation.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
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
import com.aliothmoon.maameow.presentation.navigation.BottomNavTab
import com.aliothmoon.maameow.theme.LocalReduceMotion
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility
import com.aliothmoon.maameow.theme.MaaMotion
import com.aliothmoon.maameow.theme.OpaqueTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 比普通弹窗遮罩更深，聚光灯要靠明暗对比 */
private const val ScrimAlpha = 0.72f
private const val StrokeAlpha = 0.6f
private val HolePadding = 6.dp
private val HoleCorner = 16.dp
private val HoleStroke = 1.5.dp

/** 聚焦从比靶点大一圈收拢进来，散焦往外松一点 */
private val FocusInflate = 24.dp
private val DefocusInflate = 12.dp

private val CardGap = 12.dp
private val CardMargin = 16.dp
private val CardMaxWidth = 400.dp

/** 靶点静止这么久才算落定，列表滚动中不聚焦 */
private const val SettleMillis = 80L
private const val SettleNanos = SettleMillis * 1_000_000

/** 靶点迟迟不出现（骨架屏等）时，卡片先居中亮出来 */
private const val MissingTargetGraceMillis = 150L

/** 洞与卡片共用：略硬、轻微过冲，跟随滚动不拖泥带水 */
private val SpotlightSpring = spring(
    dampingRatio = 0.85f,
    stiffness = 900f,
    visibilityThreshold = Rect(0.5f, 0.5f, 0.5f, 0.5f),
)

/**
 * 聚光灯覆盖层：遮罩挖洞 + 讲解卡片，吞掉全部指针、只有卡片按钮可点
 *
 * @param onRequestTab 切到某步所属的主 Tab；跨页由这里编排成"黑场 → 切页 → 揭示"，调用方应瞬时切
 */
@Composable
fun OnboardingOverlay(
    state: OnboardingState,
    onRequestTab: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    MaaAnimatedVisibility(
        visible = state.active,
        modifier = modifier,
        enter = MaaMotion.fadeIn(reduceMotion),
        exit = MaaMotion.fadeOut(reduceMotion),
        label = "OnboardingOverlay",
    ) {
        Spotlight(state = state, reduceMotion = reduceMotion, onRequestTab = onRequestTab)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Spotlight(
    state: OnboardingState,
    reduceMotion: Boolean,
    onRequestTab: (BottomNavTab) -> Unit,
) {
    val density = LocalDensity.current
    val currentOnRequestTab by rememberUpdatedState(onRequestTab)

    BackHandler(enabled = state.active) {
        if (state.stepIndex > 0) state.previous() else state.finish()
    }

    // 靶点上报的是 root 坐标，换算到覆盖层自身坐标
    var origin by remember { mutableStateOf(Offset.Zero) }
    val holePadding = with(density) { HolePadding.toPx() }
    val focusInflate = with(density) { FocusInflate.toPx() }
    val defocusInflate = with(density) { DefocusInflate.toPx() }

    val holeRect = remember {
        Animatable(Rect.Zero, Rect.VectorConverter, visibilityThreshold = Rect(0.5f, 0.5f, 0.5f, 0.5f))
    }
    val holeAlpha = remember { Animatable(0f) }
    val cardAlpha = remember { Animatable(0f) }

    // 隐藏期间换锚点直接跳位，淡入时不会还在半路
    var cardAnchor by remember { mutableStateOf<Rect?>(null) }
    var cardHidden by remember { mutableStateOf(true) }

    // 外层编排步骤（跨页黑场剪辑），内层跟靶点落定与跟随
    LaunchedEffect(state, reduceMotion, holePadding) {
        var shownTab: BottomNavTab? = null

        suspend fun defocus() {
            if (holeAlpha.value == 0f) return
            if (reduceMotion) {
                holeAlpha.snapTo(0f)
                return
            }
            coroutineScope {
                launch { holeAlpha.animateTo(0f, MaaMotion.spec(false, MaaMotion.Fast, MaaMotion.Linear)) }
                launch { holeRect.animateTo(holeRect.value.inflate(defocusInflate), MaaMotion.spec(false, MaaMotion.Fast)) }
            }
        }

        suspend fun focus(rect: Rect) {
            if (reduceMotion) {
                holeRect.snapTo(rect)
                holeAlpha.snapTo(1f)
                return
            }
            if (holeAlpha.value == 0f) holeRect.snapTo(rect.inflate(focusInflate))
            coroutineScope {
                launch { holeRect.animateTo(rect, SpotlightSpring) }
                if (holeAlpha.value < 1f) {
                    launch { holeAlpha.animateTo(1f, MaaMotion.spec(false, MaaMotion.Medium, MaaMotion.Linear)) }
                }
            }
        }

        suspend fun hideCard() {
            cardHidden = true
            if (cardAlpha.value == 0f) return
            if (reduceMotion) cardAlpha.snapTo(0f)
            else cardAlpha.animateTo(0f, MaaMotion.spec(false, MaaMotion.Fast, MaaMotion.Linear))
        }

        suspend fun showCard() {
            if (cardAlpha.value < 1f) {
                if (reduceMotion) cardAlpha.snapTo(1f)
                else cardAlpha.animateTo(1f, MaaMotion.spec(false, MaaMotion.Medium, MaaMotion.Linear))
            }
            cardHidden = false
        }

        suspend fun blackout() = coroutineScope {
            launch { defocus() }
            launch { hideCard() }
        }

        snapshotFlow { state.stepIndex }.collectLatest { index ->
            val step = state.steps[index]
            if (shownTab != null && step.tab != shownTab) {
                // 跨页当剪辑：先黑场，再瞬时切页，靶点到位后揭示
                blackout()
                currentOnRequestTab(step.tab)
            } else if (shownTab == null) {
                currentOnRequestTab(step.tab)
            }
            shownTab = step.tab

            val target = step.target
            if (target == null) {
                cardAnchor = null
                coroutineScope {
                    launch { defocus() }
                    launch { showCard() }
                }
                return@collectLatest
            }

            var focused = false
            var pendingAt = 0L
            snapshotFlow { state.bounds[target]?.translate(-origin)?.inflate(holePadding) }
                .collectLatest { rect ->
                    if (rect == null) {
                        focused = false
                        defocus()
                        delay(MissingTargetGraceMillis)
                        cardAnchor = null
                        showCard()
                        return@collectLatest
                    }
                    if (focused) {
                        // 已聚焦的靶点挪动：直接跟随
                        cardAnchor = rect
                        focus(rect)
                        return@collectLatest
                    }
                    // 落定窗口内又动 = 随列表滚动中：先散焦，落定后再聚焦
                    val now = System.nanoTime()
                    val moving = now - pendingAt < SettleNanos
                    pendingAt = now
                    if (moving) defocus()
                    if (!reduceMotion) delay(SettleMillis)
                    focused = true
                    cardAnchor = rect
                    coroutineScope {
                        launch { focus(rect) }
                        launch { showCard() }
                    }
                }
        }
    }

    val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = ScrimAlpha)
    val strokeColor = MaterialTheme.colorScheme.primary
    val holeCorner = with(density) { HoleCorner.toPx() }
    val strokeWidth = with(density) { HoleStroke.toPx() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .consumeAllPointerEvents()
            // DstOut 需要离屏图层，否则抠掉的是窗口背景而不是遮罩
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                drawRect(scrimColor)
                val alpha = holeAlpha.value
                if (alpha > 0f) {
                    val rect = holeRect.value
                    val corner = CornerRadius(holeCorner)
                    // 按 src alpha 抠掉遮罩，洞因此能渐显渐隐
                    drawRoundRect(
                        color = Color.Black.copy(alpha = alpha),
                        topLeft = rect.topLeft,
                        size = rect.size,
                        cornerRadius = corner,
                        blendMode = BlendMode.DstOut,
                    )
                    drawRoundRect(
                        color = strokeColor.copy(alpha = StrokeAlpha * alpha),
                        topLeft = rect.topLeft,
                        size = rect.size,
                        cornerRadius = corner,
                        style = Stroke(width = strokeWidth),
                    )
                }
            },
    ) {
        val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
        val layoutDirection = LocalLayoutDirection.current
        // 卡片按锚点摆放，位移/尺寸交给 animateBounds，与洞动画独立
        LookaheadScope {
            Layout(
                content = {
                    OnboardingCard(
                        state = state,
                        reduceMotion = reduceMotion,
                        modifier = Modifier
                            .animateBounds(
                                lookaheadScope = this@LookaheadScope,
                                boundsTransform = { _, _ ->
                                    if (reduceMotion || cardHidden) snap() else SpotlightSpring
                                },
                            )
                            .graphicsLayer { alpha = cardAlpha.value },
                    )
                },
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
                    val offset = OnboardingPlacement.resolve(
                        area = area,
                        hole = cardAnchor,
                        cardWidth = placeable.width,
                        cardHeight = placeable.height,
                        gap = gap,
                    )
                    placeable.place(offset)
                }
            }
        }
    }
}

@Composable
private fun OnboardingCard(
    state: OnboardingState,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    // 遮罩之上的卡片必须不透明，自定义背景的玻璃配色会透出底下的黑
    OpaqueTheme {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            AnimatedContent(
                targetState = state.stepIndex,
                transitionSpec = {
                    if (reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None using
                            SizeTransform(clip = false) { _, _ -> snap() }
                    } else {
                        val forward = targetState > initialState
                        val enter = fadeIn(MaaMotion.spec(false, MaaMotion.Medium, MaaMotion.Linear)) +
                            slideInVertically(MaaMotion.spec(false, MaaMotion.Medium)) { height ->
                                if (forward) height / 8 else -height / 8
                            }
                        val exit = fadeOut(MaaMotion.spec(false, MaaMotion.Fast, MaaMotion.Linear))
                        enter togetherWith exit using
                            SizeTransform(clip = false) { _, _ -> MaaMotion.spec(false, MaaMotion.Medium) }
                    }
                },
                label = "OnboardingCardContent",
            ) { index ->
                OnboardingCardContent(state = state, step = state.steps[index], index = index)
            }
        }
    }
}

@Composable
private fun OnboardingCardContent(state: OnboardingState, step: OnboardingStep, index: Int) {
    val isLast = index == state.steps.lastIndex
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
                    text = stringResource(R.string.onboarding_step_counter, index + 1, state.steps.size),
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
                .verticalScroll(rememberScrollState()),
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
            if (index > 0) {
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
