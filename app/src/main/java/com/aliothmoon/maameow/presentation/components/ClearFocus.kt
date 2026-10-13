package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.TraversableNode.Companion.TraverseDescendantsAction
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.node.traverseDescendants
import androidx.compose.ui.platform.LocalView
import com.aliothmoon.maameow.presentation.LocalInputFocusManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 点空白、点输入框外、用户滚动时清输入焦点（需 [com.aliothmoon.maameow.presentation.ProvideInputFocusManager]） */
@Composable
fun Modifier.clearFocusOnBlankTap(): Modifier {
    val inputFocusManager = LocalInputFocusManager.current
    val hostView = LocalView.current
    val latestClear by rememberUpdatedState(inputFocusManager)
    val preload = LocalPagerPreloadState.current
    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val inputGroups = remember { InputFocusGroups() }
    val releaseScope = rememberCoroutineScope()

    val scrollConnection = remember(preload) {
        object : NestedScrollConnection {
            var clearedForGesture = false
            var pointerScrolling = false

            fun clearOnce() {
                if (!clearedForGesture) {
                    clearedForGesture = true
                    latestClear.clear()
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available != Offset.Zero) preload?.onScroll()
                // bringIntoView 也使用 UserInput，只在真实拖动或滚轮期间清焦
                if (pointerScrolling && available != Offset.Zero && source == NestedScrollSource.UserInput) {
                    clearOnce()
                }
                return Offset.Zero
            }
        }
    }

    return this
        .then(InputFocusGroupsElement(inputGroups))
        .onGloballyPositioned { layoutCoordinates = it }
        .nestedScroll(scrollConnection)
        .pointerInput(preload) {
            var releaseJob: Job? = null
            try {
                awaitPointerEventScope {
                    var dragDistance = Offset.Zero
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.any { it.pressed }
                        preload?.onPointerInput(pressed)
                        if (event.type == PointerEventType.Scroll ||
                            (event.type == PointerEventType.Press && event.changes.none { it.previousPressed })
                        ) {
                            scrollConnection.clearedForGesture = false
                            dragDistance = Offset.Zero
                            dragging = false
                        }
                        if (event.type == PointerEventType.Move) {
                            event.changes.firstOrNull { it.pressed && it.previousPressed }?.let {
                                dragDistance += it.position - it.previousPosition
                                dragging = dragging || dragDistance.getDistance() > viewConfiguration.touchSlop
                            }
                        }
                        if (event.type == PointerEventType.Scroll || (pressed && dragging)) {
                            releaseJob?.cancel()
                            scrollConnection.pointerScrolling = true
                        } else if (scrollConnection.pointerScrolling && releaseJob?.isActive != true) {
                            // 拖动位移异步分发，抬手后留一帧再复位
                            releaseJob = releaseScope.launch {
                                withFrameNanos { }
                                scrollConnection.pointerScrolling = false
                            }
                        }
                        if (event.type != PointerEventType.Press) continue
                        // 仅 Android 文本编辑器 (悬浮窗 EditText); Compose TextField 的 findFocus 通常是整窗
                        val focused = hostView.findFocus() ?: continue
                        if (focused === hostView || !focused.onCheckIsTextEditor()) continue
                        val change = event.changes.firstOrNull() ?: continue
                        val coords = layoutCoordinates ?: continue
                        if (!coords.isAttached) continue
                        val posInWindow = coords.localToWindow(change.position)
                        val loc = IntArray(2)
                        focused.getLocationInWindow(loc)
                        val x = posInWindow.x.toInt()
                        val y = posInWindow.y.toInt()
                        val inside = x in loc[0] until (loc[0] + focused.width) &&
                                y in loc[1] until (loc[1] + focused.height)
                        val editorCenter = Offset(loc[0] + focused.width / 2f, loc[1] + focused.height / 2f)
                        if (!inside && !inputGroups.contains(editorCenter, posInWindow)) {
                            scrollConnection.clearOnce()
                        }
                    }
                }
            } finally {
                releaseJob?.cancel()
                scrollConnection.pointerScrolling = false
                preload?.onPointerInput(false)
            }
        }
        .pointerInput(scrollConnection) {
            detectTapGestures {
                scrollConnection.clearOnce()
            }
        }
}

/** 焦点在组内原生输入框时，点组内不算点到框外 */
fun Modifier.inputFocusGroup(): Modifier = this then InputFocusGroupElement

private object InputFocusGroupKey

private object InputFocusGroupElement : ModifierNodeElement<InputFocusGroupNode>() {
    override fun create() = InputFocusGroupNode()
    override fun update(node: InputFocusGroupNode) = Unit
    override fun hashCode() = "inputFocusGroup".hashCode()
    override fun equals(other: Any?) = other === this
}

private class InputFocusGroupNode : Modifier.Node(), TraversableNode {
    override val traverseKey: Any get() = InputFocusGroupKey
}

private class InputFocusGroups {
    var node: DelegatableNode? = null

    fun contains(editorCenter: Offset, pointInWindow: Offset): Boolean {
        var hit = false
        node?.traverseDescendants(InputFocusGroupKey) { group ->
            val coords = group.requireLayoutCoordinates()
            val bounds = if (coords.isAttached) coords.boundsInWindow() else null
            hit = bounds != null && bounds.contains(editorCenter) && bounds.contains(pointInWindow)
            if (hit) TraverseDescendantsAction.CancelTraversal else TraverseDescendantsAction.ContinueTraversal
        }
        return hit
    }
}

private data class InputFocusGroupsElement(val groups: InputFocusGroups) :
    ModifierNodeElement<InputFocusGroupsNode>() {
    override fun create() = InputFocusGroupsNode(groups)
    override fun update(node: InputFocusGroupsNode) {
        node.groups.node = null
        node.groups = groups
        groups.node = node
    }
}

private class InputFocusGroupsNode(var groups: InputFocusGroups) : Modifier.Node() {
    override fun onAttach() {
        groups.node = this
    }

    override fun onDetach() {
        if (groups.node === this) groups.node = null
    }
}

/** 吞掉全部指针事件挡住下层；子节点先收到，自身按钮照常可点 */
fun Modifier.consumeAllPointerEvents(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}
