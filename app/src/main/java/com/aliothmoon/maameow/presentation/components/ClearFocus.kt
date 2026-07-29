package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import com.aliothmoon.maameow.presentation.LocalInputFocusManager

/** 点空白、点输入框外、用户滚动时清输入焦点（需 [com.aliothmoon.maameow.presentation.ProvideInputFocusManager]） */
@Composable
fun Modifier.clearFocusOnBlankTap(): Modifier {
    val inputFocusManager = LocalInputFocusManager.current
    val hostView = LocalView.current
    val latestClear by rememberUpdatedState(inputFocusManager)
    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 仅用户手势滚动清焦, 避免 bringIntoView / IME relocate 等 SideEffect 误清
                if (available != Offset.Zero && source == NestedScrollSource.UserInput) {
                    latestClear.clear()
                }
                return Offset.Zero
            }
        }
    }

    return this
        .onGloballyPositioned { layoutCoordinates = it }
        .nestedScroll(scrollConnection)
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
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
                    if (!inside) {
                        latestClear.clear()
                    }
                }
            }
        }
        .pointerInput(Unit) {
            detectTapGestures {
                latestClear.clear()
            }
        }
}
