package com.aliothmoon.maameow.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R

/**
 * 悬浮窗专用对话框组件
 */
@Composable
fun OverlayDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    confirmText: String? = null,
    dismissText: String? = null,
    onConfirm: () -> Unit,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.error,
    confirmColor: Color = MaterialTheme.colorScheme.primary,
) {
    val resolvedConfirmText = confirmText ?: stringResource(R.string.common_confirm)
    val resolvedDismissText = dismissText ?: stringResource(R.string.common_cancel)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(150))
    ) {
        // 遮罩层：半透明黑色背景，点击可关闭
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismissRequest() },
            contentAlignment = Alignment.Center
        ) {
            // 对话框卡片：带缩放动画
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(initialScale = 0.85f, animationSpec = tween(200)),
                exit = scaleOut(targetScale = 0.85f, animationSpec = tween(150))
            ) {
                Card(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .wrapContentHeight()
                        .shadow(8.dp, RoundedCornerShape(8.dp))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { /* 消费点击事件，防止穿透到遮罩层 */ },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 可选图标
                        icon?.let {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = iconTint.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = it,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = iconTint
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // 标题
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 消息内容
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // 按钮行
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 取消按钮
                            OutlinedButton(
                                onClick = onDismissRequest,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(text = resolvedDismissText)
                            }

                            // 确认按钮
                            Button(
                                onClick = onConfirm,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = confirmColor,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(text = resolvedConfirmText)
                            }
                        }
                    }
                }
            }
        }
    }
}
