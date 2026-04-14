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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aliothmoon.maameow.presentation.LocalFloatingWindowContext

enum class TaskPromptButtonLayout {
    HORIZONTAL,
    VERTICAL,
}

/**
 * 适配型任务提示对话框
 * 支持在前台 Activity 和悬浮窗 Overlay 环境下自动切换样式
 */
@Composable
fun AdaptiveTaskPromptDialog(
    visible: Boolean,
    title: String,
    message: Any? = null, // 支持 String 或 AnnotatedString
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String? = null,
    dismissText: String? = null,
    neutralText: String? = null,
    onNeutralClick: () -> Unit = {},
    icon: ImageVector? = null, // 仅支持 ImageVector
    confirmColor: Color? = null,
    iconTint: Color? = null,
    buttonLayout: TaskPromptButtonLayout = TaskPromptButtonLayout.HORIZONTAL,
    maxWidth: Dp = 320.dp,
    dismissOnOutsideClick: Boolean = true,
    content: @Composable (() -> Unit)? = null
) {
    if (!visible) return

    val resolvedConfirmColor = confirmColor ?: MaterialTheme.colorScheme.primary
    val resolvedIconTint = iconTint ?: resolvedConfirmColor
    val resolvedConfirmText = confirmText ?: stringResource(R.string.common_confirm)
    val resolvedDismissText = dismissText ?: stringResource(R.string.common_cancel)

    if (LocalFloatingWindowContext.current) {
        FloatingTaskPromptDialog(
            title = title,
            message = message,
            onDismissRequest = onDismissRequest,
            onConfirm = onConfirm,
            confirmText = resolvedConfirmText,
            dismissText = resolvedDismissText,
            neutralText = neutralText,
            onNeutralClick = onNeutralClick,
            icon = icon,
            iconTint = resolvedIconTint,
            confirmColor = resolvedConfirmColor,
            buttonLayout = buttonLayout,
            maxWidth = maxWidth,
            dismissOnOutsideClick = dismissOnOutsideClick,
            content = content
        )
    } else {
        MaterialTaskPromptDialog(
            title = title,
            message = message,
            onDismissRequest = onDismissRequest,
            onConfirm = onConfirm,
            confirmText = resolvedConfirmText,
            dismissText = resolvedDismissText,
            neutralText = neutralText,
            onNeutralClick = onNeutralClick,
            icon = icon,
            iconTint = resolvedIconTint,
            confirmColor = resolvedConfirmColor,
            buttonLayout = buttonLayout,
            maxWidth = maxWidth,
            dismissOnOutsideClick = dismissOnOutsideClick,
            content = content
        )
    }
}

@Composable
private fun FloatingTaskPromptDialog(
    title: String,
    message: Any?,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String,
    dismissText: String?,
    neutralText: String?,
    onNeutralClick: () -> Unit,
    icon: ImageVector?,
    iconTint: Color,
    confirmColor: Color,
    buttonLayout: TaskPromptButtonLayout,
    maxWidth: Dp,
    dismissOnOutsideClick: Boolean,
    content: @Composable (() -> Unit)?
) {
    val overlayInteractionSource = remember { MutableInteractionSource() }
    val cardInteractionSource = remember { MutableInteractionSource() }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(150)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                .clickable(
                    indication = null,
                    interactionSource = overlayInteractionSource,
                    onClick = {
                        if (dismissOnOutsideClick) {
                            onDismissRequest()
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = true,
                enter = scaleIn(initialScale = 0.85f, animationSpec = tween(200)),
                exit = scaleOut(targetScale = 0.85f, animationSpec = tween(150)),
            ) {
                TaskPromptCard(
                    title = title,
                    message = message,
                    onDismissRequest = onDismissRequest,
                    onConfirm = onConfirm,
                    confirmText = confirmText,
                    dismissText = dismissText,
                    neutralText = neutralText,
                    onNeutralClick = onNeutralClick,
                    icon = icon,
                    iconTint = iconTint,
                    confirmColor = confirmColor,
                    buttonLayout = buttonLayout,
                    maxWidth = maxWidth,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .clickable(
                            indication = null,
                            interactionSource = cardInteractionSource,
                            onClick = {},
                        ),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun MaterialTaskPromptDialog(
    title: String,
    message: Any?,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String,
    dismissText: String?,
    neutralText: String?,
    onNeutralClick: () -> Unit,
    icon: ImageVector?,
    iconTint: Color,
    confirmColor: Color,
    buttonLayout: TaskPromptButtonLayout,
    maxWidth: Dp,
    dismissOnOutsideClick: Boolean,
    content: @Composable (() -> Unit)?
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnOutsideClick,
            dismissOnClickOutside = dismissOnOutsideClick,
            usePlatformDefaultWidth = false,
        ),
    ) {
        TaskPromptCard(
            title = title,
            message = message,
            onDismissRequest = onDismissRequest,
            onConfirm = onConfirm,
            confirmText = confirmText,
            dismissText = dismissText,
            neutralText = neutralText,
            onNeutralClick = onNeutralClick,
            icon = icon,
            iconTint = iconTint,
            confirmColor = confirmColor,
            buttonLayout = buttonLayout,
            maxWidth = maxWidth,
            modifier = Modifier.padding(horizontal = 24.dp),
            content = content
        )
    }
}

@Composable
private fun TaskPromptCard(
    title: String,
    message: Any?,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String,
    dismissText: String?,
    neutralText: String?,
    onNeutralClick: () -> Unit,
    icon: ImageVector?,
    iconTint: Color,
    confirmColor: Color,
    buttonLayout: TaskPromptButtonLayout,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit)?
) {
    Surface(
        modifier = modifier
            .widthIn(max = maxWidth)
            .wrapContentHeight(),
        shape = RoundedCornerShape(8.dp), // 保持 8dp 圆角风格
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                icon?.let {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = iconTint.copy(alpha = 0.12f),
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f)
                )
            }

            if (message != null || content != null) {
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (content != null) {
                content()
            } else if (message != null) {
                when (message) {
                    is AnnotatedString -> {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                        )
                    }

                    else -> {
                        Text(
                            text = message.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TaskPromptButtons(
                onDismissRequest = onDismissRequest,
                onConfirm = onConfirm,
                confirmText = confirmText,
                dismissText = dismissText,
                neutralText = neutralText,
                onNeutralClick = onNeutralClick,
                confirmColor = confirmColor,
                buttonLayout = buttonLayout,
            )
        }
    }
}

@Composable
private fun TaskPromptButtons(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String,
    dismissText: String?,
    neutralText: String?,
    onNeutralClick: () -> Unit,
    confirmColor: Color,
    buttonLayout: TaskPromptButtonLayout,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 主操作按钮：Filled
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = confirmColor,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(confirmText)
        }

        // 中性/次要按钮：Outlined
        neutralText?.let {
            OutlinedButton(
                onClick = onNeutralClick,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Text(it)
            }
        }

        // 取消/辅助按钮：Text
        dismissText?.let {
            TextButton(
                onClick = onDismissRequest,
                shape = MaterialTheme.shapes.large
            ) {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
