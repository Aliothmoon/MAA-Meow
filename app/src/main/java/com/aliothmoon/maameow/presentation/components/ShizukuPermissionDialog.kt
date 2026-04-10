package com.aliothmoon.maameow.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aliothmoon.maameow.R

@Composable
fun ShizukuPermissionDialog(
    title: String,
    message: String,
    isRequesting: Boolean,
    onConfirm: () -> Unit,
) {
    AdaptiveTaskPromptDialog(
        visible = true,
        title = title,
        message = message,
        onConfirm = onConfirm,
        onDismissRequest = {}, // 不允许通过关闭请求取消
        dismissOnOutsideClick = false,
        confirmText = if (isRequesting) stringResource(R.string.authorizing) else stringResource(R.string.authorize_now),
        dismissText = null,
        icon = Icons.Rounded.Build,
        confirmColor = MaterialTheme.colorScheme.primary
    )
}
