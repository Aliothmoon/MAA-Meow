package com.aliothmoon.maameow.presentation.components

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged


@Suppress("COMPOSE_APPLIER_CALL_MISMATCH")
@Composable
fun FloatWindowEditText(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    hint: String = "",
    singleLine: Boolean = true,
    inputType: Int = InputType.TYPE_CLASS_TEXT,
    enabled: Boolean = true,
    minHeight: Dp = 44.dp,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    hintColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    outlineColor: Color = MaterialTheme.colorScheme.outline,
    focusedOutlineColor: Color = MaterialTheme.colorScheme.primary,
    trailingIcon: @Composable (() -> Unit)? = null,
    onImeAction: (() -> Unit)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null
) {
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val textColorInt = remember(textColor) { textColor.toArgb() }
    val hintColorInt = remember(hintColor) { hintColor.toArgb() }

    val paddingPx = remember(density) { with(density) { 12.dp.roundToPx() } }

    var editTextRef by remember { mutableStateOf<ExtractModeEditText?>(null) }
    var isFocused by remember { mutableStateOf(false) }

    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnImeAction by rememberUpdatedState(onImeAction)
    val currentOnFocusChange by rememberUpdatedState(onFocusChange)

    LaunchedEffect(value) {
        editTextRef?.let { et ->
            if (et.text.toString() != value) {
                val selectionStart = et.selectionStart.coerceIn(0, value.length)
                et.setText(value)
                et.setSelection(selectionStart.coerceAtMost(value.length))
            }
        }
    }

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(backgroundColor)
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) focusedOutlineColor else outlineColor,
                    shape = shape
                )
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = minHeight)
                    .then(
                        if (trailingIcon != null) {
                            Modifier.padding(end = 40.dp)
                        } else {
                            Modifier
                        }
                    ),
                factory = { ctx ->
                    ExtractModeEditText(ctx).apply {
                        background = null

                        // 悬浮窗焦点配置
                        isFocusable = true
                        isFocusableInTouchMode = true
                        isCursorVisible = true

                        // 文本样式
                        setTextColor(textColorInt)
                        setHintTextColor(hintColorInt)
                        textSize = 16f
                        setPadding(paddingPx, paddingPx, paddingPx, paddingPx)

                        // 输入配置
                        this.inputType = inputType
                        this.isSingleLine = singleLine
                        this.isEnabled = enabled
                        this.hint = hint
                        this.imeOptions = EditorInfo.IME_ACTION_DONE

                        setText(value)

                        // 文本变化监听
                        doAfterTextChanged { editable ->
                            val newText = editable?.toString() ?: ""
                            if (newText != currentValue) {
                                currentOnValueChange(newText)
                            }
                        }

                        // IME 动作监听
                        setOnEditorActionListener { _, actionId, _ ->
                            if (actionId == EditorInfo.IME_ACTION_DONE) {
                                currentOnImeAction?.invoke()
                                clearFocus()
                                keyboardController?.hide()
                                true
                            } else false
                        }

                        // 焦点变化监听
                        setOnFocusChangeListener { _, hasFocus ->
                            isFocused = hasFocus
                            currentOnFocusChange?.invoke(hasFocus)
                            if (hasFocus) {
                                keyboardController?.show()
                            }
                        }

                        // 点击时请求焦点并显示键盘
                        setOnClickListener {
                            if (!hasFocus()) {
                                requestFocus()
                            }
                            keyboardController?.show()
                        }

                        editTextRef = this
                    }
                },
                update = { et ->
                    if (et.isEnabled != enabled) {
                        et.isEnabled = enabled
                    }
                    if (et.hint != hint) {
                        et.hint = hint
                    }
                    if (et.inputType != inputType) {
                        et.inputType = inputType
                    }
                    et.setTextColor(textColorInt)
                    et.setHintTextColor(hintColorInt)
                }
            )
            if (trailingIcon != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                ) {
                    trailingIcon()
                }
            }
        }
    }
}


private class ExtractModeEditText(context: Context) : AppCompatEditText(context) {

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs)

        // 移除阻止 Extract Mode 的标志
        outAttrs.imeOptions = outAttrs.imeOptions and
                EditorInfo.IME_FLAG_NO_EXTRACT_UI.inv() and
                EditorInfo.IME_FLAG_NO_FULLSCREEN.inv()

        return connection
    }

    override fun getGlobalVisibleRect(r: Rect?, globalOffset: Point?): Boolean {
        // 报告较小的可见区域，促使 IME 进入 Extract Mode
        val result = super.getGlobalVisibleRect(r, globalOffset)
        r?.let {
            // 设置小一点，触发 Extract Mode
            it.bottom = it.top + 1
        }
        return result
    }
}

@Composable
fun FloatWindowMultilineField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    hint: String = "",
    enabled: Boolean = true,
    minHeight: Dp = 100.dp,
    onFocusChange: ((Boolean) -> Unit)? = null
) {
    FloatWindowEditText(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        hint = hint,
        singleLine = false,
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
        enabled = enabled,
        minHeight = minHeight,
        onFocusChange = onFocusChange
    )
}
