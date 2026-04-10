package com.aliothmoon.maameow.presentation.view.panel.roguelike

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.RoguelikeConfig
import com.aliothmoon.maameow.domain.enums.RoguelikeMode
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.domain.enums.UiUsageConstants.Roguelike as RoguelikeUi

@Composable
fun ThemeSpecificSettings(
    config: RoguelikeConfig,
    onConfigChange: (RoguelikeConfig) -> Unit
) {
    when (config.theme) {
        "Mizuki" -> {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                stringResource(R.string.shuiyue_settings),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            CheckBoxWithLabel(
                checked = config.refreshTraderWithDice,
                onCheckedChange = { onConfigChange(config.copy(refreshTraderWithDice = it)) },
                label = stringResource(R.string.refresh_shop_scale)
            )
        }

        "Sami" -> {
            // WPF: Visibility="RoguelikeSquadIsFoldartal"
            val squadIsFoldartal = RoguelikeUi.isSquadFoldartal(
                config.squad, config.mode, config.theme
            )

            val isCollectibleAvailable = config.mode == RoguelikeMode.Collectible

            if (squadIsFoldartal || isCollectibleAvailable) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Text(
                    stringResource(R.string.sami_settings),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            // WPF: Visibility="RoguelikeMode == Collectible AND theme == Sami"
            if (isCollectibleAvailable) {
                CheckBoxWithLabel(
                    checked = config.firstFloorFoldartal,
                    onCheckedChange = { onConfigChange(config.copy(firstFloorFoldartal = it)) },
                    label = stringResource(R.string.target_cipher_first_floor)
                )

                AnimatedVisibility(visible = config.firstFloorFoldartal) {
                    ITextField(
                        value = config.firstFloorFoldartals,
                        onValueChange = { onConfigChange(config.copy(firstFloorFoldartals = it)) },
                        placeholder = stringResource(R.string.cipher_name),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }


            if (squadIsFoldartal) {
                CheckBoxWithLabel(
                    checked = config.newSquad2StartingFoldartal,
                    onCheckedChange = { onConfigChange(config.copy(newSquad2StartingFoldartal = it)) },
                    label = stringResource(R.string.start_cipher_with_life)
                )

                AnimatedVisibility(visible = config.newSquad2StartingFoldartal) {
                    Column {
                        ITextField(
                            value = config.newSquad2StartingFoldartals,
                            onValueChange = { onConfigChange(config.copy(newSquad2StartingFoldartals = it)) },
                            placeholder = stringResource(R.string.max_three_sep),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        "JieGarden" -> {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            CheckBoxWithLabel(
                checked = config.startWithSeed,
                onCheckedChange = { onConfigChange(config.copy(startWithSeed = it)) },
                label = stringResource(R.string.use_specified_seed)
            )

            AnimatedVisibility(visible = config.startWithSeed) {
                ITextField(
                    value = config.seed,
                    onValueChange = { onConfigChange(config.copy(seed = it)) },
                    placeholder = stringResource(R.string.seed_format),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
