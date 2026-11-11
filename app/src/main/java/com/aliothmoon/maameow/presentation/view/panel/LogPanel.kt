package com.aliothmoon.maameow.presentation.view.panel

import android.content.ClipData
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.LogColorRole
import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.model.RecruitCombination
import com.aliothmoon.maameow.data.model.RecruitTagsKind
import com.aliothmoon.maameow.data.model.RecruitTagsLog
import com.aliothmoon.maameow.presentation.components.AdaptiveTaskPromptDialog
import com.aliothmoon.maameow.presentation.components.LocalPageVisible
import com.aliothmoon.maameow.presentation.components.RainbowFlowText
import com.aliothmoon.maameow.presentation.components.RainbowStops
import com.aliothmoon.maameow.presentation.components.runRainbowFlow
import com.aliothmoon.maameow.theme.LocalReduceMotion
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility
import com.aliothmoon.maameow.theme.MaaMotion
import com.aliothmoon.maameow.theme.themedColor
import kotlinx.coroutines.launch

private val GroupGap = 8.dp
private val CardRadius = 12.dp
private val ShapeSingle = RoundedCornerShape(CardRadius)
private val ShapeTop = RoundedCornerShape(topStart = CardRadius, topEnd = CardRadius)
private val ShapeBottom = RoundedCornerShape(bottomStart = CardRadius, bottomEnd = CardRadius)
private val ShapeMiddle = RoundedCornerShape(0.dp)
private val TintShape = RoundedCornerShape(6.dp)
private const val LINE_MAX_LINES = 8

