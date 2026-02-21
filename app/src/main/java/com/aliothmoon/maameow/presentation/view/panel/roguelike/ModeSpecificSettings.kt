package com.aliothmoon.maameow.presentation.view.panel.roguelike

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                "刷等级模式设置",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            // WPF: Visibility="RoguelikeTheme != Phantom" (line 335)
            if (config.theme != "Phantom") {
                CheckBoxWithLabel(
                    checked = config.stopAtFinalBoss,
                    onCheckedChange = { onConfigChange(config.copy(stopAtFinalBoss = it)) },
                    label = "在第五层 BOSS 前暂停"
                )
            }
            CheckBoxWithLabel(
                checked = config.stopAtMaxLevel,
                onCheckedChange = { onConfigChange(config.copy(stopAtMaxLevel = it)) },
                label = "满级后自动停止"
            )
        }

        RoguelikeMode.Investment -> {
            // 投资模式设置已在上面处理
        }

        RoguelikeMode.Collectible -> {
            Text(
                "刷开局模式设置",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            RoguelikeSquadButtonGroup(
                label = "烧水使用分队",
                selectedValue = config.collectibleModeSquad,
                theme = config.theme,
                mode = config.mode,
                onValueChange = { onConfigChange(config.copy(collectibleModeSquad = it)) }
            )

            CheckBoxWithLabel(
                checked = config.collectibleModeShopping,
                onCheckedChange = { onConfigChange(config.copy(collectibleModeShopping = it)) },
                label = "刷开局模式启用购物"
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
                    label = "凹「开局干员」直升精二"
                )

                // WPF: Visibility="StartWithEliteTwo AND mode==Collectible AND (Mizuki OR Sami)" (line 216)
                AnimatedVisibility(visible = config.startWithEliteTwo) {
                    CheckBoxWithLabel(
                        checked = config.onlyStartWithEliteTwo,
                        onCheckedChange = { onConfigChange(config.copy(onlyStartWithEliteTwo = it)) },
                        label = "只凹「开局干员」直升精二，不进行作战",
                        modifier = Modifier.padding(start = 24.dp)
                    )
                }
            }

            // WPF: CheckComboBox (xaml:225-234) 开局奖励选择
            // Visibility: Mode==Collectible AND !RoguelikeOnlyStartWithEliteTwo
            val computedOnlyEliteTwo = config.onlyStartWithEliteTwo && config.startWithEliteTwo && squadIsProfessional
            if (!computedOnlyEliteTwo) {
                val awardOptions = RoguelikeUi.getCollectibleAwardOptions(config.theme)
                Text(
                    "刷开局期望奖励",
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
                "月度小队模式设置",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            CheckBoxWithLabel(
                checked = config.monthlySquadAutoIterate,
                onCheckedChange = { onConfigChange(config.copy(monthlySquadAutoIterate = it)) },
                label = "月度小队自动切换"
            )
            // WPF: Visibility="RoguelikeMonthlySquadAutoIterate" (line 357)
            if (config.monthlySquadAutoIterate) {
                CheckBoxWithLabel(
                    checked = config.monthlySquadCheckComms,
                    onCheckedChange = { onConfigChange(config.copy(monthlySquadCheckComms = it)) },
                    label = "月度小队通讯"
                )
            }
        }

        RoguelikeMode.Exploration -> {
            Text(
                "深入调查模式设置",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            CheckBoxWithLabel(
                checked = config.deepExplorationAutoIterate,
                onCheckedChange = { onConfigChange(config.copy(deepExplorationAutoIterate = it)) },
                label = "深入调查自动切换"
            )
        }

        RoguelikeMode.CLP_PDS -> {
            Text(
                "刷坍缩范式设置",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            ITextField(
                value = config.expectedCollapsalParadigms,
                onValueChange = { onConfigChange(config.copy(expectedCollapsalParadigms = it)) },
                label = "坍缩范式列表",
                placeholder = "用英文分号 ; 隔开，留空将使用默认列表。",
                modifier = Modifier.fillMaxWidth()
            )
        }

        RoguelikeMode.FindPlaytime -> {
            if (config.theme == "JieGarden") {
                Text(
                    "刷常乐节点设置",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                RoguelikeButtonGroup(
                    label = "目标常乐节点",
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