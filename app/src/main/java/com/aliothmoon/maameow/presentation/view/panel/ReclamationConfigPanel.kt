package com.aliothmoon.maameow.presentation.view.panel

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.ReclamationConfig
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.presentation.components.SelectableChipGroup

/**
 * 工具箱「牛杂」里的生息演算设置，对齐上游 ReclamationUserControl
 * 改动以 transform 提交，由存储侧在事务内读改写，连续修改不会互相覆盖
 */
fun LazyListScope.reclamationSettingsItems(
    config: ReclamationConfig,
    clientType: String,
    onUpdate: ((ReclamationConfig) -> ReclamationConfig) -> Unit,
) {
    val isRelaunchAnchor = config.theme == "RelaunchAnchor"
    val noSave = !isRelaunchAnchor && config.mode == ReclamationConfig.MODE_PROSPERITY_NO_SAVE

    item {
        SelectableChipGroup(
            label = stringResource(R.string.panel_reclamation_theme),
            selectedValue = config.theme,
            options = localizedReclamationThemeOptions(clientType),
            onSelected = { theme ->
                onUpdate {
                    if (theme == "RelaunchAnchor") {
                        it.copy(theme = theme, mode = ReclamationConfig.MODE_RA1, clearStore = false)
                    } else {
                        it.copy(theme = theme, mode = ReclamationConfig.MODE_PROSPERITY_NO_SAVE)
                    }
                }
            },
            labelFontWeight = FontWeight.Medium
        )
    }
    item {
        SelectableChipGroup(
            label = stringResource(
                if (isRelaunchAnchor) R.string.panel_reclamation_relaunch_anchor_stage
                else R.string.panel_reclamation_strategy
            ),
            selectedValue = config.mode,
            options = if (isRelaunchAnchor) localizedRelaunchAnchorModeOptions() else localizedReclamationModeOptions(),
            onSelected = { mode -> onUpdate { it.copy(mode = mode) } },
            labelFontWeight = FontWeight.Medium
        )
    }
    if (noSave) {
        item {
            CheckBoxWithLabel(
                checked = config.clearStore,
                onCheckedChange = { checked -> onUpdate { it.copy(clearStore = checked) } },
                label = stringResource(R.string.panel_reclamation_clear_store)
            )
        }
    }

    when {
        isRelaunchAnchor -> item {
            val tipRes = when (config.mode) {
                ReclamationConfig.MODE_RA15 -> R.string.panel_reclamation_relaunch_anchor_tip_ra15
                ReclamationConfig.MODE_RA4 -> R.string.panel_reclamation_relaunch_anchor_tip_ra4
                else -> R.string.panel_reclamation_relaunch_anchor_tip_ra1
            }
            TipSurface(
                text = stringResource(tipRes),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }

        noSave -> {
            item {
                TipSurface(
                    text = stringResource(R.string.panel_reclamation_notice),
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Medium,
                )
            }
            item {
                TipLines(
                    R.string.panel_reclamation_no_save_title,
                    R.string.panel_reclamation_no_save_line1,
                    R.string.panel_reclamation_no_save_line2,
                    R.string.panel_reclamation_no_save_line3,
                    R.string.panel_reclamation_no_save_line4,
                    R.string.panel_reclamation_no_save_line5,
                    warningLine = R.string.panel_reclamation_no_save_line3,
                )
            }
        }

        // 有存档：组装道具刷点数
        else -> {
            item {
                TipSurface(
                    text = stringResource(R.string.panel_reclamation_archive_tip),
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            item {
                TipLines(
                    R.string.panel_reclamation_with_save_title,
                    R.string.panel_reclamation_with_save_line1,
                    R.string.panel_reclamation_with_save_line2,
                    R.string.panel_reclamation_with_save_line3,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.panel_reclamation_tool_name),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    ITextField(
                        value = config.toolToCraft,
                        onValueChange = { text -> onUpdate { it.copy(toolToCraft = text) } },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = stringResource(R.string.panel_reclamation_tool_placeholder),
                        singleLine = false,
                    )
                    Text(
                        stringResource(R.string.panel_reclamation_tool_separator_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                SelectableChipGroup(
                    label = stringResource(R.string.panel_reclamation_increment_mode),
                    selectedValue = config.incrementMode,
                    options = localizedReclamationIncrementModeOptions(),
                    onSelected = { mode -> onUpdate { it.copy(incrementMode = mode) } },
                    labelFontWeight = FontWeight.Medium
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.panel_reclamation_max_craft_count),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    ITextField(
                        value = config.maxCraftCountPerRound.toString(),
                        onValueChange = { text ->
                            val count = text.toIntOrNull()
                            if (count != null && count > 0) {
                                onUpdate { it.copy(maxCraftCountPerRound = count) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "16",
                        singleLine = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun TipSurface(text: String, container: Color, content: Color, fontWeight: FontWeight? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = content,
            fontWeight = fontWeight,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
private fun TipLines(
    @StringRes title: Int,
    @StringRes vararg lines: Int,
    @StringRes warningLine: Int? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        lines.forEach { line ->
            Text(
                stringResource(line),
                style = MaterialTheme.typography.bodySmall,
                color = if (line == warningLine) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun localizedReclamationThemeOptions(clientType: String): List<Pair<String, String>> =
    ReclamationConfig.THEME_KEYS.map { theme ->
        val label = when (theme) {
            "Tales" -> stringResource(R.string.panel_reclamation_theme_tales)
            "Fire" -> stringResource(R.string.panel_reclamation_theme_fire)
            "RelaunchAnchor" -> stringResource(R.string.panel_reclamation_theme_relaunch_anchor)
            else -> theme
        }
        theme to if (ReclamationConfig.isThemeAdapted(theme, clientType)) label
        else stringResource(R.string.common_theme_not_adapted, label)
    }

@Composable
private fun localizedReclamationModeOptions(): List<Pair<Int, String>> =
    ReclamationConfig.TALES_MODE_VALUES.map { mode ->
        mode to when (mode) {
            ReclamationConfig.MODE_PROSPERITY_NO_SAVE -> stringResource(R.string.panel_reclamation_mode_no_save)
            ReclamationConfig.MODE_PROSPERITY_IN_SAVE -> stringResource(R.string.panel_reclamation_mode_with_save)
            else -> mode.toString()
        }
    }

@Composable
private fun localizedRelaunchAnchorModeOptions(): List<Pair<Int, String>> =
    ReclamationConfig.RELAUNCH_ANCHOR_MODE_VALUES.map { mode ->
        mode to when (mode) {
            ReclamationConfig.MODE_RA1 -> stringResource(R.string.panel_reclamation_mode_ra1)
            ReclamationConfig.MODE_RA4 -> stringResource(R.string.panel_reclamation_mode_ra4)
            ReclamationConfig.MODE_RA15 -> stringResource(R.string.panel_reclamation_mode_ra15)
            else -> mode.toString()
        }
    }

@Composable
private fun localizedReclamationIncrementModeOptions(): List<Pair<Int, String>> =
    ReclamationConfig.INCREMENT_MODE_VALUES.map { mode ->
        mode to when (mode) {
            0 -> stringResource(R.string.panel_reclamation_increment_click)
            1 -> stringResource(R.string.panel_reclamation_increment_hold)
            else -> mode.toString()
        }
    }
