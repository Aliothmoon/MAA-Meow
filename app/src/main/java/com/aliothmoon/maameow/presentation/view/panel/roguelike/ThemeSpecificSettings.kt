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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                color = Color.LightGray,
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                "水月专用设置",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            CheckBoxWithLabel(
                checked = config.refreshTraderWithDice,
                onCheckedChange = { onConfigChange(config.copy(refreshTraderWithDice = it)) },
                label = "刷新商店（指路鳞）"
            )
        }

        "Sami" -> {
            HorizontalDivider(
                color = Color.LightGray,
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                "萨米专用设置",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            // WPF: Visibility="RoguelikeMode == Collectible AND theme == Sami" (line 239)
            if (config.mode == RoguelikeMode.Collectible) {
                CheckBoxWithLabel(
                    checked = config.firstFloorFoldartal,
                    onCheckedChange = { onConfigChange(config.copy(firstFloorFoldartal = it)) },
                    label = "凹第一层远见密文板，不进行作战"
                )

                AnimatedVisibility(visible = config.firstFloorFoldartal) {
                    ITextField(
                        value = config.firstFloorFoldartals,
                        onValueChange = { onConfigChange(config.copy(firstFloorFoldartals = it)) },
                        placeholder = "密文板名称",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp)
                    )
                }
            }

            // WPF: Visibility="RoguelikeSquadIsFoldartal" (line 257)
            val squadIsFoldartal = RoguelikeUi.isSquadFoldartal(
                config.squad, config.mode, config.theme
            )
            if (squadIsFoldartal) {
                CheckBoxWithLabel(
                    checked = config.newSquad2StartingFoldartal,
                    onCheckedChange = { onConfigChange(config.copy(newSquad2StartingFoldartal = it)) },
                    label = "生活队凹开局密文板"
                )

                AnimatedVisibility(visible = config.newSquad2StartingFoldartal) {
                    Column(modifier = Modifier.padding(start = 24.dp)) {
                        ITextField(
                            value = config.newSquad2StartingFoldartals,
                            onValueChange = { onConfigChange(config.copy(newSquad2StartingFoldartals = it)) },
                            placeholder = "最多写三个，并用英文分号 ; 隔开",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        "JieGarden" -> {
            HorizontalDivider(
                color = Color.LightGray,
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            CheckBoxWithLabel(
                checked = config.startWithSeed,
                onCheckedChange = { onConfigChange(config.copy(startWithSeed = it)) },
                label = "使用指定种子开局"
            )

            AnimatedVisibility(visible = config.startWithSeed) {
                ITextField(
                    value = config.seed,
                    onValueChange = { onConfigChange(config.copy(seed = it)) },
                    placeholder = "格式: xxx,rogue_x,x",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp)
                )
            }
        }
    }
}