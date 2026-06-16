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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.achievement.AchievementCategory
import com.aliothmoon.maameow.data.achievement.AchievementState
import com.aliothmoon.maameow.data.achievement.AchievementTextFormatter
import com.aliothmoon.maameow.data.achievement.getAchievementPlaceholder
import com.aliothmoon.maameow.presentation.components.InfoCard
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.viewmodel.AchievementViewModel
import com.aliothmoon.maameow.theme.MaaDesignTokens
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun AchievementView(
    navController: NavController,
    viewModel: AchievementViewModel = koinViewModel(),
) {
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    val searchText by viewModel.searchText.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val unlockedCount by viewModel.unlockedCount.collectAsStateWithLifecycle()
    val languageTag = LocalConfiguration.current.locales[0]?.toLanguageTag().orEmpty()

    LaunchedEffect(viewModel) {
        viewModel.onScreenOpened()
    }

    Scaffold(
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = viewModel::updateSearchText,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.achievement_search_hint)) },
                )
            }
            item {
                Text(
                    text = stringResource(R.string.achievement_unlocked_count, unlockedCount, totalCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(achievements, key = { it.definition.id }) { achievement ->
                AchievementCard(achievement, languageTag)
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AchievementCard(achievement: AchievementState, languageTag: String) {
    val color = achievementColor(achievement)
    val context = LocalContext.current
    InfoCard(
        title = "",
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentPadding = PaddingValues(MaaDesignTokens.Card.innerPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.EmojiEvents,
                contentDescription = null,
                tint = color,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (achievement.unlocked) {
                            achievement.definition.title.resolve(languageTag).formatAchievementPlaceholders(context)
                        } else {
                            stringResource(R.string.achievement_locked_title)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text("#${achievement.definition.releasePhase}") },
                    )
                }

                Text(
                    text = if (achievement.unlocked) {
                        achievement.definition.description.resolve(languageTag).formatAchievementPlaceholders(context)
                    } else {
                        stringResource(R.string.achievement_locked_desc)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (!achievement.definition.hidden || achievement.unlocked) {
                        achievement.definition.condition.resolve(languageTag).formatAchievementPlaceholders(context)
                    } else {
                        stringResource(R.string.achievement_locked_condition)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (!achievement.unlocked && achievement.progressive) {
                    LinearProgressIndicator(
                        progress = { (achievement.progress.toFloat() / achievement.definition.target).coerceIn(0f, 1f) },
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
                            DateFormat.getDateTimeInstance().format(Date(achievement.unlockedAtMillis)),
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

private fun String.formatAchievementPlaceholders(context: android.content.Context): String {
    return AchievementTextFormatter.formatPlaceholders(this) { key -> context.getAchievementPlaceholder(key) }
}
