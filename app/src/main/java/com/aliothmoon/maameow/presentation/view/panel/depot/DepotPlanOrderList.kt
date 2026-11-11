package com.aliothmoon.maameow.presentation.view.panel.depot

import androidx.compose.animation.core.animate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.DepotMaintainPlan
import com.aliothmoon.maameow.data.model.DepotPlanOutcome
import com.aliothmoon.maameow.data.model.LogColorRole
import com.aliothmoon.maameow.data.resource.ItemIconLoader
import com.aliothmoon.maameow.presentation.components.ItemIcon
import com.aliothmoon.maameow.presentation.viewmodel.depotProgressText
import com.aliothmoon.maameow.theme.LocalReduceMotion
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.theme.MaaMotion
import com.aliothmoon.maameow.theme.themedColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableListItemScope

/**
 * 可拖动排序的计划列表：库存数据页的「刷取顺序」和后台任务的库存保持计划概览共用这一份。
 *
 * 两边的数据模型不一样（一边是整档所有节点的 [com.aliothmoon.maameow.presentation.viewmodel.DepotMaintainPlanUi]、
 * 一边是单个节点的 [DepotMaintainPlan]），各自映射成 [DepotPlanOrderEntry] 再交给同一个列表渲染 ——
 * 行的样子、进度口径、去向文案、拖动行为都只有一份实现，改一处两边一起变。
 */

/**
 * 排序列表里的一行。
 *
 * [key] 拖动期间用来认住这一组组合：重排时让它整体挪过去而不是重建，
 * 重建的话 [ItemIcon] 要重新走一遍取图，图标闪一下。
 * [no] 是列表里显示的序号，1 起。
 */
internal data class DepotPlanOrderEntry(
    val key: String,
    val no: Int,
    val itemId: String,
    val itemName: String,
    val current: Int,
    val target: Int,
    val need: Int,
    val outcome: DepotPlanOutcome,
)

/**
 * 后台任务那边的计划 → 排序列表的行。
 *
 * [outcomes] 与 [plans] 同序且等长，由调用方算好（同一次重组里只算一遍）。
 * 未选物品的计划也照样列出来 —— 那种计划执行侧会跳过，但用户得能在这里看到它、拖动它，
 * 不然它会在列表里凭空少一行，序号和下面的计划格子对不上。
 */
internal fun depotPlanOrderEntries(
    plans: List<DepotMaintainPlan>,
    outcomes: List<DepotPlanOutcome>,
    inventory: Map<String, Int>,
    itemNameMap: Map<String, String>,
    notSelectedLabel: String,
): List<DepotPlanOrderEntry> = plans.mapIndexed { index, plan ->
    val current = inventory[plan.dropId] ?: 0
    DepotPlanOrderEntry(
        key = "plan-order-$index",
        no = index + 1,
        itemId = plan.dropId,
        itemName = itemNameMap[plan.dropId] ?: notSelectedLabel,
        current = current,
        target = plan.dropCount,
        need = (plan.dropCount - current).coerceAtLeast(0),
        outcome = outcomes[index],
    )
}

/**
 * 一条计划的去向文案：会去刷 → 缺多少；已集齐 → 已够；其余是配了也跑不起来的原因。
 *
 * 库存数据页的列表、面板提示，后台任务的计划概览，走的都是这一份 —— 同一个状态在两处
 * 不能一个说「未选材料」另一个说「未选物品」。
 *
 * @param short 列表里的窄格子用短标签（「关卡未开放」）；面板里带上关卡号，
 *   省得用户再回去对是哪一关。[DepotPlanOutcome.Runnable] 在短标签下返回空串：
 *   行左边已经写着「当前 / 目标」，再标一次「缺 3」是把同一个差值说两遍
 */
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

    // 概览行里不写「缺多少」：左边已经有「30 / 33」，再标一次「缺 3」是重复信息
    DepotPlanOutcome.Runnable -> if (short) {
        ""
    } else {
        stringResource(R.string.depot_inventory_maintain_need, need)
    }
}

/**
 * 排序列表里的一行：序号 + 图标 + 名称/进度 + 去向，最右是拖拽手柄。
 *
 * 只有手柄能拖；行的其余区域在 [onClick] 非空时保持点击语义。
 * 已集齐的执行时会被跳过，标绿；未集齐的才是真要去刷的，用正常前景色；
 * 其余是「配了也跑不起来」，按错误色标出来。
 */
