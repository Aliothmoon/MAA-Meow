package com.aliothmoon.maameow.presentation.view.panel.roguelike

import androidx.annotation.StringRes
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
import com.aliothmoon.maameow.domain.enums.RoguelikeBlackFlowStartAnimal
import com.aliothmoon.maameow.domain.enums.RoguelikeBlackFlowStartReward
import com.aliothmoon.maameow.presentation.components.CheckBoxWithExpandableTip
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.SelectableChipGroup
import com.aliothmoon.maameow.domain.enums.UiUsageConstants.Roguelike as RoguelikeUi

/** 黑流刷等级：快速飞三层与目标结局二选一，对齐 WPF RoguelikeSettingsUserControl */
@Composable
fun BlackFlowExpSettings(
    config: RoguelikeConfig,
    onConfigChange: (RoguelikeConfig) -> Unit
) {
    CheckBoxWithExpandableTip(
        checked = config.blackFlowFastFlight,
        onCheckedChange = { onConfigChange(config.copy(blackFlowFastFlight = it)) },
        label = stringResource(R.string.panel_roguelike_blackflow_fast_flight),
        tipText = stringResource(R.string.panel_roguelike_blackflow_flight_tip),
        subtitle = stringResource(R.string.panel_roguelike_blackflow_flight_requirement)
    )
    SelectableChipGroup(
        label = stringResource(R.string.panel_roguelike_blackflow_ending),
        selectedValue = config.blackFlowEnding,
        options = localizedBlackFlowEndingOptions(),
        onSelected = { onConfigChange(config.copy(blackFlowEnding = it)) },
        enabled = !config.blackFlowFastFlight,
        labelFontWeight = FontWeight.Medium
    )
    if (config.blackFlowFastFlight) {
        BlackFlowHint(R.string.panel_roguelike_blackflow_ending_flight_tip)
    }
}

/** 黑流刷开局：准备局要培育的动物、目标局期望奖励、准备局分队 */
@Composable
fun BlackFlowStartRunSettings(
    config: RoguelikeConfig,
    onConfigChange: (RoguelikeConfig) -> Unit
) {
    val animalOptions = localizedBlackFlowStartAnimalOptions()
    StartAnimalGroup(
        label = R.string.panel_roguelike_blackflow_start_animal_1,
        selected = config.blackFlowStartAnimal1,
        other = config.blackFlowStartAnimal2,
        options = animalOptions,
        onSelected = { onConfigChange(config.copy(blackFlowStartAnimal1 = it)) }
    )
    StartAnimalGroup(
        label = R.string.panel_roguelike_blackflow_start_animal_2,
        selected = config.blackFlowStartAnimal2,
        other = config.blackFlowStartAnimal1,
        options = animalOptions,
        onSelected = { onConfigChange(config.copy(blackFlowStartAnimal2 = it)) }
    )

    // 两组勾选全部算候选，任意一项命中即停
    val (serpentRewards, normalRewards) = RoguelikeBlackFlowStartReward.entries.partition { it.serpent }
    SectionLabel(R.string.panel_roguelike_blackflow_start_rewards)
    BlackFlowHint(R.string.panel_roguelike_blackflow_start_rewards_tip)
    normalRewards.forEach { StartRewardCheckBox(config, it, enabled = true, onConfigChange) }

    // 没选羽蛇时勾选保留但不生效
    SectionLabel(R.string.panel_roguelike_blackflow_serpent_rewards)
    if (!config.blackFlowHasSerpent) {
        BlackFlowHint(R.string.panel_roguelike_blackflow_serpent_rewards_inactive)
    }
    serpentRewards.forEach {
        StartRewardCheckBox(config, it, enabled = config.blackFlowHasSerpent, onConfigChange)
    }

    CheckBoxWithExpandableTip(
        checked = config.blackFlowStartRunFastFlight,
        onCheckedChange = { onConfigChange(config.copy(blackFlowStartRunFastFlight = it)) },
        label = stringResource(R.string.panel_roguelike_blackflow_fast_flight),
        tipText = stringResource(R.string.panel_roguelike_blackflow_prep_flight_tip),
        subtitle = stringResource(R.string.panel_roguelike_blackflow_flight_requirement)
    )
    // 快速飞时显示固定的特勤分队，原设置保留
    val flight = config.blackFlowStartRunFlightActive
    RoguelikeSquadButtonGroup(
        label = stringResource(R.string.panel_roguelike_blackflow_prep_squad),
        selectedValue = if (flight) RoguelikeUi.BLACK_FLOW_FLIGHT_SQUAD else config.collectibleModeSquad,
        theme = config.theme,
        mode = config.mode,
        onValueChange = { onConfigChange(config.copy(collectibleModeSquad = it)) },
        enabled = !flight
    )
}

// 羽蛇最多一只，另一格已选羽蛇时本格的羽蛇不可选
@Composable
private fun StartAnimalGroup(
    @StringRes label: Int,
    selected: RoguelikeBlackFlowStartAnimal,
    other: RoguelikeBlackFlowStartAnimal,
    options: List<Pair<RoguelikeBlackFlowStartAnimal, String>>,
    onSelected: (RoguelikeBlackFlowStartAnimal) -> Unit
) {
    SelectableChipGroup(
        label = stringResource(label),
        selectedValue = selected,
        options = options,
        onSelected = onSelected,
        isItemEnabled = {
            it != RoguelikeBlackFlowStartAnimal.FeatheredSerpent ||
                    other != RoguelikeBlackFlowStartAnimal.FeatheredSerpent
        },
        labelFontWeight = FontWeight.Medium
    )
    localizedBlackFlowStartAnimalTip(selected)?.let { BlackFlowHint(it) }
}

@Composable
private fun StartRewardCheckBox(
    config: RoguelikeConfig,
    reward: RoguelikeBlackFlowStartReward,
    enabled: Boolean,
    onConfigChange: (RoguelikeConfig) -> Unit
) {
    CheckBoxWithLabel(
        checked = reward in config.blackFlowStartRewards,
        onCheckedChange = { checked ->
            val rewards = if (checked) config.blackFlowStartRewards + reward
            else config.blackFlowStartRewards - reward
            onConfigChange(config.copy(blackFlowStartRewards = rewards))
        },
        label = localizedBlackFlowStartRewardLabel(reward),
        subtitle = localizedBlackFlowStartRewardTip(reward),
        enabled = enabled,
        modifier = Modifier.padding(start = 8.dp)
    )
}

@Composable
private fun SectionLabel(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun BlackFlowHint(@StringRes text: Int) = BlackFlowHint(stringResource(text))

@Composable
private fun BlackFlowHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
