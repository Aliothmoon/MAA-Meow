package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun ChangelogDialog(
    content: String,
    onDismiss: () -> Unit
) {
    AdaptiveTaskPromptDialog(
        visible = true,
        title = stringResource(R.string.dialog_changelog_title),
        onConfirm = onDismiss,
        onDismissRequest = onDismiss,
        confirmText = stringResource(R.string.common_i_got_it),
        dismissText = null,
        icon = Icons.Rounded.Info,
        content = {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                MarkdownText(
                    markdown = content,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    )
}
