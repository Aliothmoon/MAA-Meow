package com.aliothmoon.maameow.presentation.view.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.aliothmoon.maameow.theme.MaaDesignTokens
import org.koin.compose.koinInject

/** 预览收起成缩略图，放不下的角标要藏起来 */
val LocalPreviewCompact = compositionLocalOf { false }

/** 收起后窄栏高度 */
val PreviewCollapsedHeight = 60.dp

private val BarCorner = MaaDesignTokens.CornerRadius.card
private val BarInset = 6.dp

/**
 * @param gameOnDisplay  没任务在跑但游戏还在虚拟屏上，状态显示就绪
 * @param expandFraction 1 展开、0 收起成缩略图窄栏，拖动把手时连续变化
 * @param cardModifier   挂在 16:9 画面卡片上，画中画取的是它的位置
 */
@Composable
fun VirtualDisplayPreview(
    modifier: Modifier = Modifier,
    isRunning: Boolean,
    onClick: () -> Unit,
    gameOnDisplay: Boolean = false,
    expandFraction: () -> Float = { 1f },
    cardModifier: Modifier = Modifier,
    appWatchdog: AppWatchdog = koinInject(),
    content: @Composable () -> Unit
) {
    val watchdogState by appWatchdog.state.collectAsStateWithLifecycle()
    val gameReady = !isRunning && gameOnDisplay
    val (dotColor, watchdogLabel) = when {
        gameReady -> Color(0xFF2196F3) to stringResource(R.string.virtual_display_game_ready)

        watchdogState == AppWatchdog.WatchdogState.WATCHING ->
            Color(0xFF4CAF50) to stringResource(R.string.virtual_display_task_running)

        watchdogState == AppWatchdog.WatchdogState.APP_DIED ->
            Color(0xFFF44336) to stringResource(R.string.virtual_display_game_stopped)

        else -> Color(0xFF9E9E9E) to stringResource(R.string.virtual_display_idle)
    }

    BoxWithConstraints(modifier = modifier) {
        val fraction = expandFraction()
        val compact = fraction < 0.5f
        // 窄栏底板与状态只在收起后半程淡入
        val barAlpha = ((0.5f - fraction) * 4f).coerceIn(0f, 1f)
        // 收起时画面缩进窄栏内衬，圆角跟着内收保持同心
        val inset = BarInset * (1f - fraction)
        val availWidth = maxWidth - inset * 2
        val availHeight = maxHeight - inset * 2

        val aspectRatio = 16f / 9f
        val (cardWidth, cardHeight) = if (availHeight * aspectRatio <= availWidth) {
            availHeight * aspectRatio to availHeight
        } else {
            availWidth to availWidth / aspectRatio
        }
        // 展开居中，收起滑到左侧给状态让位
        val cardLeft = inset + (availWidth - cardWidth) * fraction / 2
        val cardTop = (maxHeight - cardHeight) / 2

        if (barAlpha > 0f) {
            Surface(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = barAlpha },
                shape = RoundedCornerShape(BarCorner),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.padding(start = cardLeft + cardWidth + 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(dotColor)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = watchdogLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Rounded.OpenInFull,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .size(18.dp),
                    )
                }
            }
        }

        Card(
            onClick = onClick,
            modifier = Modifier
                .offset(cardLeft, cardTop)
                .then(cardModifier)
                .width(cardWidth)
                .height(cardHeight),
            shape = RoundedCornerShape(BarCorner - inset),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp * fraction)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalPreviewCompact provides compact) {
                    content()
                }

                if (!compact) {
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
