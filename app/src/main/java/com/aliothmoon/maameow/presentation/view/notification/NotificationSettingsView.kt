package com.aliothmoon.maameow.presentation.view.notification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.presentation.components.InfoCard
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.viewmodel.NotificationSettingsViewModel
import com.aliothmoon.maameow.theme.MaaDesignTokens
import org.koin.androidx.compose.koinViewModel

private val PROVIDERS = listOf(
    "ServerChan" to "Server酱",
    "Bark" to "Bark",
    "Telegram" to "Telegram",
    "DingTalk" to "钉钉机器人",
    "CustomWebhook" to "自定义 Webhook",
)

@Composable
fun NotificationSettingsView(
    viewModel: NotificationSettingsViewModel = koinViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val enabledProviders by viewModel.enabledProviders.collectAsStateWithLifecycle()
    val sendOnComplete by viewModel.sendOnComplete.collectAsStateWithLifecycle()
    val sendOnError by viewModel.sendOnError.collectAsStateWithLifecycle()
    val sendOnServiceDied by viewModel.sendOnServiceDied.collectAsStateWithLifecycle()
    val includeLogDetails by viewModel.includeLogDetails.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = "外部通知")
        }
    ) { paddingValues ->
    val contentColor = MaterialTheme.colorScheme.onSurface

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = paddingValues.calculateTopPadding()),
        contentPadding = PaddingValues(MaaDesignTokens.Spacing.lg)
    ) {
        // 触发条件
        item {
            SectionHeader("触发条件")
            InfoCard(title = "") {
                SwitchItem("任务完成时通知", sendOnComplete, contentColor) {
                    viewModel.updateSettings { copy(sendOnComplete = it.toString()) }
                }
                SettingsDivider(contentColor)
                SwitchItem("任务出错时通知", sendOnError, contentColor) {
                    viewModel.updateSettings { copy(sendOnError = it.toString()) }
                }
                SettingsDivider(contentColor)
                SwitchItem("服务异常终止时通知", sendOnServiceDied, contentColor) {
                    viewModel.updateSettings { copy(sendOnServiceDied = it.toString()) }
                }
                SettingsDivider(contentColor)
                SwitchItem("附带日志详情", includeLogDetails, contentColor) {
                    viewModel.updateSettings { copy(includeLogDetails = it.toString()) }
                }
            }
        }

        // 通知渠道
        item {
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sectionGap))
            SectionHeader("通知渠道")
        }

        PROVIDERS.forEach { (id, displayName) ->
            item(key = id) {
                val enabled = id in enabledProviders
                Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
                InfoCard(title = displayName) {
                    SwitchItem("启用", enabled, contentColor) {
                        viewModel.toggleProvider(id, it)
                    }
                    AnimatedVisibility(visible = enabled) {
                        Column(modifier = Modifier.padding(top = MaaDesignTokens.Spacing.sm)) {
                            ProviderConfig(id, settings, viewModel)
                        }
                    }
                }
            }
        }

        // 测试
        item {
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sectionGap))
            SectionHeader("测试")
            InfoCard(title = "") {
                Button(
                    onClick = { viewModel.sendTest() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabledProviders.isNotEmpty()
                ) {
                    Text("发送测试通知")
                }
            }
        }

        // 底部留白
        item { Spacer(Modifier.height(MaaDesignTokens.Spacing.xxl)) }
    }
    }
}

@Composable
private fun ProviderConfig(
    id: String,
    settings: com.aliothmoon.maameow.data.notification.NotificationSettings,
    viewModel: NotificationSettingsViewModel
) {
    when (id) {
        "ServerChan" -> {
            ITextField(
                value = settings.serverChanSendKey,
                onValueChange = { viewModel.updateSettings { copy(serverChanSendKey = it) } },
                label = "SendKey",
                placeholder = "SCT..."
            )
        }

        "Bark" -> {
            ITextField(
                value = settings.barkServer,
                onValueChange = { viewModel.updateSettings { copy(barkServer = it) } },
                label = "服务器地址",
                placeholder = "https://api.day.app"
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            ITextField(
                value = settings.barkSendKey,
                onValueChange = { viewModel.updateSettings { copy(barkSendKey = it) } },
                label = "SendKey"
            )
        }

        "Telegram" -> {
            ITextField(
                value = settings.telegramBotToken,
                onValueChange = { viewModel.updateSettings { copy(telegramBotToken = it) } },
                label = "Bot Token"
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            ITextField(
                value = settings.telegramChatId,
                onValueChange = { viewModel.updateSettings { copy(telegramChatId = it) } },
                label = "Chat ID"
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            ITextField(
                value = settings.telegramTopicId,
                onValueChange = { viewModel.updateSettings { copy(telegramTopicId = it) } },
                label = "Topic ID",
                placeholder = "可选"
            )
        }

        "DingTalk" -> {
            ITextField(
                value = settings.dingTalkAccessToken,
                onValueChange = { viewModel.updateSettings { copy(dingTalkAccessToken = it) } },
                label = "Access Token"
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            ITextField(
                value = settings.dingTalkSecret,
                onValueChange = { viewModel.updateSettings { copy(dingTalkSecret = it) } },
                label = "Secret",
                placeholder = "可选，加签密钥"
            )
        }

        "CustomWebhook" -> {
            ITextField(
                value = settings.customWebhookUrl,
                onValueChange = { viewModel.updateSettings { copy(customWebhookUrl = it) } },
                label = "Webhook URL",
                placeholder = "https://..."
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            ITextField(
                value = settings.customWebhookBody,
                onValueChange = { viewModel.updateSettings { copy(customWebhookBody = it) } },
                label = "请求体模板",
                singleLine = false,
                placeholder = """{"title":"{title}","content":"{content}"}"""
            )
            Text(
                text = "支持占位符: {title} {content} {time}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = MaaDesignTokens.Spacing.xs)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = MaaDesignTokens.Spacing.xs,
            bottom = MaaDesignTokens.Spacing.sm
        )
    )
}

@Composable
private fun SwitchItem(
    title: String,
    checked: Boolean,
    contentColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaaDesignTokens.Spacing.listItemVertical),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsDivider(contentColor: Color) {
    HorizontalDivider(
        modifier = Modifier.padding(start = MaaDesignTokens.Separator.inset),
        thickness = MaaDesignTokens.Separator.thickness,
        color = contentColor.copy(alpha = 0.12f)
    )
}
