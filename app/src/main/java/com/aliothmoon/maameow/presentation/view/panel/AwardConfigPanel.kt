package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.model.AwardConfig
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon

/**
 * 领取配置面板 - 迁移自 WPF AwardSettingsUserControl.xaml
 */
@Composable
fun AwardConfigPanel(
    config: AwardConfig,
    onConfigChange: (AwardConfig) -> Unit
) {
    var freeGachaTipExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, top = 2.dp, bottom = 4.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CheckBoxWithLabel(
            checked = config.award,
            onCheckedChange = { onConfigChange(config.copy(award = it)) },
            label = "领取每日/每周任务奖励"
        )

        CheckBoxWithLabel(
            checked = config.mail,
            onCheckedChange = { onConfigChange(config.copy(mail = it)) },
            label = "领取所有邮件奖励"
        )

        // 免费单抽（带提示）
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CheckBoxWithLabel(
                    checked = config.freeGacha,
                    onCheckedChange = { onConfigChange(config.copy(freeGacha = it)) },
                    label = "进行限定池赠送的每日免费单抽"
                )
                Spacer(modifier = Modifier.width(4.dp))
                ExpandableTipIcon(
                    expanded = freeGachaTipExpanded,
                    onExpandedChange = { freeGachaTipExpanded = it }
                )
            }
            ExpandableTipContent(
                visible = freeGachaTipExpanded,
                tipText = "若不存在免费单抽，则不会抽取",
                modifier = Modifier.padding(start = 28.dp)
            )
        }

        CheckBoxWithLabel(
            checked = config.orundum,
            onCheckedChange = { onConfigChange(config.copy(orundum = it)) },
            label = "领取幸运墙的每日合成玉奖励"
        )

        CheckBoxWithLabel(
            checked = config.mining,
            onCheckedChange = { onConfigChange(config.copy(mining = it)) },
            label = "领取限时开采许可的每日合成玉奖励"
        )

        CheckBoxWithLabel(
            checked = config.specialAccess,
            onCheckedChange = { onConfigChange(config.copy(specialAccess = it)) },
            label = "领取周年赠送月卡奖励"
        )
    }
}
