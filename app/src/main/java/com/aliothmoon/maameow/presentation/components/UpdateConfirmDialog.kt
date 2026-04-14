package com.aliothmoon.maameow.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.datasource.ResourceDownloader
import com.aliothmoon.maameow.data.model.update.UpdateInfo

/**
 * 资源更新确认弹窗
 */
@Composable
fun UpdateConfirmDialog(
    updateInfo: UpdateInfo,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val displayVersion = ResourceDownloader.formatVersionForDisplay(updateInfo.version)
    
    AdaptiveTaskPromptDialog(
        visible = true,
        title = stringResource(R.string.update_confirm_title_resource),
        message = stringResource(R.string.update_confirm_message_resource, displayVersion),
        onConfirm = onConfirm,
        onDismissRequest = onDismiss,
        confirmText = stringResource(R.string.update_confirm_download_now),
        confirmColor = Color(0xFF4CAF50),
        dismissText = stringResource(R.string.dialog_update_later),
        icon = Icons.Rounded.Info
    )
}
