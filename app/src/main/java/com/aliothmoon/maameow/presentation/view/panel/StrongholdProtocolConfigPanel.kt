package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.StrongholdProtocolConfig
import com.aliothmoon.maameow.data.resource.StrongholdPriority
import com.aliothmoon.maameow.presentation.components.INumericField
import com.aliothmoon.maameow.presentation.components.SelectableChipGroup

/**
 * 工具箱「牛杂」里的卫戍协议设置，对齐上游 ToolboxView 卫戍协议区块
 * 改动以 transform 提交，由存储侧在事务内读改写
 */
fun LazyListScope.strongholdProtocolSettingsItems(
    config: StrongholdProtocolConfig,
    priority: StrongholdPriority,
    onUpdate: ((StrongholdProtocolConfig) -> StrongholdProtocolConfig) -> Unit,
) {
    item {
        SelectableChipGroup(
            label = stringResource(R.string.panel_stronghold_mode),
            selectedValue = if (config.isBadge) StrongholdProtocolConfig.MODE_BADGE else StrongholdProtocolConfig.MODE_SCORE,
            options = listOf(
                StrongholdProtocolConfig.MODE_BADGE to stringResource(R.string.panel_stronghold_mode_badge),
                StrongholdProtocolConfig.MODE_SCORE to stringResource(R.string.panel_stronghold_mode_score),
            ),
            onSelected = { mode -> onUpdate { it.copy(mode = mode) } },
            labelFontWeight = FontWeight.Medium,
        )
    }
    if (config.isBadge) {
        item {
            SelectableChipGroup(
                label = stringResource(R.string.panel_stronghold_target),
                selectedValue = config.target,
                options = listOf(
                    StrongholdProtocolConfig.TARGET_GOLD to stringResource(R.string.panel_stronghold_target_gold),
                    StrongholdProtocolConfig.TARGET_COLOR to stringResource(R.string.panel_stronghold_target_color),
                ),
                onSelected = { target -> onUpdate { it.copy(target = target) } },
                labelFontWeight = FontWeight.Medium,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.panel_stronghold_runs),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                INumericField(
                    value = config.runs,
                    onValueChange = { runs -> onUpdate { it.copy(runs = runs) } },
                    minimum = 1,
                    maximum = StrongholdProtocolConfig.MAX_RUNS,
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(56.dp)
                )
            }
        }
    }
    item {
        val tip = if (config.isBadge) {
            stringResource(R.string.panel_stronghold_tip_badge, priority.squad.joinOrDash(), priority.shop.joinOrDash())
        } else {
            stringResource(R.string.panel_stronghold_tip_score)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                tip,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}

private fun List<String>.joinOrDash(): String = if (isEmpty()) "—" else joinToString(" → ")
