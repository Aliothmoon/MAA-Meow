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
        title = stringResource(R.string.resource_update_found),
        message = stringResource(R.string.resource_update_message, displayVersion),
        onConfirm = onConfirm,
        onDismissRequest = onDismiss,
        confirmText = stringResource(R.string.download_now),
        confirmColor = Color(0xFF4CAF50),
        dismissText = stringResource(R.string.later_update),
        icon = Icons.Rounded.Info
    )
}
