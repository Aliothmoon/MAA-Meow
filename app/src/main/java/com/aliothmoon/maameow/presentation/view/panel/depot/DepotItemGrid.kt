package com.aliothmoon.maameow.presentation.view.panel.depot

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.DepotMaintainPlan
import com.aliothmoon.maameow.data.model.LogColorRole
import com.aliothmoon.maameow.data.resource.ItemIconLoader
import com.aliothmoon.maameow.presentation.components.ItemIcon
import com.aliothmoon.maameow.presentation.viewmodel.depotProgressText
import com.aliothmoon.maameow.theme.LocalReduceMotion
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.theme.MaaMotion
import com.aliothmoon.maameow.theme.themedColor

/**
 * 物品格子：图标 + 名称 + 进度，点一格就是进那个物品的计划详情。
 *
 * 库存数据页的库存网格、后台任务库存保持面板的「已有计划 / 暂无计划」两组，用的都是这一份 ——
 * 格子的样子、进度口径、颜色只有一处实现，三处不会各长各的。
 */

/** 库存为 0 的格子压到多淡；太淡就看不出是什么材料了 */
internal const val DepotEmptyCellAlpha = 0.32f

/**
 * 格子之间的竖缝。挂到每个条目自己身上，而不是交给布局的 verticalArrangement：
 * 「暂无计划」那段收起时要让格子真的缩到 0 高，挂在条目外面的间距缩不掉，
 * 二十来行攒下来就是一百多 dp 的空档。
 */
internal val DepotCellGap = 6.dp

/**
 * 一格至少这么宽：44dp 的图标，外加一行名称、一行进度。
 *
 * 定得比图标本身大不了多少：这几处都是拿它当「一行能挤下几格」的除数，
 * 定宽了窄面板上就只剩两格，一屏看得到的材料太少。
 */
internal val DepotCellMinWidth = 78.dp

/** 一行格数的下限：再少就成列表了，图标铺不开 */
internal const val DepotCellMinColumns = 2

/** 一行格数的上限：再多图标就小到认不出是什么材料了 */
internal const val DepotCellMaxColumns = 4

/**
 * 按可用宽度算一行放几格：页面越宽放得越多，夹在 [DepotCellMinColumns] ~ [DepotCellMaxColumns] 之间。
 *
 * 库存数据页的网格和后台任务的格子铺的是同一份口径 —— 同一批材料在两个页面上
 * 不该一个四列一个两列。
 */
internal fun depotItemColumns(availableWidth: Dp): Int =
    ((availableWidth + DepotCellGap) / (DepotCellMinWidth + DepotCellGap))
        .toInt()
        .coerceIn(DepotCellMinColumns, DepotCellMaxColumns)

/**
 * 一行 [columns] 格各自多少像素宽：可用宽度按整数分，除不尽的余数摊给前面几格。
 *
 * 不能拿 dp 直接除。每格换算成像素时各自四舍五入，几格凑起来可能比可用宽度多出 1px，
 * 界面上看就是「明明算出来四列，只摆了三列，右边空出大半格」。这里按像素分，
 * 一行几格的宽度加缝严格等于可用宽度。
 */
internal fun depotCellWidthsPx(availablePx: Int, gapPx: Int, columns: Int): List<Int> {
    if (columns <= 0) return emptyList()
    val usable = (availablePx - gapPx * (columns - 1)).coerceAtLeast(0)
    val base = usable / columns
    val extra = usable % columns
    return List(columns) { base + if (it < extra) 1 else 0 }
}

/**
 * 网格里的一格。
 *
 * [target] 为 null = 这个物品还没配计划，格子只显示当前数量；配了的显示「当前 / 目标」，
 * 还差就标红。未选物品的计划（老配置里「添加」留下的空行）[name] 是「未选择」，
 * [itemId] 是空串，图标自然空着 —— 它照样留在列表里，点开就能补上物品。
 *
 * [planIndex] 是已配计划那一格在节点 `plans` 里的下标，点开详情时按它定位；
 * null = 还没有计划，点开是新建。
 */
