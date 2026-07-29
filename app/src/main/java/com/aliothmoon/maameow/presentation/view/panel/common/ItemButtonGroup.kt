package com.aliothmoon.maameow.presentation.view.panel.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aliothmoon.maameow.presentation.components.SelectableChipGroup

/**
 * 材料选择按钮组
 */
@Composable
fun ItemButtonGroup(
    modifier: Modifier = Modifier,
    label: String,
    selectedValue: String,
    items: List<String>,
    onItemSelected: (String) -> Unit,
    displayMapper: (String) -> String = { it },
) {
    SelectableChipGroup(
        label = label,
        selectedValue = selectedValue,
        options = items.map { it to displayMapper(it) },
        onSelected = onItemSelected,
        modifier = modifier
    )
}