@Composable
internal fun ReorderableListItemScope.DepotPlanOrderRow(
    entry: DepotPlanOrderEntry,
    isDragging: Boolean,
    synced: Boolean,
    iconLoader: ItemIconLoader,
    onClick: (() -> Unit)?,
    onDragStarted: () -> Unit,
) {
    val accent = when (entry.outcome) {
        DepotPlanOutcome.Enough -> LogColorRole.SUCCESS.themedColor()
        DepotPlanOutcome.Runnable -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.error
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
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = MaaDesignTokens.Spacing.sm, end = 2.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.sm),
        ) {
            Text(
                text = "${entry.no}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                // 只给下限：定死 18dp 时三位数会折行，把整行撑高
                modifier = Modifier.widthIn(min = 18.dp),
            )
            ItemIcon(
                itemId = entry.itemId,
                modifier = Modifier.size(32.dp),
                contentDescription = entry.itemName,
                loader = iconLoader,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.itemName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = depotProgressText(entry.current, entry.target, synced),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val outcomeLabel = depotPlanOutcomeLabel(
                outcome = entry.outcome,
                need = entry.need,
                stage = "",
                short = true,
            )
            if (outcomeLabel.isNotEmpty()) {
                Text(
                    text = outcomeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(max = 104.dp),
                )
            }
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = stringResource(R.string.depot_inventory_order_drag),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(32.dp)
                    .draggableHandle(onDragStarted = { onDragStarted() })
                    .padding(6.dp),
            )
        }
    }
}

/**
 * 一列可拖动排序的计划行。
 *
 * 用 ReorderableColumn 而不是 Lazy 那一版：Lazy 版要求每滑过一格就把数据重排一次，
 * 一次拖动能把整页重组几十遍（图标也跟着重开取图），滑起来就是一卡一卡的。
 * 这一版拖动期间只动它自己内部的 offset，而且是画的时候才读（不走重组），
 * 等到落手、位移动画播完，才回调一次 [onMove]。后台任务列表用的也是它。
 *
 * @param onMove 松手后回调：这一行从 [from] 挪到了 [to]，调用方按这个顺序写回自己的数据
 * @param onClick 行被点击，参数是这一行在 [entries] 里的下标；null = 这一列只做拖动排序，
 *   不响应点击。给下标而不是给 [DepotPlanOrderEntry]，是因为调用方手里的原始数据
 *   （计划对象）没进 entry，按下标回查比让 entry 再带一份引用干净
 * @param onDelete 向左划开一行、点露出来的删除按钮，参数是这一行的下标；null = 这一列划不开
 *   （库存数据页的「刷取顺序」跨多个节点，删除要连带节点一起定位，暂时不开）
 */
@Composable
internal fun DepotPlanOrderColumn(
    entries: List<DepotPlanOrderEntry>,
    synced: Boolean,
    iconLoader: ItemIconLoader,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    onClick: ((Int) -> Unit)? = null,
    onDelete: ((Int) -> Unit)? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(MaaDesignTokens.Spacing.sm),
) {
    val haptic = LocalHapticFeedback.current
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val actionWidthPx = with(LocalDensity.current) { RevealActionWidth.toPx() }

    // 露出来的删除按钮归整列所有，不记在行里：一次只可能露一条，而状态要是记在行上，
    // 划掉一行之后下标整体前移，接手的那一行会顶着别人剩下的位移出现 ——
    // 组合位置是按下标复用的，行内那个动画值会跟着位置一起被继承
    var revealedIndex by remember { mutableStateOf<Int?>(null) }
    // 露出的宽度（像素，正值）。拖动时由手指直接写，松手后交给 settleTo 收敛
    var revealWidth by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    // 把露出的宽度收到 target。animated = false 时立刻到位（开始拖动排序、划掉那一条）：
    // 这两种情况下这一行马上要干别的，等一段动画反而会跟别的位移叠在一起。
    // 收起动画期间 revealedIndex 一直留着 —— 这一行还得继续画那条带子，播完才摘
    fun settleTo(target: Float, animated: Boolean) {
        settleJob?.cancel()
        if (!animated) {
            revealWidth = target
            if (target <= 0f) revealedIndex = null
            return
        }
        settleJob = scope.launch {
            animate(
                initialValue = revealWidth,
                targetValue = target,
                animationSpec = MaaMotion.spec(reduceMotion, MaaMotion.Fast),
            ) { value, _ -> revealWidth = value }
            if (target <= 0f) revealedIndex = null
        }
    }

    // 列表短到露出来的那条已经不在了（清空、删到不够下标）就一并收掉：
    // 留着的话，之后再添够行数，那一行会莫名其妙带着删除按钮冒出来
    LaunchedEffect(entries.size) {
        val index = revealedIndex
        if (index != null && index >= entries.size) {
            settleJob?.cancel()
            revealedIndex = null
            revealWidth = 0f
        }
    }

    ReorderableColumn(
        list = entries,
        onSettle = onMove,
        modifier = modifier,
        verticalArrangement = verticalArrangement,
    ) { index, entry, isDragging ->
        key(entry.key) {
            ReorderableItem {
                val row: @Composable () -> Unit = {
                    DepotPlanOrderRow(
                        entry = entry,
                        isDragging = isDragging,
                        synced = synced,
                        iconLoader = iconLoader,
                        onClick = onClick?.let { callback -> { callback(index) } },
                        onDragStarted = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            // 开始拖动排序就把删除按钮收回去，且不播动画：这一行接下来跟着
                            // 手指走，留着半个身位的横向偏移会和拖动位移叠在一起
                            settleJob?.cancel()
                            revealedIndex = null
                            revealWidth = 0f
                        },
                    )
                }
                if (onDelete == null) {
                    row()
                } else {
                    SwipeRevealDeleteRow(
                        revealWidth = if (revealedIndex == index) revealWidth else 0f,
                        actionWidthPx = actionWidthPx,
                        onDragStart = {
                            settleJob?.cancel()
                            // 露着的是别的行就先收掉：这一行从头开始跟手
                            if (revealedIndex != index) {
                                revealedIndex = index
                                revealWidth = 0f
                            }
                        },
                        onDrag = { delta ->
                            // 往左划 = delta 为负 = 露出的宽度变大
                            revealWidth = (revealWidth - delta).coerceIn(0f, actionWidthPx)
                        },
                        onDragEnd = {
                            // 划过小半行就停在露出的位置，不够就弹回去
                            settleTo(
                                target = if (revealWidth > actionWidthPx / 2f) actionWidthPx else 0f,
                                animated = true,
                            )
                        },
                        onCollapse = { settleTo(target = 0f, animated = true) },
                        onDelete = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            // 先收回去再删：这一格马上会被下一行接手
                            settleJob?.cancel()
                            revealedIndex = null
                            revealWidth = 0f
                            onDelete(index)
                        },
                        content = row,
                    )
                }
            }
        }
    }
}

