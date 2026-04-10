package com.aliothmoon.maameow.presentation.view.background

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.service.AppWatchdog
import org.koin.compose.koinInject

@Composable
fun VirtualDisplayPreview(
    modifier: Modifier = Modifier,
    isRunning: Boolean,
    isSurfaceAvailable: Boolean,
    onClick: () -> Unit,
    appWatchdog: AppWatchdog = koinInject(),
    content: @Composable () -> Unit
) {
    val watchdogState by appWatchdog.state.collectAsStateWithLifecycle()
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val maxWidth = maxWidth
        val maxHeight = maxHeight

        val aspectRatio = 16f / 9f
        val widthFromHeight = maxHeight * aspectRatio
        val heightFromWidth = maxWidth / aspectRatio

        val (cardWidth, cardHeight) = if (widthFromHeight <= maxWidth) {
            widthFromHeight to maxHeight
        } else {
            maxWidth to heightFromWidth
        }

        Card(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .clickable(onClick = onClick),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                content()

                when {
                    !isRunning -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.pending),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    !isSurfaceAvailable -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.waiting_for_screen),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 看门狗状态指示器
                val (dotColor, label) = when (watchdogState) {
                    AppWatchdog.WatchdogState.WATCHING -> Color(0xFF4CAF50) to stringResource(R.string.game_running)
                    AppWatchdog.WatchdogState.APP_DIED -> Color(0xFFF44336) to stringResource(R.string.game_terminated)
                    AppWatchdog.WatchdogState.IDLE -> Color(0xFF9E9E9E) to stringResource(R.string.standby)
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}
