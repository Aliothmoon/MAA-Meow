package com.aliothmoon.maameow.presentation.view.panel.depot

import androidx.compose.animation.core.animate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.DepotMaintainPlan
import com.aliothmoon.maameow.data.model.DepotPlanOutcome
import com.aliothmoon.maameow.data.model.LogColorRole
import com.aliothmoon.maameow.data.resource.ItemIconLoader
import com.aliothmoon.maameow.data.resource.StageGroup
import com.aliothmoon.maameow.domain.models.DualChipConversion
import com.aliothmoon.maameow.presentation.components.ItemIcon
import com.aliothmoon.maameow.presentation.view.panel.common.stageDisplayName
import com.aliothmoon.maameow.theme.LocalReduceMotion
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.theme.MaaMotion
import com.aliothmoon.maameow.theme.themedColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import kotlin.math.roundToInt

/** 排序列表单行数据；[key] 按物品与出现次序生成，拖动重排时保持组合复用 */
internal data class DepotPlanOrderEntry(
    val key: String,
    val no: Int,
    val stage: String,
    val itemId: String,
    val itemName: String,
    val current: Int,
    val dualChipExtra: Int = 0,
    val target: Int,
    val need: Int,
    val outcome: DepotPlanOutcome,
)

/** 将库存保持计划转为可排序概览行；[inventory] 传原始快照，开启 [countDualChips] 时自动折算双芯片 */
internal fun depotPlanOrderEntries(
    plans: List<DepotMaintainPlan>,
    outcomes: List<DepotPlanOutcome>,
    inventory: Map<String, Int>,
    itemNameMap: Map<String, String>,
    notSelectedLabel: String,
    countDualChips: Boolean = false,
): List<DepotPlanOrderEntry> {
    val seen = mutableMapOf<String, Int>()
    return plans.mapIndexed { index, plan ->
        val idKey = plan.dropId.ifEmpty { "empty" }
        val occurrence = seen[idKey] ?: 0
        seen[idKey] = occurrence + 1
        val raw = inventory[plan.dropId] ?: 0
        val extra = if (countDualChips) {
            DualChipConversion.extra(plan.dropId) { inventory[it] ?: 0 }
        } else {
            0
        }
        val current = raw + extra
        DepotPlanOrderEntry(
            key = "plan-$idKey-$occurrence",
            no = index + 1,
            stage = plan.stage,
            itemId = plan.dropId,
            itemName = itemNameMap[plan.dropId] ?: notSelectedLabel,
            current = current,
            dualChipExtra = extra,
            target = plan.dropCount,
            need = (plan.dropCount - current).coerceAtLeast(0),
            outcome = outcomes[index],
        )
    }
}

/** 单条计划去向文案，列表短标签与编辑页详述共用 */
@Composable
internal fun depotPlanOutcomeLabel(
    outcome: DepotPlanOutcome,
    need: Int,
    stage: String,
    short: Boolean = false,
): String = when (outcome) {
    DepotPlanOutcome.NoItem -> stringResource(R.string.depot_inventory_maintain_no_item)
    DepotPlanOutcome.ZeroTarget -> stringResource(R.string.depot_inventory_maintain_zero_target)
    DepotPlanOutcome.StageRequired ->
        stringResource(R.string.depot_inventory_maintain_stage_required)

    DepotPlanOutcome.StageClosed -> if (short) {
        stringResource(R.string.depot_inventory_order_stage_closed)
    } else {
        stringResource(R.string.depot_inventory_maintain_stage_closed, stage)
    }

    DepotPlanOutcome.Enough -> stringResource(R.string.depot_inventory_maintain_enough)

    DepotPlanOutcome.Runnable -> if (short) {
        ""
    } else {
        stringResource(R.string.depot_inventory_maintain_need, need)
    }
}

