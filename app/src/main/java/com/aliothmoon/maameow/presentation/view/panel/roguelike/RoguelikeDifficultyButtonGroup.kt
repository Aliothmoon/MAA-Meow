package com.aliothmoon.maameow.presentation.view.panel.roguelike

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.aliothmoon.maameow.presentation.components.SelectableChipGroup

/**
 * 难度按钮组
 * 根据主题动态生成难度选项
 */
@Composable
fun RoguelikeDifficultyButtonGroup(
    label: String,
    selectedValue: Int,
    theme: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = localizedRoguelikeDifficultyOptions(theme)

    SelectableChipGroup(
        label = label,
        selectedValue = selectedValue,
        options = options,
        onSelected = onValueChange,
        modifier = modifier,
        labelFontWeight = FontWeight.Medium
    )
}
