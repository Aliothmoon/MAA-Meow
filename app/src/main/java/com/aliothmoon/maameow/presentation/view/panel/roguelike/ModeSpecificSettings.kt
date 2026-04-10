package com.aliothmoon.maameow.presentation.view.panel.roguelike

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.RoguelikeConfig
import com.aliothmoon.maameow.domain.enums.RoguelikeBoskySubNodeType
import com.aliothmoon.maameow.domain.enums.RoguelikeMode
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.domain.enums.UiUsageConstants.Roguelike as RoguelikeUi

@Composable
fun ModeSpecificSettings(
    config: RoguelikeConfig,
    onConfigChange: (RoguelikeConfig) -> Unit
) {
    when (config.mode) {
        RoguelikeMode.Exp -> {
            Text(
                stringResource(R.string.level_up_mode_settings),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            // WPF: Visibility="RoguelikeTheme != Phantom" (line 335)
            if (config.theme != "Phantom") {
                CheckBoxWithLabel(
                    checked = config.stopAtFinalBoss,
                    onCheckedChange = { onConfigChange(config.copy(stopAtFinalBoss = it)) },
                    label = stringResource(R.string.pause_before_boss_5)
                )
            }
            CheckBoxWithLabel(
                checked = config.stopAtMaxLevel,
                onCheckedChange = { onConfigChange(config.copy(stopAtMaxLevel = it)) },
                label = stringResource(R.string.auto_stop_on_max_level)
            )
        }

        RoguelikeMode.Investment -> {
            // 投资模式设置已在上面处理
        }

        RoguelikeMode.Collectible -> {
            Text(
                stringResource(R.string.open_start_mode_settings),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            RoguelikeSquadButtonGroup(
                label = stringResource(R.string.boil_water_squad),
                selectedValue = config.collectibleModeSquad,
                theme = config.theme,
                mode = config.mode,
                onValueChange = { onConfigChange(config.copy(collectibleModeSquad = it)) }
            )

            CheckBoxWithLabel(
                checked = config.collectibleModeShopping,
                onCheckedChange = { onConfigChange(config.copy(collectibleModeShopping = it)) },
                label = stringResource(R.string.open_start_shopping)
            )

            // WPF: Visibility="RoguelikeSquadIsProfessional AND (Mizuki OR Sami)" (line 205)
            val squadIsProfessional = RoguelikeUi.isSquadProfessional(
                config.squad, config.mode, config.theme
            )
            val eliteTwoVisible =
                squadIsProfessional && config.theme in listOf("Mizuki", "Sami")

            if (eliteTwoVisible) {
                CheckBoxWithLabel(
                    checked = config.startWithEliteTwo,
                    onCheckedChange = { checked ->
                        var newConfig = config.copy(startWithEliteTwo = checked)
                        // WPF: StartWithEliteTwo setter (line 499-512)
                        if (checked && config.useSupport) {
                            newConfig = newConfig.copy(useSupport = false)
                        }
                        if (!checked) {
                            newConfig = newConfig.copy(onlyStartWithEliteTwo = false)
                        }
                        onConfigChange(newConfig)
                    },
                    label = stringResource(R.string.target_elite2_start_op)
                )

                // WPF: Visibility="StartWithEliteTwo AND mode==Collectible AND (Mizuki OR Sami)" (line 216)
                AnimatedVisibility(visible = config.startWithEliteTwo) {
                    CheckBoxWithLabel(
                        checked = config.onlyStartWithEliteTwo,
                        onCheckedChange = { onConfigChange(config.copy(onlyStartWithEliteTwo = it)) },
                        label = stringResource(R.string.target_elite2_only)
                    )
                }
            }

            // WPF: CheckComboBox (xaml:225-234) 开局奖励选择
            // Visibility: Mode==Collectible AND !RoguelikeOnlyStartWithEliteTwo
            val computedOnlyEliteTwo = config.onlyStartWithEliteTwo && config.startWithEliteTwo && squadIsProfessional
            if (!computedOnlyEliteTwo) {
                val awardOptions = RoguelikeUi.getCollectibleAwardOptions(config.theme)
                Text(
                    stringResource(R.string.open_start_expected_reward),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                awardOptions.forEach { (key, displayName) ->
                    CheckBoxWithLabel(
                        checked = key in config.collectibleStartAwards,
                        onCheckedChange = { checked ->
                            val newAwards = if (checked) {
                                config.collectibleStartAwards + key
                            } else {
                                config.collectibleStartAwards - key
                            }
                            onConfigChange(config.copy(collectibleStartAwards = newAwards))
                        },
                        label = displayName,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        RoguelikeMode.Squad -> {
            Text(
                stringResource(R.string.monthly_team_settings),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            CheckBoxWithLabel(
                checked = config.monthlySquadAutoIterate,
                onCheckedChange = { onConfigChange(config.copy(monthlySquadAutoIterate = it)) },
                label = stringResource(R.string.monthly_team_auto_switch)
            )
            // WPF: Visibility="RoguelikeMonthlySquadAutoIterate" (line 357)
            if (config.monthlySquadAutoIterate) {
                CheckBoxWithLabel(
                    checked = config.monthlySquadCheckComms,
                    onCheckedChange = { onConfigChange(config.copy(monthlySquadCheckComms = it)) },
                    label = stringResource(R.string.monthly_team_comms)
                )
            }
        }

        RoguelikeMode.Exploration -> {
            Text(
                stringResource(R.string.deep_exploration_settings),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            CheckBoxWithLabel(
                checked = config.deepExplorationAutoIterate,
                onCheckedChange = { onConfigChange(config.copy(deepExplorationAutoIterate = it)) },
                label = stringResource(R.string.deep_exploration_auto_switch)
            )
        }

        RoguelikeMode.CLP_PDS -> {
            Text(
                stringResource(R.string.collapse_paradigm_settings),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            ITextField(
                value = config.expectedCollapsalParadigms,
                onValueChange = { onConfigChange(config.copy(expectedCollapsalParadigms = it)) },
                label = stringResource(R.string.collapse_paradigm_list),
                placeholder = stringResource(R.string.collapse_paradigm_placeholder),
                modifier = Modifier.fillMaxWidth()
            )
        }

        RoguelikeMode.FindPlaytime -> {
            if (config.theme == "JieGarden") {
                Text(
                    stringResource(R.string.changlo_node_settings),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                RoguelikeButtonGroup(
                    label = stringResource(R.string.target_changlo_node),
                    selectedValue = config.findPlaytimeTarget.name,
                    options = RoguelikeUi.PLAYTIME_TARGET_OPTIONS,
                    onValueChange = {
                        onConfigChange(
                            config.copy(
                                findPlaytimeTarget = RoguelikeBoskySubNodeType.valueOf(
                                    it
                                )
                            )
                        )
                    }
                )
            }
        }
    }

    // 主题特殊设置
    ThemeSpecificSettings(config, onConfigChange)
}
