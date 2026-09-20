package com.aliothmoon.maameow.presentation.components

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.theme.MaaMeowTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val HOUR_COUNT = 24
private const val HOURS_PER_PERIOD = 12
private const val MINUTE_COUNT = 60

/** 循环滚轮的虚拟圈数，起点放中间，两头实际滚不到 */
private const val LOOP_CYCLES = 1000

/** 离中心越远越淡 */
private const val FADE_DEPTH = 0.65f

/** 离中心越远越小 */
private const val SHRINK_DEPTH = 0.24f

/** 向中心收拢的比例，模拟滚筒透视 */
private const val GATHER_RATIO = 0.09f

private val PICKER_MAX_WIDTH = 304.dp

/** 24 小时制没有时段列，收窄一点保持时分列的疏密 */
private val WHEELS_ONLY_MAX_WIDTH = 248.dp

/**
 * [hour] 恒为 24 小时制，[is24Hour] 只决定滚轮怎么显示
 *
 * 构造参数只作为初始值，之后由滚轮与时段选择驱动；换制式要换实例
 */
@Stable
class WheelTimePickerState(
    initialHour: Int,
    initialMinute: Int,
    val is24Hour: Boolean = false,
) {
    internal val startHour = initialHour.coerceIn(0, HOUR_COUNT - 1)
    internal val startMinute = initialMinute.coerceIn(0, MINUTE_COUNT - 1)

    internal val hourCount = if (is24Hour) HOUR_COUNT else HOURS_PER_PERIOD

    internal val firstHourValue = if (is24Hour) 0 else 1

    internal val startHourIndex =
        if (is24Hour) startHour else (startHour + HOURS_PER_PERIOD - 1) % HOURS_PER_PERIOD

    var hour by mutableIntStateOf(startHour)
        internal set

    var minute by mutableIntStateOf(startMinute)
        internal set

    val isPm: Boolean
        get() = hour >= HOURS_PER_PERIOD

    internal fun selectHourIndex(index: Int) {
        if (is24Hour) {
            hour = index.coerceIn(0, HOUR_COUNT - 1)
        } else {
            selectHour(index + 1)
        }
    }

    internal fun selectHour(hourOfPeriod: Int) {
        require(hourOfPeriod in 1..HOURS_PER_PERIOD)
        hour = hourOfPeriod % HOURS_PER_PERIOD + if (isPm) HOURS_PER_PERIOD else 0
    }

    internal fun selectPeriod(pm: Boolean) {
        hour = hour % HOURS_PER_PERIOD + if (pm) HOURS_PER_PERIOD else 0
    }
}


@Composable
fun rememberWheelTimePickerState(
    initialHour: Int,
    initialMinute: Int,
    is24Hour: Boolean = false,
): WheelTimePickerState {
    val previous = remember { arrayOfNulls<WheelTimePickerState>(1) }
    return remember(is24Hour) {
        val last = previous[0]
        Snapshot.withoutReadObservation {
            WheelTimePickerState(
                last?.hour ?: initialHour,
                last?.minute ?: initialMinute,
                is24Hour,
            )
        }.also { previous[0] = it }
    }
}

/**
 * 自绘时分滚轮，用来替代 M3 的 TimePicker / TimeInput
 *
 * M3 表盘与键盘输入在不同 Android 版本上表现不一致，这里只依赖 foundation 的滚动与吸附
 *
 * [rows] 需为奇数，偶数会被下调一格
 */
@Composable
fun WheelTimePicker(
    state: WheelTimePickerState,
    modifier: Modifier = Modifier,
    rows: Int = 5,
    itemHeight: Dp = 48.dp,
) {
    val visibleRows = rows.coerceAtLeast(3).let { if (it % 2 == 0) it - 1 else it }
    val colors = MaterialTheme.colorScheme
    val selectionShape = RoundedCornerShape(MaaDesignTokens.CornerRadius.card)
    val digitStyle = MaterialTheme.typography.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        // 等宽数字，滚动时字形不会左右抖
        fontFeatureSettings = "tnum",
    )

    Box(
        modifier = modifier.height(itemHeight * visibleRows),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = if (state.is24Hour) WHEELS_ONLY_MAX_WIDTH else PICKER_MAX_WIDTH)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .clip(selectionShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        colors.primaryContainer.copy(alpha = 0.65f),
                                        colors.primaryContainer,
                                        colors.primaryContainer.copy(alpha = 0.65f),
                                    )
                                )
                            )
                            .border(1.dp, colors.primary.copy(alpha = 0.12f), selectionShape)
                    )
                }
                // 换制式要把两列连同 LazyListState 一起重建，否则停在旧下标
                key(state.is24Hour) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaaDesignTokens.Spacing.md)
                            .fadeVerticalEdges(edgeFraction = 0.75f / visibleRows),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WheelColumn(
                            count = state.hourCount,
                            initialIndex = state.startHourIndex,
                            onSelect = { state.selectHourIndex(it) },
                            rows = visibleRows,
                            itemHeight = itemHeight,
                            digitStyle = digitStyle,
                            modifier = Modifier.weight(1f),
                            firstValue = state.firstHourValue,
                        )
                        Column(
                            modifier = Modifier.width(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            repeat(2) {
                                Box(
                                    Modifier
                                        .size(4.dp)
                                        .background(colors.primary.copy(alpha = 0.7f), CircleShape)
                                )
                            }
                        }
                        WheelColumn(
                            count = MINUTE_COUNT,
                            initialIndex = state.startMinute,
                            onSelect = { state.minute = it },
                            rows = visibleRows,
                            itemHeight = itemHeight,
                            digitStyle = digitStyle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (!state.is24Hour) PeriodSelector(state)
        }
    }
}

