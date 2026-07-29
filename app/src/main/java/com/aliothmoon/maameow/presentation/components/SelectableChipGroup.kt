package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 通用可选芯片按钮组
 * 使用 FlowRow 自动换行平铺显示选项
 *
 * 颜色规范:
 * - 选中: primary / onPrimary
 * - 未选中: outline(0.3) / onSurface
 * - 禁用: surfaceVariant(0.38) / onSurface(0.25)
 * - 选中+全局禁用: primary(0.5) / onPrimary
 */
@Composable
fun <T> SelectableChipGroup(
    label: String,
    selectedValue: T,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isItemEnabled: (T) -> Boolean = { true },
    labelFontWeight: FontWeight? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = labelFontWeight,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { (value, displayName) ->
                val isSelected = value == selectedValue
                val itemEnabled = enabled && isItemEnabled(value)
                val chipColor = when {
                    isSelected && !enabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    isSelected -> MaterialTheme.colorScheme.primary
                    !itemEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                }
                val textColor = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    !itemEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (itemEnabled || isSelected) Modifier.clickable { onSelected(value) }
                            else Modifier
                        ),
                    color = chipColor,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