/** 计划概览单行：合并为单个无障碍语义节点，避免滚动时重排大量子节点 */
@Composable
internal fun ReorderableCollectionItemScope.DepotPlanOrderRow(
    entry: DepotPlanOrderEntry,
    isDragging: Boolean,
    synced: Boolean,
    stageGroups: List<StageGroup>,
    iconLoader: ItemIconLoader,
    onClick: (() -> Unit)?,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit = {},
    onDeleteAction: (() -> Unit)? = null,
) {
    val muted = entry.outcome != DepotPlanOutcome.Runnable && entry.outcome != DepotPlanOutcome.Enough
    val titleColor = if (muted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val successColor = LogColorRole.SUCCESS.themedColor()
    val (badgeContainerColor, badgeContentColor) = when (entry.outcome) {
        DepotPlanOutcome.Enough -> successColor.copy(alpha = 0.14f) to successColor
        DepotPlanOutcome.StageClosed -> depotWarningColors()
        else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    val progressText = depotProgressText(
        current = entry.current,
        target = entry.target,
        synced = synced,
        dualChipExtra = entry.dualChipExtra,
    )
    val stageLabel = if (entry.stage.isNotBlank()) {
        stageDisplayName(entry.stage, stageGroups, emptyLabel = "")
    } else {
        ""
    }
    val subtitleText = if (stageLabel.isNotEmpty()) "$stageLabel · $progressText" else progressText
    val outcomeLabel = depotPlanOutcomeLabel(
        outcome = entry.outcome,
        need = entry.need,
        stage = "",
        short = true,
    )
    val removeLabel = stringResource(R.string.depot_inventory_plan_remove)
    val rowDescription = buildString {
        append('#')
        append(entry.no)
        append(' ')
        append(entry.itemName)
        append(' ')
        append(subtitleText)
        if (outcomeLabel.isNotEmpty()) {
            append(' ')
            append(outcomeLabel)
        }
    }

    Surface(
        shape = RoundedCornerShape(MaaDesignTokens.CornerRadius.inner),
        color = if (isDragging) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (isDragging) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        shadowElevation = if (isDragging) 4.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = rowDescription
                if (onClick != null) {
                    role = Role.Button
                    onClick {
                        onClick()
                        true
                    }
                }
                if (onDeleteAction != null) {
                    customActions = listOf(
                        CustomAccessibilityAction(removeLabel) {
                            onDeleteAction()
                            true
                        },
                    )
                }
            }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = MaaDesignTokens.Spacing.sm, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .draggableHandle(
                        onDragStarted = { onDragStarted() },
                        onDragStopped = onDragStopped,
                    )
                    .size(width = 24.dp, height = 36.dp)
                    .padding(vertical = 8.dp),
            )
            ItemIcon(
                itemId = entry.itemId,
                modifier = Modifier.size(32.dp),
                contentDescription = null,
                loader = iconLoader,
            )
            Spacer(modifier = Modifier.width(MaaDesignTokens.Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "#${entry.no}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        text = entry.itemName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (outcomeLabel.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeContainerColor,
                    contentColor = badgeContentColor,
                    modifier = Modifier.widthIn(max = 104.dp),
                ) {
                    Text(
                        text = outcomeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** 左滑删除展开状态，跨重组与列表重排按行键跟踪 */
internal class DepotPlanSwipeState(
    private val scope: CoroutineScope,
    private val reduceMotion: Boolean,
) {
    var revealedKey by mutableStateOf<Any?>(null)
        private set
    var revealWidth by mutableFloatStateOf(0f)
        private set
    private var settleJob: Job? = null

    fun settleTo(target: Float, animated: Boolean) {
        settleJob?.cancel()
        if (!animated) {
            revealWidth = target
            if (target <= 0f) revealedKey = null
            return
        }
        settleJob = scope.launch {
            animate(
                initialValue = revealWidth,
                targetValue = target,
                animationSpec = MaaMotion.spec(reduceMotion, MaaMotion.Fast),
            ) { value, _ -> revealWidth = value }
            if (target <= 0f) revealedKey = null
        }
    }

    fun collapse(animated: Boolean) = settleTo(0f, animated)

    fun startDrag(key: Any) {
        settleJob?.cancel()
        if (revealedKey != key) {
            revealedKey = key
            revealWidth = 0f
        }
    }

    fun updateDrag(delta: Float, maxPx: Float) {
        revealWidth = (revealWidth - delta).coerceIn(0f, maxPx)
    }

    fun endDrag(maxPx: Float) {
        settleTo(
            target = if (revealWidth > maxPx / 2f) maxPx else 0f,
            animated = true,
        )
    }
}

@Composable
internal fun rememberDepotPlanSwipeState(): DepotPlanSwipeState {
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    return remember(scope, reduceMotion) { DepotPlanSwipeState(scope, reduceMotion) }
}

/** 携带稳定 LazyColumn 键的计划概览项，避免外置键表与列表长度异步错位 */
internal data class KeyedDepotPlanOrderEntry(
    val key: Any,
    val entry: DepotPlanOrderEntry,
)

/** 挂载到 LazyColumn 的可拖动排序 + 左滑删除计划行，拖至视口边缘自动滚动 */
internal fun LazyListScope.depotPlanOrderItems(
    items: List<KeyedDepotPlanOrderEntry>,
    reorderableState: ReorderableLazyListState,
    swipeState: DepotPlanSwipeState,
    synced: Boolean,
    stageGroups: List<StageGroup>,
    iconLoader: ItemIconLoader,
    onDragStopped: () -> Unit,
    onClick: ((Int) -> Unit)? = null,
    onDelete: ((Int) -> Unit)? = null,
) {
    itemsIndexed(
        items = items,
        key = { _, item -> item.key },
        contentType = { _, _ -> "depot-plan-row" },
    ) { index, item ->
        val haptic = LocalHapticFeedback.current
        val actionWidthPx = with(LocalDensity.current) { RevealActionWidth.toPx() }
        val rowKey = item.key
        val entry = item.entry

        ReorderableItem(reorderableState, key = rowKey) { isDragging ->
            val isRowRevealed = swipeState.revealedKey == rowKey && swipeState.revealWidth > 0.5f
            val deleteAt: (() -> Unit)? = onDelete?.let { del ->
                {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    swipeState.collapse(animated = false)
                    del(index)
                }
            }
            val rowOnClick: (() -> Unit)? = when {
                // 划开删除钮时点该行优先收起，避免子 Surface.clickable 截走点击直接进编辑
                isRowRevealed -> {
                    { swipeState.collapse(animated = true) }
                }

                onClick != null -> {
                    { onClick(index) }
                }

                else -> null
            }
            val row: @Composable () -> Unit = {
                DepotPlanOrderRow(
                    entry = entry,
                    isDragging = isDragging,
                    synced = synced,
                    stageGroups = stageGroups,
                    iconLoader = iconLoader,
                    onClick = rowOnClick,
                    onDragStarted = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        swipeState.collapse(animated = false)
                    },
                    onDragStopped = onDragStopped,
                    onDeleteAction = deleteAt,
                )
            }
            if (deleteAt == null) {
                row()
            } else {
                SwipeRevealDeleteRow(
                    revealWidth = if (swipeState.revealedKey == rowKey) swipeState.revealWidth else 0f,
                    actionWidthPx = actionWidthPx,
                    onDragStart = { swipeState.startDrag(rowKey) },
                    onDrag = { delta -> swipeState.updateDrag(delta, actionWidthPx) },
                    onDragEnd = { swipeState.endDrag(actionWidthPx) },
                    onDelete = deleteAt,
                    content = row,
                )
            }
        }
    }
}

/** 左滑露出独立圆角删除按钮的行容器，完全展开后才可点删 */
@Composable
private fun SwipeRevealDeleteRow(
    revealWidth: Float,
    actionWidthPx: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val currentRevealWidth by rememberUpdatedState(revealWidth)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val fullyOpen = revealWidth >= actionWidthPx - 0.5f
    val revealed = revealWidth > 0.5f

    Box(modifier = Modifier.fillMaxWidth()) {
        if (revealed) {
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(with(LocalDensity.current) { revealWidth.toDp() })
                        .padding(horizontal = RevealActionInset),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(MaaDesignTokens.CornerRadius.inner))
                            .background(MaterialTheme.colorScheme.error)
                            .clickable(enabled = fullyOpen, onClick = { currentOnDelete() }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.depot_inventory_plan_remove),
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(RevealIconSize),
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(-revealWidth.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { currentOnDragStart() },
                        onDragEnd = { currentOnDragEnd() },
                        onDragCancel = { currentOnDragEnd() },
                        onHorizontalDrag = { change, dragAmount ->
                            // 未展开时右滑不消费，避免无故截断外层翻页手势
                            if (currentRevealWidth > 0f || dragAmount < 0f) {
                                change.consume()
                                currentOnDrag(dragAmount)
                            }
                        },
                    )
                },
        ) {
            content()
        }
    }
}

private val RevealActionWidth = 72.dp
private val RevealActionInset = MaaDesignTokens.Spacing.xs
private val RevealIconSize = 20.dp
