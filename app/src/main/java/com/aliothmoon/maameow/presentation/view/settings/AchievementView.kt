package com.aliothmoon.maameow.presentation.view.settings

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.achievement.AchievementCategory
import com.aliothmoon.maameow.data.achievement.AchievementField
import com.aliothmoon.maameow.data.achievement.AchievementState
import com.aliothmoon.maameow.presentation.components.InfoCard
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.viewmodel.AchievementEvent
import com.aliothmoon.maameow.presentation.viewmodel.AchievementViewModel
import com.aliothmoon.maameow.theme.MaaDesignTokens
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun AchievementView(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: AchievementViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.onEvent(AchievementEvent.ScreenOpened)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.achievement_title),
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
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onEvent(AchievementEvent.UpdateSearchText(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.achievement_search_hint)) },
                )
            }
            item {
                Text(
                    text = stringResource(
                        R.string.achievement_unlocked_count,
                        uiState.unlockedCount,
                        uiState.totalCount
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(uiState.achievements, key = { it.definition.id }) {
                AchievementCard(it)
            }
            if (uiState.achievements.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.achievement_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AchievementCard(achievement: AchievementState) {
    val color = achievementColor(achievement)
    val dateFormat = remember { DateFormat.getDateTimeInstance() }
    InfoCard(contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.EmojiEvents,
                contentDescription = null,
                tint = color,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.rowTitleGap),
            ) {
                Text(
                    text = if (achievement.unlocked) {
                        achievementText(achievement.definition.id, AchievementField.TITLE)
                    } else {
                        stringResource(R.string.achievement_locked_title)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = if (achievement.unlocked) {
                        achievementText(achievement.definition.id, AchievementField.DESC)
                    } else {
                        stringResource(R.string.achievement_locked_desc)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (!achievement.definition.hidden || achievement.unlocked) {
                        achievementText(achievement.definition.id, AchievementField.CONDITION)
                    } else {
                        stringResource(R.string.achievement_locked_condition)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (!achievement.unlocked && achievement.progressive) {
                    LinearProgressIndicator(
                        progress = {
                            (achievement.progress.toFloat() / achievement.definition.target).coerceIn(
                                0f,
                                1f
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(
                            R.string.achievement_progress,
                            achievement.progress,
                            achievement.definition.target,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (achievement.unlocked && achievement.unlockedAtMillis != null) {
                    Text(
                        text = stringResource(
                            R.string.achievement_unlocked_at,
                            dateFormat.format(Date(achievement.unlockedAtMillis)),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
private fun achievementColor(achievement: AchievementState): Color = when {
    !achievement.unlocked -> MaterialTheme.colorScheme.outline
    achievement.definition.rare -> MaterialTheme.colorScheme.tertiary
    achievement.definition.hidden -> MaterialTheme.colorScheme.secondary
    achievement.definition.category == AchievementCategory.BUG_RELATED -> MaterialTheme.colorScheme.error
    achievement.definition.category == AchievementCategory.AUTO_BATTLE -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.primary
}