internal data class DepotItemCellUi(
    val key: String,
    val itemId: String,
    val name: String,
    val count: Int,
    val target: Int?,
    val planIndex: Int? = null,
)

/**
 * 后台任务面板的两组格子：已经配了计划的在上、还没有计划的在下。
 *
 * 计划那组按 `plans` 顺序排，和计划概览、刷取顺序同一份顺序；没计划那组按物品表顺序。
 */
internal data class DepotItemCellGroups(
    val planned: List<DepotItemCellUi>,
    val unplanned: List<DepotItemCellUi>,
)

/**
 * 按「有没有计划」把格子分成两组。
 *
 * 计划那一组一条计划一格（同一物品配了两条就是两格，跟库存数据页一个口径）；
 * 没计划那一组是物品表里其余的全部材料 —— 「添加」按钮去掉之后，加计划就是点这里的图标。
 *
 * 未选物品的计划留在计划那一组：那种计划执行侧会跳过，但用户得能看见它、点开补上物品，
 * 不然它就在界面上凭空消失了。
 */
internal fun depotMaintainItemCells(
    plans: List<DepotMaintainPlan>,
    itemIds: List<String>,
    itemNameMap: Map<String, String>,
    inventory: Map<String, Int>,
    notSelectedLabel: String,
): DepotItemCellGroups {
    val plannedIds = plans.mapTo(mutableSetOf()) { it.dropId }
    return DepotItemCellGroups(
        planned = plans.mapIndexed { index, plan ->
            DepotItemCellUi(
                key = "plan-$index",
                itemId = plan.dropId,
                name = itemNameMap[plan.dropId] ?: notSelectedLabel,
                count = inventory[plan.dropId] ?: 0,
                target = plan.dropCount,
                planIndex = index,
            )
        },
        unplanned = itemIds.filterNot { it in plannedIds }.map { id ->
            DepotItemCellUi(
                key = "item-$id",
                itemId = id,
                name = itemNameMap[id] ?: id,
                count = inventory[id] ?: 0,
                target = null,
            )
        },
    )
}

/**
 * 一格库存：图标下面是「当前 / 目标」，未集齐标红、已集齐标绿。
 *
 * 库存和库存保持是同一件事，所以不分成两个区块：没配计划的物品显示纯数量，
 * 配了计划的直接显示当前与目标，红色就代表还差。
 *
 * @param dimmed 库存为 0 的格子压淡，但仍然可点——点它就是给这个材料加计划
 * @param modifier 交给调用方挂条目级动效
 */
