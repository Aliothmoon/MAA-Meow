package com.aliothmoon.maameow.presentation.components

import android.text.InputType
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.presentation.LocalFloatingWindowContext
import com.aliothmoon.maameow.presentation.LocalInputFocusManager

/** 缓冲本地输入，避免异步回写导致光标跳转 */
@Composable
private fun rememberBufferedTextState(
    externalValue: String,
    onExternalChange: (String) -> Unit
): Pair<String, (String) -> Unit> {
    var localValue by remember { mutableStateOf(externalValue) }
    var dirty by remember { mutableStateOf(false) }

    // 等待上游确认，避免旧值覆盖本地草稿
    if (!dirty && localValue != externalValue) {
        localValue = externalValue
    }
    if (dirty && externalValue == localValue) {
        dirty = false
    }

    val onValueChange: (String) -> Unit = { newText ->
        dirty = true
        localValue = newText
        onExternalChange(newText)
    }

    return localValue to onValueChange
}

private fun KeyboardOptions.withDoneIfSingleLine(singleLine: Boolean): KeyboardOptions =
    if (singleLine && imeAction == ImeAction.Default) {
        copy(imeAction = ImeAction.Done)
    } else {
        this
    }

@Composable
fun ITextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    enabled: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    supportingText: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    outlineColor: Color? = null,
    cursorToEndRequest: Int = 0,
    onImeAction: (() -> Unit)? = null
) {
    val isInFloatingWindow = LocalFloatingWindowContext.current
    val (bufferedValue, bufferedOnChange) = rememberBufferedTextState(value, onValueChange)
    val inputFocusManager = LocalInputFocusManager.current
    val handleImeDone: () -> Unit = {
        onImeAction?.invoke()
        inputFocusManager.clear()
    }

    if (isInFloatingWindow) {
        FloatWindowEditText(
            value = bufferedValue,
            onValueChange = bufferedOnChange,
            modifier = modifier.fillMaxWidth(),
            label = label,
            hint = placeholder,
            singleLine = singleLine,
            enabled = enabled,
            shape = shape,
            outlineColor = outlineColor ?: MaterialTheme.colorScheme.outline,
            trailingIcon = trailingIcon,
            cursorToEndRequest = cursorToEndRequest,
            onImeAction = handleImeDone,
            inputType = if (singleLine) InputType.TYPE_CLASS_TEXT else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        )
    } else {
        // 仅候选回填重置选区，保留日常输入的组合状态
        var editingValue by remember(cursorToEndRequest) {
            mutableStateOf(
                TextFieldValue(
                    text = bufferedValue,
                    selection = if (cursorToEndRequest == 0) TextRange.Zero else TextRange(bufferedValue.length),
                )
            )
        }
        if (editingValue.text != bufferedValue) {
            editingValue = editingValue.copy(text = bufferedValue)
        }
        OutlinedTextField(
            value = editingValue,
            onValueChange = {
                val textChanged = it.text != editingValue.text
                editingValue = it
                if (textChanged) bufferedOnChange(it.text)
            },
            modifier = modifier.fillMaxWidth(),
            label = label?.let { { Text(it) } },
            placeholder = { Text(placeholder) },
            singleLine = singleLine,
            enabled = enabled,
            shape = shape,
            supportingText = supportingText,
            trailingIcon = trailingIcon,
            colors = if (outlineColor != null) {
                OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = outlineColor
                )
            } else {
                OutlinedTextFieldDefaults.colors()
            },
            keyboardOptions = KeyboardOptions.Default.withDoneIfSingleLine(singleLine),
            keyboardActions = if (singleLine) {
                KeyboardActions(onDone = { handleImeDone() })
            } else {
                KeyboardActions.Default
            }
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
    supportingText: @Composable (() -> Unit)? = null,
    inputFilter: ((String) -> Boolean)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    inputType: Int? = null,
) {
    val isInFloatingWindow = LocalFloatingWindowContext.current
    val inputFocusManager = LocalInputFocusManager.current
    val filteredOnChange: (String) -> Unit = if (inputFilter != null) {
        { text -> if (inputFilter(text)) onValueChange(text) }
    } else {
        onValueChange
    }
    val (bufferedValue, bufferedOnChange) = rememberBufferedTextState(value, filteredOnChange)
    val resolvedOptions = keyboardOptions.withDoneIfSingleLine(singleLine)
    val handleImeDone: () -> Unit = { inputFocusManager.clear() }

    if (isInFloatingWindow) {
        FloatWindowEditText(
            value = bufferedValue,
            onValueChange = bufferedOnChange,
            modifier = modifier.fillMaxWidth(),
            label = label,
            hint = placeholder,
            singleLine = singleLine,
            enabled = enabled,
            inputType = inputType
                ?: if (singleLine) InputType.TYPE_CLASS_TEXT
                else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            onImeAction = handleImeDone,
            onFocusChange = { hasFocus ->
                if (!hasFocus) {
                    onFocusLost()
                }
            }
        )
    } else {
        OutlinedTextField(
            value = bufferedValue,
            onValueChange = bufferedOnChange,
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
            supportingText = supportingText,
            keyboardOptions = resolvedOptions,
            keyboardActions = if (singleLine) {
                KeyboardActions(onDone = { handleImeDone() })
            } else {
                KeyboardActions.Default
            },
        )
    }
}
