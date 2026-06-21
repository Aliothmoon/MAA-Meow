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
    onDismiss: () -> Unit,
    confirmText: String? = null,
    dismissText: String? = null,
    dismissOnOutsideClick: Boolean = true,
) {
    val resolvedDismissText = dismissText ?: stringResource(R.string.common_cancel)

    AdaptiveTaskPromptDialog(
        visible = true,
        title = title,
        message = message,
        onConfirm = onConfirm,
        onDismissRequest = onDismiss,
        dismissOnOutsideClick = dismissOnOutsideClick,
        confirmText = if (isRequesting) {
            stringResource(R.string.shizuku_auth_requesting)
        } else {
            confirmText ?: stringResource(R.string.shizuku_auth_grant_now)
        },
        dismissText = resolvedDismissText,
        icon = Icons.Rounded.Build,
        confirmColor = MaterialTheme.colorScheme.primary
    )
}
