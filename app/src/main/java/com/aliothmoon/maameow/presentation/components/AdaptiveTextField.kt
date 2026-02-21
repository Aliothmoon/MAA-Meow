package com.aliothmoon.maameow.presentation.components

import android.text.InputType
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import com.aliothmoon.maameow.presentation.LocalFloatingWindowContext


@Composable
fun ITextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    enabled: Boolean = true,
    supportingText: @Composable (() -> Unit)? = null,
    outlineColor: Color? = null,
    onImeAction: (() -> Unit)? = null
) {
    val isInFloatingWindow = LocalFloatingWindowContext.current

    if (isInFloatingWindow) {
        // 悬浮窗环境：使用 FloatWindowEditText
        FloatWindowEditText(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth(),
            label = label,
            hint = placeholder,
            singleLine = singleLine,
            enabled = enabled,
            inputType = if (singleLine) InputType.TYPE_CLASS_TEXT else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        )
    } else {
        // 普通环境：使用 OutlinedTextField
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth(),
            label = label?.let { { Text(it) } },
            placeholder = { Text(placeholder) },
            singleLine = singleLine,
            enabled = enabled,
            supportingText = supportingText
        )
    }
}


@Composable
fun ITextFieldWithFocus(
    value: String,
    onValueChange: (String) -> Unit,
    onFocusLost: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    enabled: Boolean = true,
    supportingText: @Composable (() -> Unit)? = null
) {
    val isInFloatingWindow = LocalFloatingWindowContext.current

    if (isInFloatingWindow) {
        // 悬浮窗环境：使用 FloatWindowEditText
        FloatWindowEditText(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth(),
            label = label,
            hint = placeholder,
            singleLine = singleLine,
            enabled = enabled,
            inputType = if (singleLine) InputType.TYPE_CLASS_TEXT else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            onFocusChange = { hasFocus ->
                if (!hasFocus) {
                    onFocusLost()
                }
            }
        )
    } else {
        // 普通环境：使用 OutlinedTextField + onFocusChanged
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        onFocusLost()
                    }
                },
            label = label?.let { { Text(it) } },
            placeholder = { Text(placeholder) },
            singleLine = singleLine,
            enabled = enabled,
            supportingText = supportingText
        )
    }
}
