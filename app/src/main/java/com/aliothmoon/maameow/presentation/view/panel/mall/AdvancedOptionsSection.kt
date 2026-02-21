package com.aliothmoon.maameow.presentation.view.panel.mall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.model.MallConfig
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon

@Composable
fun AdvancedOptionsSection(config: MallConfig, onConfigChange: (MallConfig) -> Unit) {

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Text(
            "高级选项",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        // 溢出时无视黑名单
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            var forceShoppingTipExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CheckBoxWithLabel(
                    checked = config.forceShoppingIfCreditFull,
                    onCheckedChange = { onConfigChange(config.copy(forceShoppingIfCreditFull = it)) },
                    label = "信用溢出时无视黑名单",
                    enabled = config.shopping
                )
                Spacer(modifier = Modifier.width(4.dp))
                ExpandableTipIcon(
                    expanded = forceShoppingTipExpanded,
                    onExpandedChange = { forceShoppingTipExpanded = it })
            }
            ExpandableTipContent(
                visible = forceShoppingTipExpanded,
                tipText = "信用点即将溢出时，强制购买黑名单物品避免浪费",
                modifier = Modifier.padding(start = 28.dp)
            )
        }

        // 只买打折物品
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            var onlyDiscountTipExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CheckBoxWithLabel(
                    checked = config.onlyBuyDiscount,
                    onCheckedChange = { onConfigChange(config.copy(onlyBuyDiscount = it)) },
                    label = "只购买打折的信用商品",
                    enabled = config.shopping
                )
                Spacer(modifier = Modifier.width(4.dp))
                ExpandableTipIcon(
                    expanded = onlyDiscountTipExpanded,
                    onExpandedChange = { onlyDiscountTipExpanded = it })
            }
            ExpandableTipContent(
                visible = onlyDiscountTipExpanded,
                tipText = "注意：可能会导致信用点数溢出！仍然会购买未打折的白名单物品！",
                modifier = Modifier.padding(start = 28.dp)
            )
        }

        // 预留300信用点
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            var reserveCreditTipExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CheckBoxWithLabel(
                    checked = config.reserveMaxCredit,
                    onCheckedChange = { onConfigChange(config.copy(reserveMaxCredit = it)) },
                    label = "信用点低于 300 时停止购买",
                    enabled = config.shopping
                )
                Spacer(modifier = Modifier.width(4.dp))
                ExpandableTipIcon(
                    expanded = reserveCreditTipExpanded,
                    onExpandedChange = { reserveCreditTipExpanded = it })
            }
            ExpandableTipContent(
                visible = reserveCreditTipExpanded,
                tipText = "低于 300 信用点也仍然会购买白名单物品！",
                modifier = Modifier.padding(start = 28.dp)
            )
        }
    }
}