@Composable
fun WheelTimeFormatToggle(
    is24Hour: Boolean,
    onFormatChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.xs),
    ) {
        listOf(false, true).forEach { target ->
            SegmentCell(
                selected = is24Hour == target,
                text = stringResource(
                    if (target) R.string.time_picker_format_24h
                    else R.string.time_picker_format_12h
                ),
                onSelect = { onFormatChange(target) },
                textStyle = MaterialTheme.typography.labelLarge,
                modifier = Modifier.size(width = 42.dp, height = 32.dp),
            )
        }
    }
}

@Composable
private fun PeriodSelector(state: WheelTimePickerState) {
    Column(
        modifier = Modifier
            .width(56.dp)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.xs),
    ) {
        listOf(false, true).forEach { pm ->
            SegmentCell(
                selected = state.isPm == pm,
                text = stringResource(if (pm) R.string.time_picker_pm else R.string.time_picker_am),
                onSelect = { state.selectPeriod(pm) },
                textStyle = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            )
        }
    }
}

@Composable
private fun SegmentCell(
    selected: Boolean,
    text: String,
    onSelect: () -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(MaaDesignTokens.CornerRadius.button)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.primaryContainer else colors.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = if (selected) colors.primary.copy(alpha = 0.3f) else colors.outlineVariant,
                shape = shape,
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = {
                    if (!selected) {
                        onSelect()
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = textStyle,
            color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 单列循环滚轮，虚拟项按 [count] 取模，首尾相接 */
@Composable
private fun WheelColumn(
    count: Int,
    initialIndex: Int,
    onSelect: (Int) -> Unit,
    rows: Int,
    itemHeight: Dp,
    digitStyle: TextStyle,
    modifier: Modifier = Modifier,
    firstValue: Int = 0,
) {
    val startIndex = count * (LOOP_CYCLES / 2) + initialIndex
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val edgeRows = (rows / 2).toFloat()

    // 取中心线最近的一项，坐标系与 viewport 一致，不受内边距口径影响
    val centered by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
        }
    }

    // 滚动过程中持续上报，避免未停稳就点确定拿到旧值
    LaunchedEffect(listState) {
        var first = true
        snapshotFlow { centered }
            .filterNotNull()
            .map { it % count }
            .distinctUntilChanged()
            .collect { index ->
                currentOnSelect(index)
                if (!first) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                first = false
            }
    }

    LazyColumn(
        modifier = modifier
            .height(itemHeight * rows),
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        contentPadding = PaddingValues(vertical = itemHeight * (rows / 2)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(count * LOOP_CYCLES, key = { it }) { index ->
            val isSelected = (centered ?: startIndex) == index
            val digitColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tween(durationMillis = 120),
                label = "wheelDigitColor",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .semantics { selected = isSelected }
                    // 变形放在绘制阶段按像素距离连续插值，不触发重组
                    .graphicsLayer {
                        val info = listState.layoutInfo
                        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                        // 以行为单位的有符号距离，布局未完成时按初始位置估算
                        val offsetRows = if (item != null && item.size > 0) {
                            val viewCenter =
                                (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            (item.offset + item.size / 2f - viewCenter) / item.size
                        } else {
                            (index - startIndex).toFloat()
                        }
                        val depth = (abs(offsetRows) / edgeRows).coerceIn(0f, 1f)
                        alpha = 1f - FADE_DEPTH * depth
                        val shrink = 1f - SHRINK_DEPTH * depth
                        scaleX = shrink
                        scaleY = shrink
                        translationY = -offsetRows * itemHeight.toPx() * GATHER_RATIO
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { scope.launch { listState.animateScrollToItem(index) } },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (index % count + firstValue).toString().padStart(2, '0'),
                    style = digitStyle,
                    color = digitColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun WheelTimePickerPreview() {
    MaaMeowTheme(useSystemMonetColor = false) {
        Surface {
            WheelTimePicker(
                state = rememberWheelTimePickerState(9, 41),
                modifier = Modifier.padding(MaaDesignTokens.Spacing.xxl),
            )
        }
    }
}

@Preview(name = "24h", showBackground = true)
@Preview(name = "24h Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun Hour24WheelTimePickerPreview() {
    MaaMeowTheme(useSystemMonetColor = false) {
        Surface {
            Column(
                modifier = Modifier.padding(MaaDesignTokens.Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WheelTimeFormatToggle(is24Hour = true, onFormatChange = {})
                WheelTimePicker(state = rememberWheelTimePickerState(21, 5, is24Hour = true))
            }
        }
    }
}

@Preview(name = "Compact", showBackground = true)
@Composable
private fun CompactWheelTimePickerPreview() {
    MaaMeowTheme(useSystemMonetColor = false) {
        Surface {
            WheelTimePicker(
                state = rememberWheelTimePickerState(23, 59),
                rows = 3,
                modifier = Modifier.padding(MaaDesignTokens.Spacing.xxl),
            )
        }
    }
}

/** 上下边缘做真 alpha 遮罩淡出，与所在背景色无关 */
private fun Modifier.fadeVerticalEdges(edgeFraction: Float): Modifier {
    val edge = edgeFraction.coerceIn(0f, 0.5f)
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    edge to Color.Black,
                    1f - edge to Color.Black,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}
