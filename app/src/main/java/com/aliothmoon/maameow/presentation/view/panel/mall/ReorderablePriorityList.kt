package com.aliothmoon.maameow.presentation.view.panel.mall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ReorderablePriorityList(
    items: List<String>,
    enabled: Boolean,
    onItemsReordered: (List<String>) -> Unit,
    onItemRemoved: (Int) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(lazyListState = lazyListState, onMove = { from, to ->
            val mutableList = items.toMutableList()
            val item = mutableList.removeAt(from.index)
            mutableList.add(to.index, item)
            onItemsReordered(mutableList)
        })

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, Color.LightGray)
    ) {
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂无优先购买物品\n点击下方按钮添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(items, key = { index, item -> "$item-$index" }) { index, item ->
                    ReorderableItem(reorderableState, key = "$item-$index") { isDragging ->
                        PriorityItemRow(
                            item = item,
                            index = index,
                            isDragging = isDragging,
                            enabled = enabled,
                            onRemove = { onItemRemoved(index) },
                            modifier = Modifier.longPressDraggableHandle()
                        )
                    }
                }
            }
        }
    }
}