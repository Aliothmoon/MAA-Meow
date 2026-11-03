package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.presentation.LocalInputFocusManager

private val IndicatorWidth = 20.dp
private val IndicatorHeight = 3.dp

/**
 * 面板标题栏
 *
 * @param pagePosition 分页实时位置，指示条跟手滑动；为空时停在选中项
 */
@Composable
fun PanelHeader(
    selectedTab: PanelTab = PanelTab.TASKS,
    onTabSelected: (PanelTab) -> Unit = {},
    showActions: Boolean = true,
    isLocked: Boolean = false,
    onLockToggle: (Boolean) -> Unit = {},
    onHome: () -> Unit = {},
    pagePosition: (() -> Float)? = null,
    modifier: Modifier = Modifier,
) {
    val inputFocus = LocalInputFocusManager.current
    val select: (PanelTab) -> Unit = { tab ->
        if (selectedTab != tab) inputFocus.clear()
        onTabSelected(tab)
    }

    if (!showActions) {
        EvenTabRow(selectedTab, select, pagePosition, modifier)
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PanelTab.entries.forEach { tab ->
                TabLabel(
                    tab = tab,
                    selected = selectedTab == tab,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { select(tab) }
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onHome,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = stringResource(R.string.panel_cd_go_home),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = { onLockToggle(!isLocked) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Filled.Lock else Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = if (isLocked)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// 等宽四格，整格可点，指示条在绘制阶段读分页位置
@Composable
private fun EvenTabRow(
    selectedTab: PanelTab,
    onSelect: (PanelTab) -> Unit,
    pagePosition: (() -> Float)?,
    modifier: Modifier,
) {
    val indicatorColor = MaterialTheme.colorScheme.primary
    val lastIndex = PanelTab.entries.lastIndex
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .drawBehind {
                val cell = size.width / (lastIndex + 1)
                val pos = (pagePosition?.invoke() ?: selectedTab.ordinal.toFloat())
                    .coerceIn(0f, lastIndex.toFloat())
                val w = IndicatorWidth.toPx()
                val h = IndicatorHeight.toPx()
                drawRoundRect(
                    color = indicatorColor,
                    topLeft = Offset(cell * (pos + 0.5f) - w / 2, size.height - h),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(h / 2),
                )
            },
    ) {
        PanelTab.entries.forEach { tab ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(tab) }
                    .padding(top = 4.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                TabLabel(tab, selectedTab == tab)
            }
        }
    }
}

@Composable
private fun TabLabel(tab: PanelTab, selected: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(tab.labelRes),
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = modifier,
    )
}
