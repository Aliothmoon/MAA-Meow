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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.INumericField

/**
 * 理智药/源石/次数区域
 */
@Composable
fun MedicineAndStoneSection(
    config: FightConfig,
    onConfigChange: (FightConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 使用理智药
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CheckBoxWithLabel(
                checked = config.useMedicine,
                onCheckedChange = {
                    onConfigChange(
                        config.copy(
                            useMedicine = it,
                            // 关闭理智药时，同时关闭源石
                            useStone = if (!it) false else config.useStone
                        )
                    )
                },
                label = "使用理智药",
                enabled = !config.useStone,  // 使用源石时禁用理智药
                modifier = Modifier.weight(1f)
            )
            INumericField(
                value = config.medicineNumber,
                onValueChange = { onConfigChange(config.copy(medicineNumber = it)) },
                minimum = 0,
                maximum = 999,
                enabled = config.useMedicine && !config.useStone,
                modifier = Modifier.width(80.dp)
            )
        }

        // 使用源石 TODO 暂时不支持使用
//        Row(
//            modifier = Modifier.fillMaxWidth(),
//            horizontalArrangement = Arrangement.SpaceBetween,
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            UseStoneSection(config, onConfigChange, modifier = Modifier.weight(1f))
//            INumericField(
//                value = config.stoneNumber,
//                onValueChange = { onConfigChange(config.copy(stoneNumber = it)) },
//                minimum = 0,
//                maximum = 999,
//                enabled = config.useStone,
//                modifier = Modifier.width(80.dp)
//            )
//        }

        // 战斗次数限制
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CheckBoxWithLabel(
                checked = config.hasTimesLimited,
                onCheckedChange = { onConfigChange(config.copy(hasTimesLimited = it)) },
                label = "指定次数",
                modifier = Modifier.weight(1f)
            )
            INumericField(
                value = config.maxTimes,
                onValueChange = { onConfigChange(config.copy(maxTimes = it)) },
                minimum = 0,
                maximum = 999,
                enabled = config.hasTimesLimited,
                modifier = Modifier.width(80.dp)
            )
        }

        // 代理倍率整除警告
        val showSeriesWarning = config.hasTimesLimited &&
                config.series > 0 &&
                config.maxTimes % config.series != 0

        if (showSeriesWarning) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFFF3E0),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "战斗次数 ${config.maxTimes} 无法被代理倍率 ${config.series} 整除，可能无法完全消耗理智",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF57C00),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}