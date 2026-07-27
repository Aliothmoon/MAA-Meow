package com.aliothmoon.maameow.presentation.components

import android.text.InputType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * 数字输入框（失焦时验证）, 基于 [ITextFieldWithFocus]
 *
 * @param value 当前值
 * @param onValueChange 值变化回调（仅在失焦且验证通过后调用）
 * @param modifier 修饰符
 * @param label 标签文本（浮动标签）
 * @param hint 提示文本（placeholder）
 * @param minimum 最小值
 * @param maximum 最大值
 * @param increment 步进值
 * @param valueFormat 格式化字符串
 * @param enabled 是否启用
 */
@Composable
fun INumericField(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    hint: String = "",
    minimum: Int = 0,
    maximum: Int = Int.MAX_VALUE,
    increment: Int = 1,
    valueFormat: String = "%d",
    enabled: Boolean = true
) {
    var inputText by remember(value) { mutableStateOf(valueFormat.format(value)) }

    ITextFieldWithFocus(
        value = inputText,
        onValueChange = { inputText = it },
        onFocusLost = {
            val text = inputText
            if (text.isEmpty() || text == "-") {
                inputText = valueFormat.format(minimum)
                if (minimum != value) onValueChange(minimum)
            } else {
                val intValue = text.toIntOrNull()
                if (intValue != null) {
                    val clampedValue = intValue.coerceIn(minimum, maximum)
                    val alignedValue = if (increment > 1) {
                        (clampedValue / increment) * increment
                    } else {
                        clampedValue
                    }
                    inputText = valueFormat.format(alignedValue)
                    if (alignedValue != value) onValueChange(alignedValue)
                } else {
                    inputText = valueFormat.format(value)
                }
            }
        },
        modifier = modifier,
        label = label,
        placeholder = hint,
        enabled = enabled,
        inputFilter = { it.isEmpty() || it == "-" || it.toIntOrNull() != null },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED,
    )
}
