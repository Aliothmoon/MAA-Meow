package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aliothmoon.maameow.theme.MaaThemeAlphas

/**
 * 面板里的卡片按钮，取自牛杂「优先系列事件」那组
 *
 * 与 [SelectableChipGroup] 的实心药丸不同，这里是描边卡片
 * [selected] 恒为 false 时就是一颗普通动作按钮
 */
@Composable
fun SelectableCardButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
) {
    val container = when {
        !enabled -> MaterialTheme.colorScheme.surface
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val border = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = MaaThemeAlphas.DISABLED)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = MaaThemeAlphas.DISABLED)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = container,
        border = BorderStroke(width = 1.dp, color = border),
        modifier = modifier.then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Text(
            text = text,
            style = textStyle,
            color = content,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
