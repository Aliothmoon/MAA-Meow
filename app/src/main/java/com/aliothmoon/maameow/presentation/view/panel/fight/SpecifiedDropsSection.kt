package com.aliothmoon.maameow.presentation.view.panel.fight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.data.resource.ItemInfo
import com.aliothmoon.maameow.domain.enums.UiUsageConstants
import com.aliothmoon.maameow.presentation.components.CheckBoxWithExpandableTip
import com.aliothmoon.maameow.presentation.components.INumericField

/**
 * 指定材料掉落区域
 */
@Composable
fun SpecifiedDropsSection(
    config: FightConfig,
    onConfigChange: (FightConfig) -> Unit,
    dropItems: List<ItemInfo>
) {
    // 构建材料 ID 到名称的映射
    val itemNameMap = remember(dropItems) {
        dropItems.associate { it.id to it.name }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 启用指定掉落复选框
        CheckBoxWithExpandableTip(
            checked = config.isSpecifiedDrops,
            onCheckedChange = {
                onConfigChange(
                    config.copy(
                        isSpecifiedDrops = it,
                        // 取消时清空材料设置
                        dropsItemId = if (!it) "" else config.dropsItemId,
                        dropsQuantity = if (!it) 5 else config.dropsQuantity
                    )
                )
            },
            label = "指定材料掉落",
            tipText = "刷到指定数量的材料后停止作战"
        )

        // 材料选择和数量输入（启用后显示）
        if (config.isSpecifiedDrops) {
            // 材料选择说明提示
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFFF3E0),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "该选项不会自动计算最优关卡，请手动选择关卡",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF57C00),
                    modifier = Modifier.padding(8.dp)
                )
            }

            // 材料选择（平铺展示）
            val itemIds = if (dropItems.isNotEmpty()) {
                dropItems.map { it.id }
            } else {
                UiUsageConstants.dropItems
            }

            ItemButtonGroup(
                label = "材料",
                selectedValue = config.dropsItemId,
                items = itemIds,
                onItemSelected = { onConfigChange(config.copy(dropsItemId = it)) },
                displayMapper = { id -> itemNameMap[id] ?: id }
            )

            // 材料数量
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "目标数量",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                INumericField(
                    value = config.dropsQuantity,
                    onValueChange = { onConfigChange(config.copy(dropsQuantity = it)) },
                    minimum = 1,
                    maximum = 1145141919,
                    modifier = Modifier.width(100.dp)
                )
            }
        }
    }
}