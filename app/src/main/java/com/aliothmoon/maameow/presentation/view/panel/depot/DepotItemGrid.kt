package com.aliothmoon.maameow.presentation.view.panel.depot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.model.DepotMaintainPlan
import com.aliothmoon.maameow.data.model.LogColorRole
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ItemIconLoader
import com.aliothmoon.maameow.presentation.components.ItemIcon
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.theme.themedColor

internal const val DepotEmptyCellAlpha = 0.32f
internal val DepotCellGap = 6.dp
internal val DepotCellMinWidth = 78.dp
internal const val DepotCellMinColumns = 2
internal const val DepotCellMaxColumns = 4

/** 按可用宽度算一行列数，限制在 [DepotCellMinColumns] ~ [DepotCellMaxColumns] */
internal fun depotItemColumns(availableWidth: Dp): Int =
    ((availableWidth + DepotCellGap) / (DepotCellMinWidth + DepotCellGap))
        .toInt()
        .coerceIn(DepotCellMinColumns, DepotCellMaxColumns)

/** 按整数像素均分行宽，余数分摊到前几列避免四舍五入多出 1px */
internal fun depotCellWidthsPx(availablePx: Int, gapPx: Int, columns: Int): List<Int> {
    if (columns <= 0) return emptyList()
    val usable = (availablePx - gapPx * (columns - 1)).coerceAtLeast(0)
    val base = usable / columns
    val extra = usable % columns
    return List(columns) { base + if (it < extra) 1 else 0 }
}

internal data class DepotItemCellUi(
    val key: String,
    val itemId: String,
    val name: String,
    val count: Int,
    val target: Int?,
    val planIndex: Int? = null,
)

internal data class DepotItemCellGroups(
    val planned: List<DepotItemCellUi>,
    val unplanned: List<DepotItemCellUi>,
)

/** 按是否已有计划将物品分成已计划与未计划两组 */
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

/** 单格物品：合并为单个无障碍语义节点，避免长网格滚动每帧重排语义树 */
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
    val hasBaseline = synced || cell.itemId in ItemHelper.CURRENCY_ITEM_IDS
    val unmet = hasBaseline && target != null && target > 0 && cell.count < target
    val accent = when {
        target == null -> MaterialTheme.colorScheme.onSurfaceVariant
        !hasBaseline -> MaterialTheme.colorScheme.onSurfaceVariant
        unmet -> MaterialTheme.colorScheme.primary
        else -> LogColorRole.SUCCESS.themedColor()
    }
    val valueText = if (target == null) {
        if (hasBaseline) "${cell.count}" else "--"
    } else {
        depotProgressText(cell.count, target, hasBaseline)
    }

    Surface(
        shape = RoundedCornerShape(MaaDesignTokens.CornerRadius.inner),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = target?.let { BorderStroke(1.dp, accent.copy(alpha = 0.5f)) },
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (dimmed) DepotEmptyCellAlpha else 1f)
            .clearAndSetSemantics {
                contentDescription = "${cell.name} $valueText"
                role = Role.Button
                onClick {
                    onClick(cell)
                    true
                }
            }
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
                contentDescription = null,
                loader = iconLoader,
            )
            Text(
                text = cell.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (target == null) MaterialTheme.colorScheme.onSurface else accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 懒加载物品网格，仅组合可见区域格子避免抽屉打开时全量渲染 */
@Composable
internal fun DepotItemLazyGrid(
    cells: List<DepotItemCellUi>,
    synced: Boolean,
    iconLoader: ItemIconLoader,
    onClick: (DepotItemCellUi) -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    BoxWithConstraints(modifier = modifier) {
        val columns = depotItemColumns(maxWidth)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(DepotCellGap),
            verticalArrangement = Arrangement.spacedBy(DepotCellGap),
        ) {
            items(
                items = cells,
                key = { it.key },
                contentType = { "depot-item-cell" },
            ) { cell ->
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
