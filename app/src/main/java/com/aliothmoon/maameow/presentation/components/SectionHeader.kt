package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.aliothmoon.maameow.theme.MaaDesignTokens

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = MaaDesignTokens.Spacing.sm),
    )
}

@Composable
fun ListItemDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
) {
    HorizontalDivider(
        modifier = modifier.padding(start = MaaDesignTokens.Separator.inset),
        thickness = MaaDesignTokens.Separator.thickness,
        color = color,
    )
}
