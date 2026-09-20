package com.aliothmoon.maameow.presentation.view.panel.roguelike

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.RoguelikeConfig
import com.aliothmoon.maameow.domain.enums.RoguelikeMode
import com.aliothmoon.maameow.presentation.components.CheckBoxWithExpandableTip
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility
import com.aliothmoon.maameow.domain.enums.UiUsageConstants.Roguelike as RoguelikeUi

@Composable
fun AdvancedRoguelikeSettings(
    config: RoguelikeConfig,
    onConfigChange: (RoguelikeConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 投资相关
        Text(
            stringResource(R.string.panel_roguelike_investment_settings),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        // WPF: IsEnabled="{c:Binding 'RoguelikeMode != Investment'}" (line 150)
        // 投资模式下强制启用投资，checkbox 禁用
        CheckBoxWithLabel(
            checked = config.investmentEnabled,
            onCheckedChange = { onConfigChange(config.copy(investmentEnabled = it)) },
            label = stringResource(R.string.panel_roguelike_investment_enabled),
            enabled = config.mode != RoguelikeMode.Investment
        )

        MaaAnimatedVisibility(visible = config.investmentEnabled) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ITextField(
                    value = config.investCount.toString(),
                    onValueChange = {
                        onConfigChange(
                            config.copy(
                                investCount = it.toIntOrNull() ?: 999
                            )
                        )
                    },
                    label = stringResource(R.string.panel_roguelike_invest_count),
                    placeholder = "999",
                    modifier = Modifier.fillMaxWidth()
                )

                // WPF: Visibility="RoguelikeInvestmentEnabled AND RoguelikeMode != Collectible" (line 156)
                if (config.mode != RoguelikeMode.Collectible) {
                    CheckBoxWithLabel(
                        checked = config.stopWhenInvestmentFull,
                        onCheckedChange = { onConfigChange(config.copy(stopWhenInvestmentFull = it)) },
                        label = stringResource(R.string.panel_roguelike_stop_when_invest_full)
                    )
                }

                if (config.mode == RoguelikeMode.Investment) {
                    CheckBoxWithLabel(
                        checked = config.investmentWithMoreScore,
                        onCheckedChange = { onConfigChange(config.copy(investmentWithMoreScore = it)) },
                        label = stringResource(R.string.panel_roguelike_investment_more_score)
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

        // 助战相关
        Text(
            stringResource(R.string.panel_roguelike_support_settings),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        // WPF: EnableAdditionalStartingCoreChar (xaml:341)
        CheckBoxWithExpandableTip(
            checked = config.useAdditionalStartingOpers,
            onCheckedChange = { onConfigChange(config.copy(useAdditionalStartingOpers = it)) },
            label = stringResource(R.string.panel_roguelike_additional_core_chars),
            tipText = stringResource(R.string.panel_roguelike_additional_core_chars_tip)
        )

        CheckBoxWithExpandableTip(
            checked = config.startingOperUseSupport(0),
            onCheckedChange = { checked ->
                val squadIsProfessional = RoguelikeUi.isSquadProfessional(
                    config.squad, config.mode, config.theme
                )
                var newConfig = config.withStartingOper(0) { it.copy(useSupport = checked) }
                // WPF: UseSupportUnit setter (line 656-666)
                if (checked && config.startWithEliteTwo && squadIsProfessional) {
                    newConfig = newConfig.copy(startWithEliteTwo = false)
                }
                onConfigChange(newConfig)
            },
            label = useSupportLabel(config, 0),
            tipText = stringResource(R.string.panel_roguelike_use_support_tip),
            enabled = config.isStartingOperFilled(0)
        )

        MaaAnimatedVisibility(visible = config.useAdditionalStartingOpers) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (index in 1 until RoguelikeConfig.MAX_STARTING_OPERS) {
                    CheckBoxWithLabel(
                        checked = config.startingOperUseSupport(index),
                        onCheckedChange = { checked ->
                            onConfigChange(
                                config.withStartingOper(index) { it.copy(useSupport = checked) }
                            )
                        },
                        label = useSupportLabel(config, index),
                        enabled = config.isStartingOperFilled(index)
                    )
                }
            }
        }

        // WPF: Visibility 为任一顺位勾了助战（2/3 顺位要开关开启才计入）(xaml:390)
        MaaAnimatedVisibility(visible = config.anyStartingOperUsesSupport) {
            CheckBoxWithLabel(
                checked = config.enableNonfriendSupport,
                onCheckedChange = { onConfigChange(config.copy(enableNonfriendSupport = it)) },
                label = stringResource(R.string.panel_roguelike_nonfriend_support),
                enabled = config.isStartingOperFilled(0)
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

        // 开局次数 - WPF: Maximum="99999" (line 142)
        ITextField(
            value = config.startsCount.toString(),
            onValueChange = {
                onConfigChange(
                    config.copy(
                        startsCount = it.toIntOrNull() ?: 99999
                    )
                )
            },
            label = stringResource(R.string.panel_roguelike_starts_count),
            placeholder = "99999",
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

        // 模式特殊设置
        ModeSpecificSettings(config, onConfigChange)
    }
}

/** WPF RoguelikeUseSupportUnitFormat —— 干员名为空时退回「第 N 位干员」 */
@Composable
private fun useSupportLabel(config: RoguelikeConfig, index: Int): String {
    val who = config.startingOperName(index).ifBlank {
        stringResource(R.string.panel_roguelike_starting_oper_position, index + 1)
    }
    return stringResource(R.string.panel_roguelike_use_support_format, who)
}
