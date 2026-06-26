package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.aliothmoon.maameow.theme.MaaDesignTokens

@Composable
private fun BaseCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = MaaDesignTokens.Card.elevation),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    title: String = "",
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    BaseCard(
        modifier = modifier,
        contentPadding = PaddingValues(MaaDesignTokens.Card.innerPadding),
        containerColor = containerColor,
    ) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                modifier = Modifier.padding(bottom = MaaDesignTokens.Spacing.sm),
            )
        }
        content()
    }
}

@Composable
fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    BaseCard(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = MaaDesignTokens.Card.innerPadding,
            vertical = MaaDesignTokens.Spacing.xs,
        ),
        containerColor = containerColor,
        content = content,
    )
}
