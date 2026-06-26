package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.aliothmoon.maameow.theme.MaaDesignTokens

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(
                    enabled = enabled, onClick = onClick
                ) else Modifier
            )
            .padding(vertical = MaaDesignTokens.Spacing.listItemVertical),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = if (trailing != null) Modifier.weight(1f) else Modifier,
            verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.rowTitleGap),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) titleColor else titleColor.copy(alpha = 0.6f),
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) descriptionColor else descriptionColor.copy(alpha = 0.6f),
                )
            }
        }
        trailing?.invoke()
    }
}
