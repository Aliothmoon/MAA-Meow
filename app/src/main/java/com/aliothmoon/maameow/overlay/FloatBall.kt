package com.aliothmoon.maameow.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aliothmoon.maameow.domain.state.MaaExecutionState


@Composable
fun FloatBall(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    runningState: MaaExecutionState = MaaExecutionState.IDLE,
    countdownSeconds: Int? = null,
) {
    val isCountdown = countdownSeconds != null
    val targetColor = when {
        isCountdown -> Color(0xFFFFA726)
        runningState == MaaExecutionState.RUNNING -> Color(0xFF4CAF50) // 绿色 - 运行中
        runningState == MaaExecutionState.STOPPING -> Color(0xFFFFA726) // 橙色 - 停止中
        runningState == MaaExecutionState.ERROR -> Color(0xFFE53935) // 红色 - 错误
        else -> MaterialTheme.colorScheme.primary // IDLE, STARTING 等使用默认主题色
    }

    val baseColor by animateColorAsState(
        targetValue = targetColor.copy(alpha = 0.85f),
        animationSpec = tween(300),
    )

    val textColor = Color.White

    val infiniteTransition = rememberInfiniteTransition()
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
    )

    val alphaModifier = if (runningState == MaaExecutionState.RUNNING || isCountdown) {
        Modifier.alpha(breathingAlpha)
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .size(32.dp)
            .border(1.dp, textColor.copy(alpha = 0.15f), CircleShape)
            .then(alphaModifier),
        shape = CircleShape,
        color = baseColor,
        shadowElevation = 8.dp,
        tonalElevation = 4.dp,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isCountdown) {
                Text(
                    text = "$countdownSeconds",
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Icon(
                    imageVector = when (runningState) {
                        MaaExecutionState.RUNNING -> Icons.Filled.PlayArrow
                        MaaExecutionState.ERROR -> Icons.Filled.Warning
                        else -> Icons.Filled.Check
                    },
                    contentDescription = runningState.name,
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
