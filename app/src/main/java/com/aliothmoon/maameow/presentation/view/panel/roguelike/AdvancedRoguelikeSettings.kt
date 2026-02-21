package com.aliothmoon.maameow.presentation.view.panel.roguelike

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.model.RoguelikeConfig
import com.aliothmoon.maameow.domain.enums.RoguelikeMode
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon
import com.aliothmoon.maameow.domain.enums.UiUsageConstants.Roguelike as RoguelikeUi

@Composable
fun AdvancedRoguelikeSettings(
    config: RoguelikeConfig,
    onConfigChange: (RoguelikeConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 投资相关
        Text(
            "投资设置",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        // WPF: IsEnabled="{c:Binding 'RoguelikeMode != Investment'}" (line 150)
        // 投资模式下强制启用投资，checkbox 禁用
        CheckBoxWithLabel(
            checked = config.investmentEnabled,
            onCheckedChange = { onConfigChange(config.copy(investmentEnabled = it)) },
            label = "投资源石锭",
            enabled = config.mode != RoguelikeMode.Investment
        )

        AnimatedVisibility(visible = config.investmentEnabled) {
            Column(
                modifier = Modifier.padding(start = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("投资 N 个源石锭后停止任务", style = MaterialTheme.typography.bodySmall)
                    ITextField(
                        value = config.investCount.toString(),
                        onValueChange = {
                            onConfigChange(
                                config.copy(
                                    investCount = it.toIntOrNull() ?: 999
                                )
                            )
                        },
                        placeholder = "999",
                        modifier = Modifier.width(80.dp)
                    )
                }

                // WPF: Visibility="RoguelikeInvestmentEnabled AND RoguelikeMode != Collectible" (line 156)
                if (config.mode != RoguelikeMode.Collectible) {
                    CheckBoxWithLabel(
                        checked = config.stopWhenInvestmentFull,
                        onCheckedChange = { onConfigChange(config.copy(stopWhenInvestmentFull = it)) },
                        label = "储备源石锭达到上限时停止"
                    )
                }

                if (config.mode == RoguelikeMode.Investment) {
                    CheckBoxWithLabel(
                        checked = config.investmentWithMoreScore,
                        onCheckedChange = { onConfigChange(config.copy(investmentWithMoreScore = it)) },
                        label = "投资模式启用购物、招募、进2层"
                    )
                }
            }
        }

        HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)

        // 助战相关
        Text(
            "助战设置",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        var supportTipExpanded by remember { mutableStateOf(false) }
        // WPF: IsEnabled="{c:Binding 'RoguelikeCoreChar != \"\"'}" (line 305)
        val supportEnabled = config.coreChar.isNotEmpty()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckBoxWithLabel(
                    checked = config.useSupport,
                    onCheckedChange = { checked ->
                        val squadIsProfessional = RoguelikeUi.isSquadProfessional(
                            config.squad, config.mode, config.theme
                        )
                        var newConfig = config.copy(useSupport = checked)
                        // WPF: UseSupportUnit setter (line 656-666)
                        if (checked && config.startWithEliteTwo && squadIsProfessional) {
                            newConfig = newConfig.copy(startWithEliteTwo = false)
                        }
                        onConfigChange(newConfig)
                    },
                    label = "「开局干员」使用助战",
                    enabled = supportEnabled
                )
                Spacer(modifier = Modifier.width(4.dp))
                ExpandableTipIcon(
                    expanded = supportTipExpanded,
                    onExpandedChange = { supportTipExpanded = it })
            }
            ExpandableTipContent(
                visible = supportTipExpanded,
                tipText = "需先填写「开局干员」",
                modifier = Modifier.padding(start = 28.dp)
            )
        }

        // WPF: Visibility="RoguelikeUseSupportUnit AND RoguelikeCoreChar != ''" (line 321)
        AnimatedVisibility(visible = config.useSupport && supportEnabled) {
            CheckBoxWithLabel(
                checked = config.enableNonfriendSupport,
                onCheckedChange = { onConfigChange(config.copy(enableNonfriendSupport = it)) },
                label = "可以使用非好友助战",
                modifier = Modifier.padding(start = 24.dp)
            )
        }

        HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)

        // 开局次数 - WPF: Maximum="99999" (line 142)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("开始探索 N 次后停止任务", style = MaterialTheme.typography.bodySmall)
            ITextField(
                value = config.startsCount.toString(),
                onValueChange = {
                    onConfigChange(
                        config.copy(
                            startsCount = it.toIntOrNull() ?: 99999
                        )
                    )
                },
                placeholder = "99999",
                modifier = Modifier.width(100.dp)
            )
        }

        HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)

        // 模式特殊设置
        ModeSpecificSettings(config, onConfigChange)
    }
}