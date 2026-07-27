package com.aliothmoon.maameow.presentation

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController

/** Compose TextField + 悬浮窗 EditText + IME 的输入失焦 */
interface InputFocusManager {
    fun clear(force: Boolean = true)
}

val LocalInputFocusManager = staticCompositionLocalOf<InputFocusManager> {
    error(
        "LocalInputFocusManager not provided. " +
            "Wrap content with ProvideInputFocusManager { }."
    )
}

@Composable
fun ProvideInputFocusManager(content: @Composable () -> Unit) {
    val focusManager = LocalFocusManager.current
    val hostView = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val manager = remember(focusManager, hostView, keyboardController) {
        object : InputFocusManager {
            override fun clear(force: Boolean) {
                clearInputFocus(
                    focusManager = focusManager,
                    hostView = hostView,
                    keyboardController = keyboardController,
                    force = force,
                )
            }
        }
    }
    CompositionLocalProvider(LocalInputFocusManager provides manager, content = content)
}

fun clearInputFocus(
    focusManager: FocusManager,
    hostView: View,
    keyboardController: SoftwareKeyboardController? = null,
    force: Boolean = true,
) {
    focusManager.clearFocus(force = force)

    val focused = hostView.findFocus()
    if (focused != null && focused.onCheckIsTextEditor()) {
        focused.clearFocus()
    }

    keyboardController?.hide()
    val imm = hostView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(hostView.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
}