/**
 * 向左划开一行，右端露出一个删除按钮 —— 和库存数据页「清空配置档」那张卡片
 * （见 `SwipeRevealProfileCard`）是同一套手势：跟手划开、松手过小半行就停住、
 * 不够则弹回去，点那个红块才真的删。
 *
 * 位移由调用方给（[revealWidth]），这一格自己不记：划掉一行之后下标整体前移，
 * 组合位置按下标复用，位移要是记在这一格里，顶上来的一行会带着别人的位移出现。
 * 用 material3 的 SwipeToDismissBox 也是栽在同一个地方，另外它的 confirmValueChange
 * 在一次拖动里会被反复回调（实测一次划动删掉三条计划），拿它当删除的触发点不行。
 *
 * 按钮只在整条完全露出来之后才可点：收回动画正在播的时候它还是可见的，
 * 那时候要是还能点，用户看着它缩回去却把行删了。
 *
 * 红块左右留 [RevealActionInset]、四角用 [MaaDesignTokens.CornerRadius.inner]，
 * 和行卡片同一套圆角、同一个高度。贴着行的右边缘只圆靠右那两个角不行：行的右上角也是圆的，
 * 两个圆角在接缝处各自往里让，会裂出一块背景色。
 *
 * @param onCollapse 露出状态下点了这一行：收回去（这时候点进来多半是想改主意）
 */
@Composable
private fun SwipeRevealDeleteRow(
    revealWidth: Float,
    actionWidthPx: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onCollapse: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    // 手势块只装一次，里面闭包捕获到的会一直是最初那一版 lambda：
    // 删除要按当前下标取计划、划开要写当前这一列的状态，拿旧的会删错一条
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnCollapse by rememberUpdatedState(onCollapse)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val fullyOpen = revealWidth >= actionWidthPx - 0.5f
    val revealed = revealWidth > 0.5f

    Box(modifier = Modifier.fillMaxWidth()) {
        if (revealed) {
            // 垫在下面：行往左挪开多少，右端就露出多少。
            // matchParentSize 只跟行等高，不参与父 Box 的尺寸测量（行高是行自己撑的）
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                // 窗口跟着行的右边缘一起长，窗口里左右再留一条缝：红块要四周都圆，
                // 就得和行卡片一样是块独立的圆角矩形。贴着行的右边缘只圆右边两角是不行的 ——
                // 行的右上角本身是圆的，两个圆角在接缝处各自往里让，会裂出一块背景色。
                // 上下不留缝：红块和行卡片一样高，矮一圈看着就是没对齐
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
                            change.consume()
                            currentOnDrag(dragAmount)
                        },
                    )
                }
                // 露着的时候点这一行是「收回去」，不是行本身那件事（进详情、进设置）。
                // 挂在行的外层、不另盖一层：盖一层会把右侧的拖拽手柄挡在命中测试之外，
                // 而手柄是这一层的后代，先收到事件，拖动排序照样起得来。
                // 用 pointerInput 而不是 clickable：后者会把这一行的语义节点合并掉
                // （展开期间读屏读不到行里的字）
                .pointerInput(revealed) {
                    if (revealed) detectTapGestures { currentOnCollapse() }
                },
        ) {
            content()
        }
    }
}

/** 划开后整列让出的宽度：行往左挪这么多，右端就空出这么多 */
private val RevealActionWidth = 72.dp

/**
 * 红块左右留的边距。留出这一条缝，它才是块四周都圆的独立按钮，而不是贴着行的一条红边。
 *
 * 只管左右：上下和行卡片齐平 —— 上下也留一圈的话，红块会比行卡片矮一圈，看着就是没对齐。
 */
private val RevealActionInset = MaaDesignTokens.Spacing.xs

/** 删除按钮里那个图标多大 */
private val RevealIconSize = 20.dp
