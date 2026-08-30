package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility

/**
 * 内嵌二次确认面板
 *
 * 悬浮窗里弹不出 Dialog，破坏性或高风险操作的确认统一走这个：就地展开一块警告色区域，
 * 确认后由调用方执行动作并把 [visible] 置回 false
 *
 * @param visible 是否展开，由调用方持有
 * @param message 说清楚会发生什么，别只写「确定吗」
 * @param title 可选标题，仅在需要额外强调时给
 */
@Composable
fun InlineConfirmPanel(
    visible: Boolean,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    confirmText: String = stringResource(R.string.common_confirm),
    dismissText: String = stringResource(R.string.common_cancel),
) {
    MaaAnimatedVisibility(visible = visible) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                InlineActionRow(
                    confirmText = confirmText,
                    dismissText = dismissText,
                    onConfirm = onConfirm,
                    onDismiss = onDismiss,
                    confirmContainerColor = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * 内嵌面板底部的取消/确认按钮行
 * 单独抽出来给 [InlineConfirmPanel] 和其它就地展开的操作面板共用，统一高度与字号
 */
@Composable
fun InlineActionRow(
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    confirmContainerColor: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        ) {
            Text(dismissText, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier.height(32.dp),
            enabled = confirmEnabled,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = confirmContainerColor),
        ) {
            Text(confirmText, style = MaterialTheme.typography.bodySmall)
        }
    }
}
