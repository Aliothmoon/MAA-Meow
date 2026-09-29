package com.aliothmoon.maameow.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.state.ResourceInitState
import com.aliothmoon.maameow.theme.OpaqueTheme
import com.aliothmoon.maameow.utils.i18n.asString

/**
 * 资源初始化弹窗
 * 显示初始化进度或失败信息
 */
@Composable
fun ResourceInitDialog(
    state: ResourceInitState,
    onDismiss: () -> Unit = {},
    onRetry: () -> Unit = {}
) {

    when (state) {
        is ResourceInitState.Extracting -> {
            // 解压进度弹窗（不可关闭）——恢复不透明配色，避免透出主界面自定义背景图
            OpaqueTheme {
                Dialog(
                    onDismissRequest = {},
                    properties = DialogProperties(
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false
                    )
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.resource_init_in_progress_title),
                                style = MaterialTheme.typography.titleMedium
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            ExtractProgressBar(
                                state = state,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // 总数未知时（准备、清理旧资源）不显示 0 / 0
                            if (state.totalCount > 0) {
                                Text(
                                    text = "${state.extractedCount} / ${state.totalCount} (${state.progress}%)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (state.currentFile.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.currentFile,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = stringResource(R.string.resource_init_in_progress_message),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        is ResourceInitState.Failed -> {
            // 失败弹窗
            AdaptiveTaskPromptDialog(
                visible = true,
                title = stringResource(R.string.resource_init_failed_title),
                message = stringResource(
                    R.string.resource_init_failed_message,
                    state.text.asString()
                ),
                onConfirm = onRetry,
                onDismissRequest = onDismiss,
                confirmText = stringResource(R.string.resource_init_retry),
                dismissText = stringResource(R.string.common_cancel),
                icon = Icons.Rounded.Warning,
                confirmColor = MaterialTheme.colorScheme.error
            )
        }

        else -> {
            // 其他状态不显示弹窗
        }
    }
}

/**
 * 解压进度条
 * 总数未知时走不定进度动画；已知时平滑过渡，并有一道光扫过整条，进度一时不动也看得出在跑
 */
@Composable
private fun ExtractProgressBar(
    state: ResourceInitState.Extracting,
    modifier: Modifier = Modifier
) {
    if (state.totalCount <= 0) {
        LinearProgressIndicator(modifier = modifier)
        return
    }

    // 按文件数取小数进度，不用整数百分比，免得每满 1% 才跳一格
    val progress by animateFloatAsState(
        targetValue = state.extractedCount.toFloat() / state.totalCount,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "extractProgress"
    )
    val shimmerTransition = rememberInfiniteTransition(label = "extractShimmer")
    val shimmerPos by shimmerTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "extractShimmerPos"
    )
    // 已完成段是主色，轨道是浅色，同一种高光总有一边看不见，分开取色
    val filledShimmer = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.45f)
    val trackShimmer = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)

    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.drawWithContent {
            drawContent()
            val rtl = layoutDirection == LayoutDirection.Rtl
            val sweepWidth = size.width * 0.2f
            val center = size.width * if (rtl) 1f - shimmerPos else shimmerPos
            fun sweep(color: Color) = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, color, Color.Transparent),
                startX = center - sweepWidth,
                endX = center + sweepWidth
            )
            // RTL 下已完成段在右侧
            val filled = size.width * progress
            val split = if (rtl) size.width - filled else filled
            val bar = Path().apply {
                addRoundRect(
                    RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.height / 2))
                )
            }
            clipPath(bar) {
                clipRect(right = split) { drawRect(sweep(if (rtl) trackShimmer else filledShimmer)) }
                clipRect(left = split) { drawRect(sweep(if (rtl) filledShimmer else trackShimmer)) }
            }
        }
    )
}

/**
 * 重新初始化确认弹窗
 */
@Composable
fun ReInitializeConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AdaptiveTaskPromptDialog(
        visible = true,
        title = stringResource(R.string.resource_init_reinitialize_title),
        message = stringResource(R.string.resource_init_reinitialize_message),
        onConfirm = onConfirm,
        onDismissRequest = onDismiss,
        confirmText = stringResource(R.string.resource_init_reinitialize_confirm),
        dismissText = stringResource(R.string.common_cancel),
        icon = Icons.Rounded.Refresh,
        confirmColor = MaterialTheme.colorScheme.error
    )
}
