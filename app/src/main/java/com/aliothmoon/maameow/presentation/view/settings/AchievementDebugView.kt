package com.aliothmoon.maameow.presentation.view.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.achievement.AchievementField
import com.aliothmoon.maameow.presentation.components.InfoCard
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.viewmodel.AchievementEffect
import com.aliothmoon.maameow.presentation.viewmodel.AchievementEvent
import com.aliothmoon.maameow.presentation.viewmodel.AchievementViewModel
import com.aliothmoon.maameow.theme.MaaDesignTokens
import org.koin.androidx.compose.koinViewModel

@Composable
fun AchievementDebugView(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: AchievementViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val achievements = uiState.allAchievements
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf(achievements.firstOrNull()?.definition?.id.orEmpty()) }

    LaunchedEffect(achievements) {
        if (achievements.isNotEmpty() && achievements.none { it.definition.id == selectedId }) {
            selectedId = achievements.first().definition.id
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val resId = when (effect) {
                AchievementEffect.Unlocked -> R.string.achievement_debug_unlock_done
                AchievementEffect.UnlockedAll -> R.string.achievement_debug_unlock_all_done
                AchievementEffect.Cleared -> R.string.achievement_debug_clear_done
            }
            Toast.makeText(context, resId, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.achievement_debug_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = { navController.navigateUp() },
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                horizontal = MaaDesignTokens.Spacing.listHorizontal,
                vertical = MaaDesignTokens.Spacing.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.md),
        ) {
            item {
                InfoCard(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.md)) {
                        Text(
                            text = stringResource(R.string.achievement_debug_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.achievement_debug_select_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            achievements.forEach { state ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = state.definition.id == selectedId,
                                            onClick = { selectedId = state.definition.id },
                                        )
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    RadioButton(
                                        selected = state.definition.id == selectedId,
                                        onClick = { selectedId = state.definition.id },
                                    )
                                    Text(
                                        text = "${state.definition.id} - ${
                                            achievementText(state.definition.id, AchievementField.TITLE)
                                        }",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedId.isNotBlank(),
                            onClick = { viewModel.onEvent(AchievementEvent.Unlock(selectedId)) },
                        ) {
                            Text(stringResource(R.string.achievement_debug_unlock))
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = achievements.any { !it.unlocked },
                            onClick = { viewModel.onEvent(AchievementEvent.UnlockAll) },
                        ) {
                            Text(stringResource(R.string.achievement_debug_unlock_all))
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.onEvent(AchievementEvent.ClearAllRecords) },
                        ) {
                            Text(stringResource(R.string.achievement_debug_clear_all))
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
