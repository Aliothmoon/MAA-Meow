package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aliothmoon.maameow.domain.state.ResourceInitState

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
            // 解压进度弹窗（不可关闭）
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
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
                            text = "正在初始化资源",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // 进度条
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 进度文本
                        Text(
                            text = "${state.extractedCount} / ${state.totalCount} (${state.progress}%)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

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
                            text = "首次启动需要解压资源文件，请稍候...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        is ResourceInitState.Failed -> {
            // 失败弹窗
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("初始化失败") },
                text = {
                    Column {
                        Text("资源初始化失败: ${state.message}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "请检查存储空间是否充足，然后重试。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = onRetry) {
                        Text("重试")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                }
            )
        }

        else -> {
            // 其他状态不显示弹窗
        }
    }
}

/**
 * 重新初始化确认弹窗
 */
@Composable
fun ReInitializeConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重新初始化资源") },
        text = {
            Column {
                Text("确定要重新初始化资源吗？")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "这将删除现有资源并重新从内置资源包解压。如果您已更新过资源，更新的内容将被覆盖。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("确认重新初始化")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