@Composable
internal fun DepotItemCell(
    cell: DepotItemCellUi,
    synced: Boolean,
    iconLoader: ItemIconLoader,
    onClick: (DepotItemCellUi) -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val target = cell.target
    val unmet = target != null && target > 0 && cell.count < target
    val accent = when {
        target == null -> MaterialTheme.colorScheme.onSurfaceVariant
        unmet -> MaterialTheme.colorScheme.error
        else -> LogColorRole.SUCCESS.themedColor()
    }
    Surface(
        shape = RoundedCornerShape(MaaDesignTokens.CornerRadius.inner),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = target?.let { BorderStroke(1.dp, accent.copy(alpha = 0.5f)) },
        modifier = modifier
            // 竖缝挂在自己身上，收起时才缩得干净（见 DepotCellGap）
            .padding(bottom = DepotCellGap)
            .fillMaxWidth()
            .alpha(if (dimmed) DepotEmptyCellAlpha else 1f)
            .clickable { onClick(cell) },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ItemIcon(
                itemId = cell.itemId,
                modifier = Modifier.size(44.dp),
                contentDescription = cell.name,
                loader = iconLoader,
            )
            Text(
                text = cell.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (target == null) {
                Text(
                    text = "${cell.count}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                // 格子只有 ~80dp 宽，目标能到 10 位；不省略号的话会被从中间硬裁
                Text(
                    text = depotProgressText(cell.count, target, synced),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 格子只有 ~80dp 宽：「30 / 33」已经说清还差多少，再跟一行「缺 3」是把
                // 同一个差值说两遍，还会把格子撑高、每行的格子数排不齐。缺口看红色数字即可。
            }
        }
    }
}

/**
 * 一片物品格子，一行 [depotItemColumns] 格，自己切行铺。
 *
 * 不用 [androidx.compose.foundation.layout.FlowRow]：它按「这一行还塞不塞得下」自己断行，
 * 判定的行宽和格子实际拿到的宽度只要差 1px，最后一格就会被挤到下一行。83% 缩放下面板内容
 * 738px、四格各 175px 加三条 13px 的缝正好铺满，FlowRow 却只摆了三格、右边空出 188px ——
 * 「算出来四列只显示三列」就是这么来的。行自己按 [columns] 切好，一行就是几格，
 * 不再依赖「正好铺满」这种边界情况。
 *
 * 宽度按像素整分（见 [depotCellWidthsPx]），可用宽度直接取 [BoxWithConstraints] 的整数像素
 * `constraints.maxWidth`：dp 往返一次会丢掉那 1px。
 *
 * 格子长在竖直滚动的容器里，所以用 [Column] + [Row] 而不是 Lazy 网格（Lazy 网格拿不到有限高度）。
 */
@Composable
internal fun DepotItemFlow(
    cells: List<DepotItemCellUi>,
    synced: Boolean,
    iconLoader: ItemIconLoader,
    onClick: (DepotItemCellUi) -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = depotItemColumns(maxWidth)
        val density = LocalDensity.current
        val rows = remember(cells, columns) { cells.chunked(columns) }
        // 整数像素，不走 maxWidth.roundToPx() 那圈 dp：那一圈在 83% 下会多算 1px
        val cellWidths = depotCellWidthsPx(
            availablePx = constraints.maxWidth,
            gapPx = with(density) { DepotCellGap.roundToPx() },
            columns = columns,
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEach { rowCells ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DepotCellGap),
                ) {
                    rowCells.forEachIndexed { index, cell ->
                        // 按 key 认住这一格：拖动重排、删掉一条计划之后，其余格子的图标不该重新取一遍
                        key(cell.key) {
                            Box(
                                modifier = Modifier.width(
                                    with(density) { cellWidths[index].toDp() },
                                ),
                            ) {
                                DepotItemCell(
                                    cell = cell,
                                    synced = synced,
                                    iconLoader = iconLoader,
                                    dimmed = dimmed,
                                    onClick = onClick,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分段行：一条横线加组标题，把格子断成几段。
 *
 * [onToggle] 非空时整行可点，整行最右多一个跟着 [collapsed] 转 180° 的箭头。
 * 折叠语义跟 CollapsibleSection 保持一致：收起时箭头朝下，
 * 播报文案复用 common_expand / common_collapse 那一对。
 */
@Composable
internal fun DepotSectionBreak(
    textRes: Int,
    count: Int,
    color: Color,
    collapsed: Boolean = false,
    onToggle: (() -> Unit)? = null,
) {
    val expandLabel = stringResource(R.string.common_expand)
    val collapseLabel = stringResource(R.string.common_collapse)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MaaDesignTokens.Spacing.sm, bottom = DepotCellGap),
    ) {
        HorizontalDivider(color = color.copy(alpha = 0.4f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onToggle == null) {
                        Modifier
                    } else {
                        Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = if (collapsed) expandLabel else collapseLabel,
                            onClick = onToggle,
                        )
                    }
                )
                .padding(
                    top = MaaDesignTokens.Spacing.xs,
                    bottom = 2.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(textRes, count),
                style = MaterialTheme.typography.labelLarge,
                color = color,
            )
            if (onToggle != null) {
                // 箭头贴到整行最右：它是「这行能展开」的提示，跟着标题走会被当成标题的一部分
                Spacer(modifier = Modifier.weight(1f))
                val arrowRotation by animateFloatAsState(
                    targetValue = if (collapsed) 0f else 180f,
                    animationSpec = MaaMotion.spec(LocalReduceMotion.current, MaaMotion.Fast),
                    label = "depot-section-arrow",
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (collapsed) expandLabel else collapseLabel,
                    tint = color,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(arrowRotation),
                )
            }
        }
    }
}
