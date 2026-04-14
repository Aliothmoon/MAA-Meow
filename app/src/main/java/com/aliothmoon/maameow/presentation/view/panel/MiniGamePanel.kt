package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.resource.MiniGameTextRegistry
import com.aliothmoon.maameow.presentation.viewmodel.MiniGameDelegate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MiniGamePanel(
    modifier: Modifier = Modifier,
    delegate: MiniGameDelegate
) {
    val state by delegate.state.collectAsStateWithLifecycle()
    val miniGames by delegate.miniGames.collectAsStateWithLifecycle()

    val currentGame = delegate.findGame(state.selectedTaskName)
    val tip = currentGame?.tip?.takeIf { it.isNotBlank() } ?: MiniGameTextRegistry.EMPTY_TIP
    val isUnsupported = currentGame?.isUnsupported == true
    val currentGameDisplay = currentGame?.display ?: ""

    val tabTitleTextStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = 13.sp,
        lineHeight = 16.sp
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 4.dp)),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.panel_mini_game_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        // 任务选择 - 卡片网格
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.panel_mini_game_name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                miniGames.chunked(3).forEach { rowGames ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        rowGames.forEach { game ->
                            val selected = state.selectedTaskName == game.value
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (game.isUnsupported) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (game.isUnsupported && selected) {
                                        MaterialTheme.colorScheme.error
                                    } else if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 36.dp)
                                    .clickable { delegate.onTaskSelected(game.value) }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = game.display,
                                        style = tabTitleTextStyle,
                                        color = if (game.isUnsupported) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                        repeat(3 - rowGames.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Tip 提示
        if (tip.isNotBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isUnsupported) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = if (isUnsupported) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        if (currentGameDisplay.isNotBlank()) {
                            Text(
                                text = currentGameDisplay,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isUnsupported) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Text(
                            text = tip,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isUnsupported) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }

        // 隐秘战线配置
        if (delegate.isSecretFront(state.selectedTaskName)) {
            item {
                HorizontalDivider()
            }

            // 结局选择
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.panel_mini_game_ending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MiniGameDelegate.ENDINGS.forEach { ending ->
                            FilterChip(
                                selected = state.selectedEnding == ending,
                                onClick = { delegate.onEndingSelected(ending) },
                                label = {
                                    Text(
                                        text = ending,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                shape = RoundedCornerShape(8.dp),
                            )
                        }
                    }
                }
            }

            // 事件选择 - 卡片网格
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.panel_mini_game_preferred_events),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        MiniGameDelegate.EVENTS.forEach { (value, display) ->
                            val selected = state.selectedEvent == value
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                modifier = Modifier
                                    .clickable { delegate.onEventSelected(value) }
                            ) {
                                Text(
                                    text = display,
                                    style = tabTitleTextStyle,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

    }
}
