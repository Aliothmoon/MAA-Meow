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
 * 数字输入框, 基于 [ITextFieldWithFocus]
 *
 * @param value 当前值
 * @param onValueChange 值变化回调（默认仅在失焦且验证通过后调用，见 [commitOnChange]）
 * @param modifier 修饰符
 * @param label 标签文本（浮动标签）
 * @param hint 提示文本（placeholder）
 * @param minimum 最小值
 * @param maximum 最大值
 * @param increment 步进值
 * @param valueFormat 格式化字符串
 * @param enabled 是否启用
 * @param commitOnChange 为 true 时每敲一下就回写合法值。面板里有「保存」按钮时用：
 *   只靠失焦提交的话，用户敲完数字直接点保存，写下去的还是上一个值
 * @param onBlankChange 配合 [commitOnChange] 用；框被清空（中间态）时回调 true，
 *   调用方据此禁用「保存」，免得把上一次的数字当成新值写进去
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
    enabled: Boolean = true,
    commitOnChange: Boolean = false,
    onBlankChange: ((Boolean) -> Unit)? = null,
) {
    var inputText by remember(value) { mutableStateOf(valueFormat.format(value)) }

    ITextFieldWithFocus(
        value = inputText,
        onValueChange = { raw ->
            inputText = raw
            if (commitOnChange) {
                onBlankChange?.invoke(raw.isEmpty())
                // 空串、溢出这些中间态不回写；value 没变，remember(value) 也就不会把框重置掉
                raw.toIntOrNull()?.let { onValueChange(it.coerceIn(minimum, maximum)) }
            }
        },
        onFocusLost = {
            val text = inputText
            onBlankChange?.invoke(false)
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
