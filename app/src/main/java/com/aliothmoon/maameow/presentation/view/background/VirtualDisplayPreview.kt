package com.aliothmoon.maameow.presentation.view.background

import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.service.AppWatchdog
import org.koin.compose.koinInject

@Composable
internal fun VirtualDisplayPreview(
    modifier: Modifier = Modifier,
    isRunning: Boolean,
    isSurfaceAvailable: Boolean,
    onClick: () -> Unit,
    onRenderedCardBounds: (PreviewCardBounds) -> Unit = {},
    appWatchdog: AppWatchdog = koinInject(),
    content: @Composable () -> Unit
) {
    val watchdogState by appWatchdog.state.collectAsStateWithLifecycle()
    // 容器与卡片谁先完成布局不确定，两边都落一次再换算相对坐标
    val containerCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val cardCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
    fun reportCardBounds() {
        val container = containerCoordinates.value
        val card = cardCoordinates.value
        if (container == null || card == null || !container.isAttached || !card.isAttached) return
        if (card.size.width <= 0 || card.size.height <= 0) return
        val topLeft = runCatching { container.localPositionOf(card, Offset.Zero) }.getOrNull() ?: return
        onRenderedCardBounds(
            PreviewCardBounds(
                left = topLeft.x,
                top = topLeft.y,
                width = card.size.width.toFloat(),
                height = card.size.height.toFloat(),
            )
        )
    }
    BoxWithConstraints(
        modifier = modifier.onGloballyPositioned { coords ->
            containerCoordinates.value = coords
            reportCardBounds()
        },
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
            onClick = onClick,
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .onGloballyPositioned { coords ->
                    cardCoordinates.value = coords
                    reportCardBounds()
                },
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
                                text = stringResource(R.string.virtual_display_pending),
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
                                text = stringResource(R.string.virtual_display_waiting_surface),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 看门狗状态指示器
                val (dotColor, label) = when (watchdogState) {
                    AppWatchdog.WatchdogState.WATCHING -> {
                        Color(0xFF4CAF50) to stringResource(R.string.virtual_display_game_running)
                    }

                    AppWatchdog.WatchdogState.APP_DIED -> {
                        Color(0xFFF44336) to stringResource(R.string.virtual_display_game_stopped)
                    }

                    AppWatchdog.WatchdogState.IDLE -> {
                        Color(0xFF9E9E9E) to stringResource(R.string.virtual_display_idle)
                    }
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
