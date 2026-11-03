package com.aliothmoon.maameow.presentation.onboarding.demo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.presentation.components.GuideDemoHooks
import com.aliothmoon.maameow.presentation.components.LocalGuideDemoHooks
import com.aliothmoon.maameow.presentation.components.LocalGuideHighlight
import com.aliothmoon.maameow.presentation.components.drawGuideFrame
import com.aliothmoon.maameow.presentation.components.rememberGuidePhase
import com.aliothmoon.maameow.presentation.onboarding.OnboardingState
import com.aliothmoon.maameow.presentation.view.panel.TaskListDetailLayout
import com.aliothmoon.maameow.theme.LocalReduceMotion
import com.aliothmoon.maameow.theme.OpaqueTheme
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/** 一轮手势：出现 → 长按 → 拖动 → 松手 → 消失 → 停顿 */
private const val GESTURE_CYCLE_MS = 3_000
private const val APPEAR_END = 0.10f
private const val PRESS_END = 0.30f
private const val MOVE_END = 0.68f
private const val RELEASE_END = 0.78f
private const val FADE_END = 0.88f

private val HandSize = 40.dp

/** 手指图标的指尖在图标内的相对位置 */
private const val TIP_X = 0.45f
private const val TIP_Y = 0.18f

/** 任务设置演示：真实列表组件喂内存数据，门槛做完才能下一步 */
@Composable
internal fun TaskDemoStage(state: OnboardingState, demo: TaskDemoState) {
    val nightTheme = stringResource(R.string.onboarding_demo_night_theme)
    val gate = TaskDemoRules.gate(demo.nodes, nightTheme)
    val highlight = TaskDemoRules.highlight(gate, demo.nodes, demo.ui)
    val dragHint = TaskDemoRules.dragHint(gate, demo.nodes)

    val hooks = remember { GuideDemoHooks() }
    val clientType = koinInject<TaskChainState>().clientType

    OpaqueTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                GateTip(gate = gate, nightTheme = nightTheme)
                Spacer(modifier = Modifier.height(8.dp))

                var pressed by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // 只旁听不消费，按下时收起手势动画
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    pressed = event.changes.any { it.pressed }
                                }
                            }
                        },
                ) {
                    CompositionLocalProvider(
                        LocalGuideHighlight provides highlight,
                        LocalGuideDemoHooks provides hooks,
                    ) {
                        TaskListDetailLayout(
                            nodes = demo.nodes,
                            selectedNode = demo.selectedNode,
                            selectedNodeId = demo.ui.selectedNodeId,
                            isEditMode = demo.ui.isEditMode,
                            isAddingTask = demo.ui.isAddingTask,
                            isProfileMode = false,
                            profiles = emptyList(),
                            activeProfileId = "",
                            clientType = clientType,
                            onNodeEnabledChange = demo::setEnabled,
                            onNodeSelected = demo::select,
                            onNodeMove = demo::move,
                            onToggleEditMode = demo::toggleEditMode,
                            onToggleAddingTask = demo::toggleAddingTask,
                            // 演示不涉及配置方案
                            onToggleProfileMode = {},
                            onConfigChange = demo::updateConfig,
                            onAddNode = demo::add,
                            onRemoveNode = demo::remove,
                            onDuplicateNode = demo::duplicate,
                            onRenameNode = demo::rename,
                            onRunFromNode = {},
                            canRunFromNode = false,
                            onSwitchProfile = {},
                            onRenameProfile = { _, _ -> },
                            onDuplicateProfile = {},
                            onDeleteProfile = {},
                            onCreateProfile = {},
                            onReorderProfile = { _, _ -> },
                            wrapDetailInCard = true,
                        )
                    }
                    GuideFrames(hooks)
                    if (dragHint != null && !pressed) {
                        DragGestureHint(hint = dragHint, hooks = hooks)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                DemoFooter(state = state, done = gate == TaskDemoGate.DONE)
            }
        }
    }
}

