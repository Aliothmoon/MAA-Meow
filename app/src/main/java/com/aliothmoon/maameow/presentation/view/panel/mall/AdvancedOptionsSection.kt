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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.MallConfig
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon

@Composable
fun AdvancedOptionsSection(config: MallConfig, onConfigChange: (MallConfig) -> Unit) {

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Text(
            stringResource(R.string.panel_mall_advanced_options),
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
                    label = stringResource(R.string.panel_mall_force_shopping_if_full),
                    enabled = config.shopping
                )
                Spacer(modifier = Modifier.width(4.dp))
                ExpandableTipIcon(
                    expanded = forceShoppingTipExpanded,
                    onExpandedChange = { forceShoppingTipExpanded = it })
            }
            ExpandableTipContent(
                visible = forceShoppingTipExpanded,
                tipText = stringResource(R.string.panel_mall_force_shopping_if_full_tip)
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
                    label = stringResource(R.string.panel_mall_only_discount),
                    enabled = config.shopping
                )
                Spacer(modifier = Modifier.width(4.dp))
                ExpandableTipIcon(
                    expanded = onlyDiscountTipExpanded,
                    onExpandedChange = { onlyDiscountTipExpanded = it })
            }
            ExpandableTipContent(
                visible = onlyDiscountTipExpanded,
                tipText = stringResource(R.string.panel_mall_only_discount_tip)
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
                    label = stringResource(R.string.panel_mall_reserve_credit),
                    enabled = config.shopping
                )
                Spacer(modifier = Modifier.width(4.dp))
                ExpandableTipIcon(
                    expanded = reserveCreditTipExpanded,
                    onExpandedChange = { reserveCreditTipExpanded = it })
            }
            ExpandableTipContent(
                visible = reserveCreditTipExpanded,
                tipText = stringResource(R.string.panel_mall_reserve_credit_tip)
            )
        }
    }
}