/** 浮层形式的任务执行日志面板，按任务链分组 */
@Composable
fun LogPanel(
    modifier: Modifier = Modifier,
    logs: List<LogItem>,
    running: Boolean = false,
    onClearLogs: () -> Unit,
    onExportLogs: (() -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    var isAutoScroll by remember { mutableStateOf(true) }
    var selectedLog by remember { mutableStateOf<LogItem?>(null) }
    // 用户手动展开/收起的覆盖值，按分组 id
    val toggled = remember { mutableStateMapOf<Long, Boolean>() }
    val groups = remember(logs) { groupLogs(logs) }
    val rows by remember(groups, running) {
        derivedStateOf { buildRows(groups, running, toggled) }
    }

    // 日志满上限后条数不再变，按末条 id 触发
    LaunchedEffect(logs.lastOrNull()?.id, isAutoScroll) {
        if (isAutoScroll && rows.isNotEmpty()) {
            listState.scrollToItem(rows.lastIndex)
        }
    }
    val showResumeButton by remember {
        derivedStateOf { !isAutoScroll && listState.canScrollForward }
    }

    // 手动滑回底部即恢复跟随
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isAutoScroll = false
        } else if (!listState.canScrollForward) {
            isAutoScroll = true
        }
    }

    // 可视高度变化（如展开画面预览）时列表保住首行，底部被挤出屏幕，跟随时重新贴底
    val follow by rememberUpdatedState(isAutoScroll)
    val lastIndex by rememberUpdatedState(rows.lastIndex)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.viewportSize.height }
            .collect { if (follow && lastIndex >= 0) listState.scrollToItem(lastIndex) }
    }

    // 标题行 36dp，与小工具页签行同高
    Column(modifier = modifier.fillMaxSize().padding(TabPanelPadding)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
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

            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Row {
                    if (onExportLogs != null) {
                        IconButton(onClick = onExportLogs, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.FolderZip,
                                contentDescription = stringResource(R.string.settings_log_export_title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onClearLogs, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.common_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (logs.isEmpty()) {
                Text(
                    text = stringResource(R.string.panel_log_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            val container = GroupColors(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outlineVariant)
            val a11y = rememberLogA11y()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(
                    items = rows,
                    key = { it.key },
                    contentType = { it.type }
                ) { row ->
                    when (row) {
                        is HeaderRow -> GroupHeader(
                            row = row,
                            container = container,
                            a11y = a11y,
                            onToggle = {
                                isAutoScroll = false
                                toggled[row.group.id] = !row.expanded
                            }
                        )

                        is LineRow -> LogLine(
                            row = row,
                            container = container,
                            a11y = a11y,
                            onOpen = { selectedLog = it }
                        )

                        is SummaryRow -> SummaryCard(group = row.group, container = container)
                    }
                }
            }

            val reduceMotion = LocalReduceMotion.current
            MaaAnimatedVisibility(
                visible = showResumeButton && logs.isNotEmpty(),
                enter = MaaMotion.fadeIn(reduceMotion),
                exit = MaaMotion.fadeOut(reduceMotion),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp),
            ) {
                IconButton(onClick = { isAutoScroll = true }) {
                    Icon(
                        imageVector = Icons.Rounded.VerticalAlignBottom,
                        contentDescription = stringResource(R.string.panel_log_resume_auto_scroll),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
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

// data class：每次刷入都会重建行，按值相等让未变的行跳过重组
@Immutable
private sealed interface LogRow {
    val key: Any
    val type: Int
}

@Immutable
private data class HeaderRow(
    val group: LogGroup,
    val status: LogTaskStatus,
    val expanded: Boolean,
    val last: Boolean,
) : LogRow {
    override val key: Any get() = "h${group.id}"
    override val type: Int get() = 0
}

@Immutable
private data class LineRow(
    val item: LogItem,
    val first: Boolean,
    val last: Boolean,
    /** 特殊标签行的星级，见 [specialStar]；未知为 0 */
    val star: Int = 0,
) : LogRow {
    override val key: Any get() = item.id
    override val type: Int get() = 1
}

@Immutable
private data class SummaryRow(val group: LogGroup) : LogRow {
    override val key: Any get() = "s${group.id}"
    override val type: Int get() = 2
}

/** 已结束的任务默认收起，最后一组与出错组默认展开 */
private fun buildRows(
    groups: List<LogGroup>,
    running: Boolean,
    toggled: Map<Long, Boolean>,
): List<LogRow> {
    val rows = ArrayList<LogRow>()
    groups.forEachIndexed { index, group ->
        val isLast = index == groups.lastIndex
        when (group.kind) {
            LogGroupKind.SUMMARY -> rows += SummaryRow(group)
            LogGroupKind.PLAIN -> group.lines.forEachIndexed { i, item ->
                rows += LineRow(item, first = i == 0, last = i == group.lines.lastIndex, star = specialStar(group.lines, i))
            }

            LogGroupKind.TASK -> {
                val status = group.status(running, isLast)
                val expanded = toggled[group.id] ?: (isLast || status == LogTaskStatus.FAILED)
                val showLines = expanded && group.lines.isNotEmpty()
                rows += HeaderRow(group, status, expanded, last = !showLines)
                if (showLines) {
                    group.lines.forEachIndexed { i, item ->
                        rows += LineRow(item, first = false, last = i == group.lines.lastIndex, star = specialStar(group.lines, i))
                    }
                }
            }
        }
    }
    return rows
}

/**
 * MaaCore 回传的特殊标签只有本地化名，不硬编码各语言标签名；
 * 事件顺序固定为 识别 → 特殊标签 → 结果，取同一槽位结果的最高星级
 */
private fun specialStar(lines: List<LogItem>, index: Int): Int {
    if (lines[index].recruitTags?.kind != RecruitTagsKind.SPECIAL) return 0
    for (i in index + 1 until lines.size) {
        val next = lines[i]
        next.recruitTooltip?.takeIf { it.isNotEmpty() }?.let { combos -> return combos.maxOf { it.starLevel } }
        if (next.recruitTags?.kind == RecruitTagsKind.DETECTED) break
    }
    return 0
}

// surface 底加描边：动态取色下 surface 与背景同色，靠描边分组
@Immutable
private data class GroupColors(val fill: Color, val outline: Color)

private fun Modifier.groupBlock(first: Boolean, last: Boolean, colors: GroupColors): Modifier {
    val shape: Shape = when {
        first && last -> ShapeSingle
        first -> ShapeTop
        last -> ShapeBottom
        else -> ShapeMiddle
    }
    return fillMaxWidth()
        .padding(top = if (first) GroupGap else 0.dp)
        .clip(shape)
        .background(colors.fill)
        .drawWithCache {
            val stroke = 1.dp.toPx()
            val radius = CardRadius.toPx()
            // 非首尾行的上下边外延到行外被裁掉，同组行之间不出横线
            val top = if (first) stroke / 2 else -radius * 2
            val bottom = if (last) size.height - stroke / 2 else size.height + radius * 2
            onDrawBehind {
                drawRoundRect(
                    color = colors.outline,
                    topLeft = Offset(stroke / 2, top),
                    size = Size(size.width - stroke, bottom - top),
                    cornerRadius = CornerRadius(radius - stroke / 2),
                    style = Stroke(stroke),
                )
            }
        }
}

@Composable
private fun GroupHeader(
    row: HeaderRow,
    container: GroupColors,
    a11y: LogA11y,
    onToggle: () -> Unit,
) {
    val group = row.group
    val start = group.start
    val title = start?.task ?: start?.content.orEmpty()
    val duration = if (start != null && group.end != null) {
        formatElapsed(group.end.timestampMillis - start.timestampMillis)
    } else {
        null
    }
    val expandable = group.lines.isNotEmpty()
    val rotation by animateFloatAsState(if (row.expanded) 180f else 0f, label = "logGroupChevron")

    Row(
        modifier = Modifier
            .groupBlock(first = true, last = row.last, colors = container)
            .then(if (expandable) Modifier.clickable(onClick = onToggle) else Modifier)
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(
                    title,
                    a11y.status(row.status),
                    duration,
                    group.warnings.takeIf { it > 0 }?.let { a11y.warnings.format(it) },
                ).joinToString(", ")
                if (expandable) {
                    role = Role.Button
                    stateDescription = if (row.expanded) a11y.expanded else a11y.collapsed
                    onClick(if (row.expanded) a11y.collapse else a11y.expand) { onToggle(); true }
                }
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TaskStatusIcon(row.status)
        Spacer(modifier = Modifier.width(10.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (group.warnings > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = LogColorRole.WARNING.themedColor(),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = group.warnings.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (duration != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = duration,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expandable) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation)
            )
        }
    }
}

@Composable
private fun TaskStatusIcon(status: LogTaskStatus) {
    val modifier = Modifier.size(18.dp)
    when (status) {
        LogTaskStatus.RUNNING -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        }

        LogTaskStatus.DONE -> Icon(
            Icons.Rounded.CheckCircle, null, modifier, tint = LogColorRole.SUCCESS.themedColor()
        )

        LogTaskStatus.FAILED -> Icon(
            Icons.Rounded.Cancel, null, modifier, tint = MaterialTheme.colorScheme.error
        )

        LogTaskStatus.INTERRUPTED -> Icon(
            Icons.Rounded.StopCircle, null, modifier, tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 读屏文案：面板级解析一次传给各行，长列表不在每行调 stringResource */
@Immutable
private data class LogA11y(
    val done: String,
    val failed: String,
    val running: String,
    val interrupted: String,
    val warnings: String,
    val expanded: String,
    val collapsed: String,
    val expand: String,
    val collapse: String,
    val viewDetails: String,
    val warning: String,
    val error: String,
    val starFormat: String,
    val recruitResult: String,
) {
    fun status(status: LogTaskStatus): String = when (status) {
        LogTaskStatus.DONE -> done
        LogTaskStatus.FAILED -> failed
        LogTaskStatus.RUNNING -> running
        LogTaskStatus.INTERRUPTED -> interrupted
    }

    fun starText(level: Int): String = starFormat.format(level)

    /** null 表示直接读正文 */
    fun describe(item: LogItem, star: Int): String? {
        item.recruitTags?.let { tags ->
            val base = "${tags.label}: ${tags.tags.joinToString(", ")}"
            return if (tags.kind == RecruitTagsKind.SPECIAL && star > 0) "$base, ${starText(star)}" else base
        }
        item.recruitTooltip?.takeIf { it.isNotEmpty() }?.let { combos ->
            val best = combos.maxBy { it.starLevel }
            return recruitResult.format(
                starText(best.starLevel),
                best.tags.joinToString(" + "),
                best.opers.joinToString(", ") { it.name },
            )
        }
        return when (item.level) {
            LogLevel.WARNING -> "$warning, ${item.content}"
            LogLevel.ERROR -> "$error, ${item.content}"
            else -> null
        }
    }
}

@Composable
private fun rememberLogA11y() = LogA11y(
    done = stringResource(R.string.log_a11y_task_done),
    failed = stringResource(R.string.log_a11y_task_failed),
    running = stringResource(R.string.log_a11y_task_running),
    interrupted = stringResource(R.string.log_a11y_task_interrupted),
    warnings = stringResource(R.string.log_a11y_warnings),
    expanded = stringResource(R.string.log_a11y_expanded),
    collapsed = stringResource(R.string.log_a11y_collapsed),
    expand = stringResource(R.string.common_expand),
    collapse = stringResource(R.string.common_collapse),
    viewDetails = stringResource(R.string.panel_log_view_details),
    warning = stringResource(R.string.common_warning),
    error = stringResource(R.string.log_a11y_error),
    starFormat = stringResource(R.string.log_a11y_star),
    recruitResult = stringResource(R.string.log_a11y_recruit_result),
)

/** 等级色只用于图标、底色与特殊徽章，正文保持高对比 */
private val PlainLevels = setOf(
    LogLevel.MESSAGE, LogLevel.INFO, LogLevel.TRACE,
    LogLevel.SUCCESS, LogLevel.WARNING, LogLevel.ERROR,
)

@Composable
private fun LogLine(
    row: LineRow,
    container: GroupColors,
    a11y: LogA11y,
    onOpen: (LogItem) -> Unit,
) {
    val item = row.item
    val scheme = MaterialTheme.colorScheme
    var textColor = scheme.onSurface
    var tint = Color.Transparent
    var icon: ImageVector? = null
    var iconColor = Color.Unspecified
    when (item.level) {
        LogLevel.TRACE -> textColor = scheme.onSurfaceVariant
        LogLevel.SUCCESS -> {
            icon = Icons.Rounded.CheckCircle
            iconColor = LogColorRole.SUCCESS.themedColor()
        }

        LogLevel.WARNING -> {
            icon = Icons.Rounded.Warning
            iconColor = LogColorRole.WARNING.themedColor()
            tint = iconColor.copy(alpha = 0.14f)
        }

        LogLevel.ERROR -> {
            textColor = scheme.error
            icon = Icons.Rounded.Error
            iconColor = scheme.error
            tint = scheme.error.copy(alpha = 0.10f)
        }

        else -> Unit
    }

    var overflow by remember { mutableStateOf(false) }
    val clickable = item.hasDetails || overflow

    Box(
        modifier = Modifier
            .groupBlock(first = row.first, last = row.last, colors = container)
            .padding(
                start = 6.dp,
                end = 6.dp,
                top = if (row.first) 6.dp else 0.dp,
                bottom = if (row.last) 6.dp else 0.dp,
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TintShape)
                .background(tint)
                .then(if (clickable) Modifier.clickable { onOpen(item) } else Modifier)
                .clearAndSetSemantics {
                    text = AnnotatedString(item.content)
                    // 描述只在读屏取语义时才拼
                    a11y.describe(item, row.star)?.let { contentDescription = it }
                    if (clickable) onClick(a11y.viewDetails) { onOpen(item); true }
                }
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = item.formattedTime,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                modifier = Modifier.alignByBaseline()
            )
            Spacer(modifier = Modifier.width(8.dp))
            val recruitTags = item.recruitTags
            val combos = item.recruitTooltip?.takeIf { it.isNotEmpty() }
            // 公招行已有徽标与标签块，不叠等级徽章
            if (item.level !in PlainLevels && combos == null && recruitTags == null) {
                LogLevelBadge(
                    level = item.level,
                    color = item.level.themedColor(),
                    compact = true,
                    modifier = Modifier.padding(top = 1.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            val contentModifier = Modifier
                .weight(1f)
                .alignByBaseline()
            when {
                recruitTags != null -> RecruitTagsBlock(recruitTags, row.star, textColor, contentModifier)
                combos != null -> RecruitResultPreview(combos, contentModifier)
                else -> Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    maxLines = LINE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { overflow = it.hasVisualOverflow },
                    modifier = contentModifier
                )
            }
            if (item.hasDetails) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(14.dp),
                    tint = scheme.onSurfaceVariant
                )
            }
        }
    }
}

private val TagShape = RoundedCornerShape(4.dp)

@Composable
private fun RecruitTagChip(tag: String, kind: RecruitTagsKind, star: Int) {
    val scheme = MaterialTheme.colorScheme
    if (kind == RecruitTagsKind.SPECIAL && star >= 6) {
        RainbowBadge(tag, TagShape)
        return
    }
    val (background, content) = when (kind) {
        RecruitTagsKind.DETECTED -> scheme.secondaryContainer to scheme.onSecondaryContainer
        RecruitTagsKind.SELECTED -> scheme.primary to scheme.onPrimary
        // 结果还没到时先按 5★ 显示
        RecruitTagsKind.SPECIAL -> starColor(5) to Color(0xDE000000)
        RecruitTagsKind.ROBOT -> LogColorRole.ROBOT.themedColor().copy(alpha = 0.2f) to scheme.onSurface
    }
    Text(
        text = tag,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (kind == RecruitTagsKind.SPECIAL) FontWeight.Bold else null,
        color = content,
        modifier = Modifier
            .clip(TagShape)
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun RecruitTagsBlock(tags: RecruitTagsLog, star: Int, labelColor: Color, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = tags.label,
            style = MaterialTheme.typography.bodySmall,
            color = labelColor
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tags.tags.forEach { tag -> RecruitTagChip(tag, tags.kind, star) }
        }
    }
}

@Composable
private fun RecruitResultPreview(combos: List<RecruitCombination>, modifier: Modifier) {
    val best = combos.maxBy { it.starLevel }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        StarBadge(best.starLevel)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "${best.tags.joinToString(" + ")}  →  ${best.opers.joinToString(" ") { it.name }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StarBadge(level: Int) {
    if (level >= 6) {
        RainbowBadge("$level★")
        return
    }
    val color = starColor(level)
    val solid = level >= 5
    Text(
        text = "$level★",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (solid) Color(0xDE000000) else color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (solid) color else color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun SummaryCard(group: LogGroup, container: GroupColors) {
    val head = group.lines.first()
    val (icon, accent) = when (head.level) {
        LogLevel.SUCCESS -> Icons.Rounded.CheckCircle to LogColorRole.SUCCESS.themedColor()
        LogLevel.ERROR -> Icons.Rounded.Cancel to MaterialTheme.colorScheme.error
        else -> Icons.Rounded.Info to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val text = remember(group) { group.lines.joinToString("\n") { it.content } }
    val title = text.substringBefore('\n')
    val body = text.substringAfter('\n', "")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = GroupGap)
            .clip(ShapeSingle)
            .background(container.fill)
            .background(accent.copy(alpha = 0.12f))
            .clearAndSetSemantics { this.text = AnnotatedString(text) }
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (body.isNotEmpty()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = head.formattedTime,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun LogDetailDialog(
    logItem: LogItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copiedText = stringResource(R.string.panel_log_copied)
    val (icon, iconTint) = detailIcon(logItem)
    val scheme = MaterialTheme.colorScheme

    AdaptiveTaskPromptDialog(
        visible = true,
        title = stringResource(R.string.log_detail_title),
        onConfirm = onDismiss,
        onDismissRequest = onDismiss,
        confirmText = stringResource(R.string.common_close),
        dismissText = null,
        icon = icon,
        iconTint = iconTint,
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = logItem.formattedTime,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        color = scheme.onSurfaceVariant
                    )
                    if (logItem.level !in PlainLevels) {
                        LogLevelBadge(level = logItem.level, color = logItem.level.themedColor(), compact = false)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(ClipData.newPlainText("log", logItem.toPlainText()).toClipEntry())
                            }
                            Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.common_copy),
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                SelectionContainer {
                    Text(
                        text = logItem.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (logItem.level == LogLevel.ERROR) scheme.error else scheme.onSurface
                    )
                }

                logItem.recruitTooltip?.let { combos ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        combos.forEach { RecruitComboCard(it) }
                    }
                }

                logItem.tooltip?.let { tip ->
                    SelectionContainer {
                        Text(
                            text = tip,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = scheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(scheme.surfaceVariant)
                                .padding(10.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun detailIcon(item: LogItem): Pair<ImageVector, Color> {
    val recruit = item.recruitTooltip
    return when {
        item.level == LogLevel.ERROR -> Icons.Rounded.Error to MaterialTheme.colorScheme.error
        item.level == LogLevel.WARNING -> Icons.Rounded.Warning to LogColorRole.WARNING.themedColor()
        item.level == LogLevel.SUCCESS -> Icons.Rounded.CheckCircle to LogColorRole.SUCCESS.themedColor()
        recruit != null -> Icons.Rounded.Star to starColor(recruit.maxOf { it.starLevel })
        else -> Icons.Rounded.Info to MaterialTheme.colorScheme.primary
    }
}

private val StarRoles = arrayOf(
    LogColorRole.STAR_1, LogColorRole.STAR_2, LogColorRole.STAR_3,
    LogColorRole.STAR_4, LogColorRole.STAR_5, LogColorRole.STAR_6,
)

@Composable
private fun starColor(star: Int): Color =
    StarRoles.getOrNull(star - 1)?.themedColor() ?: MaterialTheme.colorScheme.onSurfaceVariant

private val RainbowTextShadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 4f)

/** 白字加阴影，金/绿段上也看得清 */
@Composable
private fun RainbowBadge(text: String, shape: Shape = RoundedCornerShape(6.dp)) {
    val pageVisible = LocalPageVisible.current
    val phase = remember { Animatable(0f) }
    LaunchedEffect(pageVisible) {
        runRainbowFlow(phase) { pageVisible.value }
    }
    val span = with(LocalDensity.current) { 48.dp.toPx() }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(shadow = RainbowTextShadow),
        fontWeight = FontWeight.ExtraBold,
        color = Color.White,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .drawWithCache {
                val brush = Brush.linearGradient(
                    colorStops = RainbowStops,
                    start = Offset.Zero,
                    end = Offset(span, 0f),
                    tileMode = TileMode.Repeated,
                )
                onDrawBehind {
                    // phase 只在绘制阶段读，每帧只重绘
                    val shift = phase.value * span * 2f
                    translate(left = shift) {
                        drawRect(brush = brush, topLeft = Offset(-shift, 0f), size = size)
                    }
                }
            }
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun StarText(level: Int, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelMedium
    if (level >= 6) {
        RainbowFlowText(
            text = "$level★",
            style = style,
            maxLines = 1,
            span = with(LocalDensity.current) { 36.dp.toPx() },
            modifier = modifier,
        )
    } else {
        Text(
            text = "$level★",
            style = style,
            fontWeight = FontWeight.Bold,
            color = starColor(level),
            maxLines = 1,
            modifier = modifier,
        )
    }
}

@Composable
private fun RecruitComboCard(combo: RecruitCombination) {
    val scheme = MaterialTheme.colorScheme
    val starFormat = stringResource(R.string.log_a11y_star)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 弹窗带 tonalElevation，填充色会和底色撞，用描边分隔
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(10.dp))
            // 整卡读一次，「★」符号换成「N 星」
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append(starFormat.format(combo.starLevel)).append(", ")
                    append(combo.tags.joinToString(" + "))
                    combo.opers.groupBy { it.starLevel }.forEach { (star, opers) ->
                        append("; ").append(starFormat.format(star)).append(": ")
                        append(opers.joinToString(", ") { it.name })
                    }
                }
            }
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        StarBadge(combo.starLevel)
        Spacer(modifier = Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                combo.tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = scheme.primaryContainer.copy(alpha = 0.6f),
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            combo.opers.groupBy { it.starLevel }.forEach { (star, opers) ->
                Row {
                    StarText(
                        level = star,
                        modifier = Modifier
                            .width(28.dp)
                            .alignByBaseline()
                    )
                    Text(
                        text = opers.joinToString("  ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface,
                        modifier = Modifier.alignByBaseline()
                    )
                }
            }
        }
    }
}

private fun LogItem.toPlainText(): String = buildString {
    append('[').append(formattedTime).append("] ").append(content)
    recruitTooltip?.forEach { combo ->
        append('\n').append(combo.starLevel).append("★ ").append(combo.tags.joinToString(" "))
        append(": ").append(combo.opers.joinToString(" ") { "${it.name}(${it.starLevel}★)" })
    }
    tooltip?.let { append('\n').append(it) }
}

/** [compact] 切换列表行 / 详情弹窗两档尺寸。 */
@Composable
private fun LogLevelBadge(
    level: LogLevel,
    color: Color,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(if (compact) 3.dp else 4.dp),
        color = color.copy(alpha = 0.15f),
        modifier = modifier
    ) {
        Text(
            text = stringResource(level.labelRes),
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

