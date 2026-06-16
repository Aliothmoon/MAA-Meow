package com.aliothmoon.maameow.presentation.view.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.presentation.components.InfoCard
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.theme.MaaDesignTokens
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun AchievementDebugView(
    navController: NavController,
    repository: AchievementRepository = koinInject(),
) {
    val achievements by repository.achievements.collectAsStateWithLifecycle()
    val languageTag = LocalConfiguration.current.locales[0]?.toLanguageTag().orEmpty()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf(achievements.firstOrNull()?.definition?.id.orEmpty()) }

    LaunchedEffect(achievements) {
        if (achievements.isNotEmpty() && achievements.none { it.definition.id == selectedId }) {
            selectedId = achievements.first().definition.id
        }
    }

    Scaffold(
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InfoCard(
                    title = "",
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    contentPadding = PaddingValues(MaaDesignTokens.Card.innerPadding),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(R.string.achievement_debug_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { expanded = true },
                            ) {
                                Text(selectedId.ifBlank { stringResource(R.string.achievement_debug_select_label) })
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                            ) {
                                achievements.forEach { state ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "${state.definition.id} - ${state.definition.title.resolve(languageTag)}",
                                                maxLines = 1,
                                            )
                                        },
                                        onClick = {
                                            selectedId = state.definition.id
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedId.isNotBlank(),
                            onClick = {
                                coroutineScope.launch {
                                    repository.unlock(selectedId)
                                    Toast.makeText(context, R.string.achievement_debug_unlock_done, Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) {
                            Text(stringResource(R.string.achievement_debug_unlock))
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = achievements.any { !it.unlocked },
                            onClick = {
                                coroutineScope.launch {
                                    repository.unlockAll()
                                    Toast.makeText(context, R.string.achievement_debug_unlock_all_done, Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) {
                            Text(stringResource(R.string.achievement_debug_unlock_all))
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                coroutineScope.launch {
                                    repository.clearAllRecords()
                                    Toast.makeText(context, R.string.achievement_debug_clear_done, Toast.LENGTH_SHORT).show()
                                }
                            },
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
