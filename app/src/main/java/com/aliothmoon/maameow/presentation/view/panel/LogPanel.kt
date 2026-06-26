package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.LogColorRole
import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.model.RecruitCombination
import com.aliothmoon.maameow.presentation.components.AdaptiveTaskPromptDialog
import com.aliothmoon.maameow.theme.LocalLogPalette
import com.aliothmoon.maameow.theme.themedColor

/** 浮层形式的任务执行日志面板。 */
@Composable
fun LogPanel(
    logs: List<LogItem>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()
    var isAutoScroll by remember { mutableStateOf(true) }
    var selectedLog by remember { mutableStateOf<LogItem?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(logs.size, isAutoScroll) {
        if (isAutoScroll && logs.isNotEmpty()) {
            listState.scrollToItem(logs.size - 1)
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isAutoScroll = false
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.panel_log_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Row {
                IconButton(onClick = onClearLogs) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.common_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = logs,
                    key = { it.id }
                ) { logItem ->
                    LogLine(
                        logItem = logItem,
                        onClick = { selectedLog = logItem }
                    )
                }
            }

            if (listState.canScrollForward && logs.isNotEmpty()) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.scrollToItem(logs.size - 1)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.panel_log_resume_auto_scroll),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    selectedLog?.let { log ->
        LogDetailDialog(
            logItem = log,
            onDismiss = { selectedLog = null }
        )
    }
}

@Composable
private fun LogLine(
    logItem: LogItem,
    onClick: () -> Unit
) {
    val levelColor = logItem.level.themedColor()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = logItem.formattedTime,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
                modifier = Modifier.widthIn(min = 55.dp)
            )

            LogLevelBadge(level = logItem.level, color = levelColor, compact = true)

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = logItem.content,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp
                ),
                color = levelColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (logItem.hasDetails) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.panel_log_view_details),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LogDetailDialog(
    logItem: LogItem,
    onDismiss: () -> Unit
) {
    val levelColor = logItem.level.themedColor()
    AdaptiveTaskPromptDialog(
        visible = true,
        title = stringResource(R.string.log_detail_title),
        onConfirm = onDismiss,
        onDismissRequest = onDismiss,
        confirmText = stringResource(R.string.common_confirm),
        dismissText = null,
        icon = Icons.Rounded.Info,
        iconTint = levelColor,
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LogLevelBadge(level = logItem.level, color = levelColor, compact = false)
                    Text(
                        text = logItem.formattedTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = logItem.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = levelColor
                )

                val recruitTooltip = logItem.recruitTooltip
                val plainTooltip = logItem.tooltip
                if (recruitTooltip != null || plainTooltip != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.panel_log_details_section),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            if (recruitTooltip != null) {
                                val tooltipTextColor = MaterialTheme.colorScheme.onSurface
                                val richTooltip = rememberRecruitTooltipAnnotated(
                                    data = recruitTooltip,
                                    defaultColor = tooltipTextColor,
                                )
                                Text(
                                    text = richTooltip,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tooltipTextColor,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                            } else {
                                Text(
                                    text = plainTooltip!!,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                            }
                        }
                    }
                }

                logItem.screenshotPath?.let { path ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.panel_log_screenshot_section),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { /* TODO: 打开图片 */ }
                        )
                    }
                }
            }
        }
    )
}

/** [compact] 切换列表行 / 详情弹窗两档尺寸。 */
@Composable
private fun LogLevelBadge(
    level: LogLevel,
    color: Color,
    compact: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(if (compact) 3.dp else 4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = level.displayName,
            style = if (compact) {
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                MaterialTheme.typography.labelMedium
            },
            color = color,
            modifier = Modifier.padding(
                horizontal = if (compact) 4.dp else 6.dp,
                vertical = if (compact) 1.dp else 2.dp,
            )
        )
    }
}

/**
 * [defaultColor] 用于干员名等无星级修饰的片段，应与外层 [Text.color] 一致——
 * 否则外层 [Surface] 切换 `LocalContentColor` 会让默认色与外层不一致。
 */
@Composable
private fun rememberRecruitTooltipAnnotated(
    data: List<RecruitCombination>,
    defaultColor: Color,
): AnnotatedString {
    val palette = LocalLogPalette.current
    return remember(data, palette, defaultColor) {
        val starColors = arrayOf(
            Color.Unspecified, // 星级从 1 开始，索引 0 占位
            palette[LogColorRole.STAR_1],
            palette[LogColorRole.STAR_2],
            palette[LogColorRole.STAR_3],
            palette[LogColorRole.STAR_4],
            palette[LogColorRole.STAR_5],
            palette[LogColorRole.STAR_6],
        )

        fun colorOf(star: Int): Color = starColors.getOrNull(star) ?: defaultColor
        buildAnnotatedString {
            data.forEachIndexed { i, combo ->
                withStyle(
                    SpanStyle(
                        color = colorOf(combo.starLevel),
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append("${combo.starLevel}★ Tags:  ${combo.tags.joinToString("  ")}")
                }
                combo.opers.forEach { oper ->
                    append("\n  ")
                    withStyle(SpanStyle(color = colorOf(oper.starLevel))) {
                        append("★".repeat(oper.starLevel))
                    }
                    withStyle(SpanStyle(color = defaultColor)) {
                        append(" ${oper.name}")
                    }
                }
                if (i < data.lastIndex) append("\n\n")
            }
        }
    }
}
