package com.aliothmoon.maameow.schedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.presentation.components.AppIcon
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.search.ProvideSettingSearch
import com.aliothmoon.maameow.presentation.search.SettingSearchTarget
import com.aliothmoon.maameow.theme.MaaDesignTokens
import org.koin.androidx.compose.koinViewModel

@Composable
fun ScheduleAppBlacklistView(
    navController: NavController,
    viewModel: ScheduleAppBlacklistViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val runMode by viewModel.runMode.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.schedule_app_blacklist_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = { navController.navigateUp() },
            )
        },
    ) { paddingValues ->
        val contentColor = MaterialTheme.colorScheme.onSurface

        ProvideSettingSearch {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding()),
                contentPadding = PaddingValues(
                    start = MaaDesignTokens.Spacing.listHorizontal,
                    end = MaaDesignTokens.Spacing.listHorizontal,
                    top = MaaDesignTokens.Spacing.sm,
                    bottom = MaaDesignTokens.Spacing.sm + paddingValues.calculateBottomPadding(),
                ),
            ) {
                item(key = "desc") {
                    SettingSearchTarget(R.string.schedule_app_blacklist_title) {
                        Text(
                            text = stringResource(R.string.schedule_app_blacklist_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = MaaDesignTokens.Spacing.sm),
                        )
                    }
                    if (runMode == RunMode.FOREGROUND) {
                        Text(
                            text = stringResource(R.string.schedule_app_blacklist_foreground_inactive),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(bottom = MaaDesignTokens.Spacing.sm),
                        )
                    }
                }

                when (val current = state) {
                    ScheduleAppBlacklistViewModel.UiState.Loading -> item(key = "loading") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.settings_shizuku_launch_app_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    ScheduleAppBlacklistViewModel.UiState.Failed -> item(key = "failed") {
                        Text(
                            text = stringResource(R.string.settings_shizuku_launch_app_picker_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    is ScheduleAppBlacklistViewModel.UiState.Loaded -> {
                        item(key = "search") {
                            ITextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = stringResource(R.string.settings_shizuku_launch_app_search_hint),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = stringResource(
                                    R.string.schedule_app_blacklist_selected,
                                    current.checkedCount,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    top = MaaDesignTokens.Spacing.md,
                                    bottom = MaaDesignTokens.Spacing.xs,
                                ),
                            )
                        }
                        val keyword = query.trim()
                        val rows = current.rows.filter {
                            keyword.isEmpty() ||
                                    it.label.contains(keyword, ignoreCase = true) ||
                                    it.packageName.contains(keyword, ignoreCase = true)
                        }
                        if (rows.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    text = stringResource(R.string.settings_shizuku_launch_app_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                            }
                        } else {
                            items(rows, key = { it.packageName }) { row ->
                                BlacklistAppRow(
                                    row = row,
                                    onToggle = { viewModel.setBlacklisted(row.packageName, it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlacklistAppRow(
    row: ScheduleAppBlacklistViewModel.AppRow,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .toggleable(value = row.checked, role = Role.Checkbox, onValueChange = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(row.packageName)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (row.label != row.packageName) {
                Text(
                    text = row.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Checkbox(checked = row.checked, onCheckedChange = null)
    }
}
