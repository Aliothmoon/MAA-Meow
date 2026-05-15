package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.ReclamationConfig
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.presentation.components.SelectableChipGroup
import kotlinx.coroutines.launch

@Composable
fun ReclamationConfigPanel(config: ReclamationConfig, onConfigChange: (ReclamationConfig) -> Unit) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 4.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Tab 行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.common_tab_general),
                style = MaterialTheme.typography.bodyMedium,
                color = if (pagerState.currentPage == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable {
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                }
            )
            Text(
                text = stringResource(R.string.common_tab_advanced),
                style = MaterialTheme.typography.bodyMedium,
                color = if (pagerState.currentPage == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable {
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                }
            )
            Text(
                text = stringResource(R.string.common_description),
                style = MaterialTheme.typography.bodyMedium,
                color = if (pagerState.currentPage == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (pagerState.currentPage == 2) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable {
                    coroutineScope.launch { pagerState.animateScrollToPage(2) }
                }
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
        )

        // Tab 内容区
        HorizontalPager(
            pageSize = PageSize.Fill,
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            userScrollEnabled = true
        ) { page ->
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                when (page) {
                    // 常规设置 Tab
                    0 -> {
                        val isRelaunchAnchor = config.theme == "RelaunchAnchor"
                        item {
                            ReclamationButtonGroup(
                                label = stringResource(R.string.panel_reclamation_theme),
                                options = localizedReclamationThemeOptions(),
                                selectedValue = config.theme,
                                onValueChange = {
                                    val theme = it as String
                                    val updated = if (theme == "RelaunchAnchor") {
                                        config.copy(theme = theme, mode = 0, clearStore = false)
                                    } else {
                                        config.copy(theme = theme)
                                    }
                                    onConfigChange(updated)
                                }
                            )
                        }
                        item {
                            AnimatedVisibility(visible = !isRelaunchAnchor) {
                                ReclamationButtonGroup(
                                    label = stringResource(R.string.panel_reclamation_strategy),
                                    options = localizedReclamationModeOptions(),
                                    selectedValue = config.mode,
                                    onValueChange = { onConfigChange(config.copy(mode = it as Int)) }
                                )
                            }
                        }
                        item {
                            AnimatedVisibility(visible = !isRelaunchAnchor && config.mode == 0) {
                                CheckBoxWithLabel(
                                    checked = config.clearStore,
                                    onCheckedChange = { onConfigChange(config.copy(clearStore = it)) },
                                    label = stringResource(R.string.panel_reclamation_clear_store)
                                )
                            }
                        }
                        item {
                            AnimatedVisibility(visible = !isRelaunchAnchor && config.mode == 1) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.panel_reclamation_archive_tip),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 高级设置 Tab
                    1 -> {
                        val isArchiveMode = config.mode == 1
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    stringResource(R.string.panel_reclamation_tool_name),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                ITextField(
                                    value = config.toolToCraft,
                                    onValueChange = { onConfigChange(config.copy(toolToCraft = it)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = stringResource(R.string.panel_reclamation_tool_placeholder),
                                    singleLine = false,
                                    enabled = isArchiveMode
                                )
                                Text(
                                    stringResource(R.string.panel_reclamation_tool_separator_tip),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        item {
                            ReclamationButtonGroup(
                                label = stringResource(R.string.panel_reclamation_increment_mode),
                                options = localizedReclamationIncrementModeOptions(),
                                selectedValue = config.incrementMode,
                                onValueChange = { onConfigChange(config.copy(incrementMode = it as Int)) },
                                enabled = isArchiveMode
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
                                    onValueChange = {
                                        val newValue = it.toIntOrNull()
                                        if (newValue != null && newValue > 0) {
                                            onConfigChange(config.copy(maxCraftCountPerRound = newValue))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = "16",
                                    singleLine = true,
                                    enabled = isArchiveMode
                                )
                            }
                        }
                    }

                    // 说明 Tab
                    2 -> {
                        if (config.theme == "RelaunchAnchor") {
                            item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.panel_reclamation_relaunch_anchor_tip),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        } else {
                            item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.panel_reclamation_notice),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        stringResource(R.string.panel_reclamation_no_save_title),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        stringResource(R.string.panel_reclamation_no_save_line1),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        stringResource(R.string.panel_reclamation_no_save_line2),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        stringResource(R.string.panel_reclamation_no_save_line3),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        stringResource(R.string.panel_reclamation_no_save_line4),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        stringResource(R.string.panel_reclamation_no_save_line5),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        stringResource(R.string.panel_reclamation_with_save_title),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        stringResource(R.string.panel_reclamation_with_save_line1),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        stringResource(R.string.panel_reclamation_with_save_line2),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        stringResource(R.string.panel_reclamation_with_save_line3),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReclamationButtonGroup(
    label: String,
    options: List<Pair<Any, String>>,
    selectedValue: Any,
    onValueChange: (Any) -> Unit,
    enabled: Boolean = true
) {
    SelectableChipGroup(
        label = label,
        selectedValue = selectedValue,
        options = options,
        onSelected = onValueChange,
        enabled = enabled,
        labelFontWeight = FontWeight.Medium
    )
}

@Composable
private fun localizedReclamationThemeOptions(): List<Pair<Any, String>> {
    return ReclamationConfig.THEME_KEYS.map { theme ->
        theme to when (theme) {
            "Tales" -> stringResource(R.string.panel_reclamation_theme_tales)
            "Fire" -> stringResource(R.string.panel_reclamation_theme_fire)
            "RelaunchAnchor" -> stringResource(R.string.panel_reclamation_theme_relaunch_anchor)
            else -> theme
        }
    }
}

@Composable
private fun localizedReclamationModeOptions(): List<Pair<Any, String>> {
    return ReclamationConfig.MODE_VALUES.map { mode ->
        mode to when (mode) {
            0 -> stringResource(R.string.panel_reclamation_mode_no_save)
            1 -> stringResource(R.string.panel_reclamation_mode_with_save)
            else -> mode.toString()
        }
    }
}

@Composable
private fun localizedReclamationIncrementModeOptions(): List<Pair<Any, String>> {
    return ReclamationConfig.INCREMENT_MODE_VALUES.map { mode ->
        mode to when (mode) {
            0 -> stringResource(R.string.panel_reclamation_increment_click)
            1 -> stringResource(R.string.panel_reclamation_increment_hold)
            else -> mode.toString()
        }
    }
}
