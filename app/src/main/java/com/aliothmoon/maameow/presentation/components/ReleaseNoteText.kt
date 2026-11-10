package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.utils.Misc

internal data class ReleaseNoteHeader(val version: String, val url: String, val date: String)

/** git-cliff 的版本标题行：## [0.25.3](url) - 2026-11-04 */
private val HEADER_REGEX = Regex("""^##\s*\[([^\]]+)]\(([^)\s]+)\)\s*-\s*(\d{4}-\d{2}-\d{2})$""")

/** Markdown 二级标题太大，首行版本标题单独排版 */
internal fun splitReleaseHeader(markdown: String): Pair<ReleaseNoteHeader?, String> {
    val text = markdown.trimStart()
    val match = HEADER_REGEX.matchEntire(text.substringBefore('\n').trim()) ?: return null to markdown
    val (version, url, date) = match.destructured
    return ReleaseNoteHeader(version, url, date) to text.substringAfter('\n', "").trimStart()
}

@Composable
fun ReleaseNoteText(markdown: String, modifier: Modifier = Modifier) {
    val (header, body) = remember(markdown) { splitReleaseHeader(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        header?.let { ReleaseNoteHeaderRow(it) }
        MaaMarkdownText(
            markdown = body,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ReleaseNoteHeaderRow(header: ReleaseNoteHeader) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.clickable { Misc.openUriSafely(context, header.url) },
        ) {
            Text(
                text = "v${header.version}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Text(
            text = header.date,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
