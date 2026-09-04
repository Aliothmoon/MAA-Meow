package com.aliothmoon.maameow.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun MaaMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val scheme = MaterialTheme.colorScheme
    MarkdownText(
        markdown = markdown,
        modifier = modifier,
        style = style,
        syntaxHighlightColor = scheme.surfaceContainerHighest,
        syntaxHighlightTextColor = scheme.onSurface,
        linkColor = scheme.primary,
    )
}
