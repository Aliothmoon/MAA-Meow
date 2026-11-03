package com.aliothmoon.maameow.presentation.view.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.service.AppWatchdog
import org.koin.compose.koinInject

/** 预览收起成缩略图，放不下的角标要藏起来 */
val LocalPreviewCompact = compositionLocalOf { false }

/** 收起后窄栏高度 */
val PreviewCollapsedHeight = 56.dp

/**
 * @param expandFraction 1 展开、0 收起成缩略图窄栏，拖动把手时连续变化
 * @param cardModifier   挂在 16:9 画面卡片上，画中画取的是它的位置
 */
@Composable
fun VirtualDisplayPreview(
    modifier: Modifier = Modifier,
    isRunning: Boolean,
    isSurfaceAvailable: Boolean,
    onClick: () -> Unit,
    expandFraction: () -> Float = { 1f },
    cardModifier: Modifier = Modifier,
    appWatchdog: AppWatchdog = koinInject(),
    content: @Composable () -> Unit
) {
    val watchdogState by appWatchdog.state.collectAsStateWithLifecycle()
    val (dotColor, watchdogLabel) = when (watchdogState) {
        AppWatchdog.WatchdogState.WATCHING ->
            Color(0xFF4CAF50) to stringResource(R.string.virtual_display_game_running)

        AppWatchdog.WatchdogState.APP_DIED ->
            Color(0xFFF44336) to stringResource(R.string.virtual_display_game_stopped)

        AppWatchdog.WatchdogState.IDLE ->
            Color(0xFF9E9E9E) to stringResource(R.string.virtual_display_idle)
    }
    val pendingText = when {
        !isRunning -> stringResource(R.string.virtual_display_pending)
        !isSurfaceAvailable -> stringResource(R.string.virtual_display_waiting_surface)
        else -> null
    }

    BoxWithConstraints(modifier = modifier) {
        val aspectRatio = 16f / 9f
        val widthFromHeight = maxHeight * aspectRatio
        val heightFromWidth = maxWidth / aspectRatio

        val (cardWidth, cardHeight) = if (widthFromHeight <= maxWidth) {
            widthFromHeight to maxHeight
        } else {
            maxWidth to heightFromWidth
        }

        val fraction = expandFraction()
        val compact = fraction < 0.5f
        // 展开居中，收起滑到左侧给状态让位
        val cardLeft = (maxWidth - cardWidth) * fraction / 2
        val cardTop = (maxHeight - cardHeight) / 2

        Card(
            onClick = onClick,
            modifier = Modifier
                .offset(cardLeft, cardTop)
                .then(cardModifier)
                .width(cardWidth)
                .height(cardHeight),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalPreviewCompact provides compact) {
                    content()
                }

                if (!compact) {
                    if (pendingText != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pendingText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 看门狗状态指示器
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
                        StatusDot(dotColor)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = watchdogLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 收起后状态挪到缩略图右侧，越往下收越清楚
        if (compact) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = cardLeft + cardWidth + 12.dp)
                    .graphicsLayer { alpha = ((0.5f - fraction) * 4f).coerceIn(0f, 1f) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(dotColor)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = watchdogLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (pendingText != null) {
                    Text(
                        text = pendingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}