@Composable
private fun GateTip(gate: TaskDemoGate, nightTheme: String) {
    val done = gate == TaskDemoGate.DONE
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (done) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (done) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        AnimatedContent(
            targetState = gate,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "DemoGateTip",
        ) { current ->
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (current == TaskDemoGate.DONE) Icons.Rounded.CheckCircle else Icons.Rounded.TouchApp,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = gateTip(current, nightTheme),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun gateTip(gate: TaskDemoGate, nightTheme: String): String = when (gate) {
    TaskDemoGate.RESTORE_FIGHT -> stringResource(R.string.onboarding_demo_gate_restore_fight)
    TaskDemoGate.ADD_FIGHT -> stringResource(R.string.onboarding_demo_gate_add_fight)
    TaskDemoGate.SELECT_STAGE -> stringResource(R.string.onboarding_demo_gate_select_stage)
    TaskDemoGate.REORDER_FIGHT -> stringResource(R.string.onboarding_demo_gate_reorder_fight)
    TaskDemoGate.ENABLE_WEEKLY -> stringResource(R.string.onboarding_demo_gate_enable_weekly)
    TaskDemoGate.CHECK_WEEK_DAYS -> stringResource(R.string.onboarding_demo_gate_check_week_days)
    TaskDemoGate.ADD_SWITCH_THEME -> stringResource(R.string.onboarding_demo_gate_add_switch_theme)
    TaskDemoGate.REORDER_THEME -> stringResource(R.string.onboarding_demo_gate_reorder_theme)
    TaskDemoGate.ADD_NIGHT_THEME -> stringResource(R.string.onboarding_demo_gate_add_night_theme, nightTheme)
    TaskDemoGate.DONE -> stringResource(R.string.onboarding_demo_done)
}

@Composable
private fun DemoFooter(state: OnboardingState, done: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 演示随时可跳过，不受首启强制限制
        if (!done) {
            TextButton(onClick = { state.next() }) {
                Text(stringResource(R.string.onboarding_demo_skip))
            }
        } else if (state.skippable) {
            TextButton(onClick = { state.finish() }) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = { state.previous() }) {
            Text(stringResource(R.string.onboarding_previous))
        }
        Spacer(modifier = Modifier.width(4.dp))
        Button(
            onClick = { state.next() },
            enabled = done,
            shape = MaterialTheme.shapes.large,
        ) {
            Text(stringResource(R.string.onboarding_next))
        }
    }
}

/** 顶层统一画描边框，不拦截触摸 */
@Composable
private fun GuideFrames(hooks: GuideDemoHooks) {
    val phase = rememberGuidePhase(LocalReduceMotion.current)
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawBehind {
                hooks.frames.values.forEach { frame ->
                    drawGuideFrame(frame.rect.translate(-origin), frame.corner, phase.value)
                }
            },
    )
}

/** 长按拖动示意，不拦截触摸 */
@Composable
private fun DragGestureHint(hint: DragHint, hooks: GuideDemoHooks) {
    val from = hooks.nodeBounds[hint.nodeId] ?: return
    val anchor = hooks.nodeBounds[hint.anchorId] ?: return
    val start = from.center
    val end = Offset(start.x, if (hint.below) anchor.bottom else anchor.top)

    val reduceMotion = LocalReduceMotion.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(hint, reduceMotion) {
        if (reduceMotion) {
            // 不放动画，停在按住的姿势
            progress.snapTo(PRESS_END)
            return@LaunchedEffect
        }
        while (true) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(GESTURE_CYCLE_MS, easing = LinearEasing))
        }
    }

    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val handPx = with(density) { HandSize.toPx() }
    val rippleMax = with(density) { 28.dp.toPx() }
    val color = MaterialTheme.colorScheme.primary

    fun tip(): Offset {
        val p = progress.value
        val t = ((p - PRESS_END) / (MOVE_END - PRESS_END)).coerceIn(0f, 1f)
        return lerp(start, end, FastOutSlowInEasing.transform(t)) - origin
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawBehind {
                // 长按蓄力圈
                val p = progress.value
                if (p in APPEAR_END..PRESS_END) {
                    val k = (p - APPEAR_END) / (PRESS_END - APPEAR_END)
                    drawCircle(color, radius = rippleMax * k, center = tip(), alpha = 0.35f * (1f - k))
                }
                if (p in APPEAR_END..RELEASE_END) {
                    drawCircle(color, radius = rippleMax * 0.35f, center = tip(), alpha = 0.3f)
                }
            },
    ) {
        Icon(
            imageVector = Icons.Rounded.TouchApp,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(HandSize)
                .offset {
                    val t = tip()
                    IntOffset((t.x - handPx * TIP_X).roundToInt(), (t.y - handPx * TIP_Y).roundToInt())
                }
                .graphicsLayer {
                    val p = progress.value
                    alpha = when {
                        p < APPEAR_END -> p / APPEAR_END
                        p < RELEASE_END -> 1f
                        p < FADE_END -> 1f - (p - RELEASE_END) / (FADE_END - RELEASE_END)
                        else -> 0f
                    }
                    val press = when {
                        p < APPEAR_END -> 0f
                        p < PRESS_END -> (p - APPEAR_END) / (PRESS_END - APPEAR_END)
                        p < MOVE_END -> 1f
                        p < RELEASE_END -> 1f - (p - MOVE_END) / (RELEASE_END - MOVE_END)
                        else -> 0f
                    }
                    val scale = 1f - 0.15f * press
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(TIP_X, TIP_Y)
                },
        )
    }
}
