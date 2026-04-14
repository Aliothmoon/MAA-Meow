package com.aliothmoon.maameow.presentation.view.notification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.AppSettingsManager.EventNotificationLevel
import com.aliothmoon.maameow.domain.service.MaaEventNotifier
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.presentation.components.InfoCard
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.viewmodel.NotificationSettingsViewModel
import com.aliothmoon.maameow.theme.MaaDesignTokens
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private val PROVIDERS: List<Pair<String, Int>> = listOf(
    "ServerChan" to R.string.notification_provider_server_chan,
    "Telegram" to R.string.notification_provider_telegram,
    "Discord" to R.string.notification_provider_discord,
    "DingTalk" to R.string.notification_provider_ding_talk,
    "Discord Webhook" to R.string.notification_provider_discord_webhook,
    "SMTP" to R.string.notification_provider_smtp,
    "Bark" to R.string.notification_provider_bark,
    "Qmsg" to R.string.notification_provider_qmsg,
    "Gotify" to R.string.notification_provider_gotify,
    "CustomWebhook" to R.string.notification_provider_custom_webhook,
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

    val appSettingsManager: AppSettingsManager = koinInject()
    val eventNotificationLevel by appSettingsManager.eventNotificationLevel.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val testMessage = stringResource(R.string.notification_test_message)

    Scaffold(
        topBar = {
            TopAppBar(title = stringResource(R.string.notification_settings_title))
        }
    ) { paddingValues ->
    val contentColor = MaterialTheme.colorScheme.onSurface

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = paddingValues.calculateTopPadding()),
        contentPadding = PaddingValues(MaaDesignTokens.Spacing.lg)
    ) {
        // 内部通知
        item {
            val isEnabled = eventNotificationLevel != EventNotificationLevel.OFF
            SectionHeader(stringResource(R.string.notification_section_internal))
            InfoCard(title = "") {
                SwitchItem(
                    title = stringResource(R.string.notification_enable),
                    checked = isEnabled,
                    contentColor = contentColor
                ) { enabled ->
                    coroutineScope.launch {
                        appSettingsManager.setEventNotificationLevel(
                            if (enabled) EventNotificationLevel.DEFAULT else EventNotificationLevel.OFF
                        )
                    }
                }
                AnimatedVisibility(visible = isEnabled) {
                    Column {
                        SettingsDivider(contentColor)
                        SwitchItem(
                            title = stringResource(R.string.notification_popup),
                            checked = eventNotificationLevel == EventNotificationLevel.HIGH,
                            contentColor = contentColor
                        ) { popup ->
                            coroutineScope.launch {
                                appSettingsManager.setEventNotificationLevel(
                                    if (popup) EventNotificationLevel.HIGH else EventNotificationLevel.DEFAULT
                                )
                            }
                        }
                        SettingsDivider(contentColor)
                        val eventNotifier: MaaEventNotifier = koinInject()
                        Button(
                            onClick = {
                                eventNotifier.notifyAllTasksCompleted(testMessage)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = MaaDesignTokens.Spacing.sm),
                            shape = MaterialTheme.shapes.small,
                            contentPadding = ButtonDefaults.ContentPadding
                        ) {
                            Text(stringResource(R.string.notification_send_test))
                        }
                    }
                }
            }
        }

        // 外部通知 - 触发条件
        item {
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sectionGap))
            SectionHeader(stringResource(R.string.notification_section_external))
            InfoCard(title = "") {
                SwitchItem(stringResource(R.string.notification_send_on_complete), sendOnComplete, contentColor) {
                    viewModel.updateSettings { copy(sendOnComplete = it.toString()) }
                }
                SettingsDivider(contentColor)
                SwitchItem(stringResource(R.string.notification_send_on_error), sendOnError, contentColor) {
                    viewModel.updateSettings { copy(sendOnError = it.toString()) }
                }
                SettingsDivider(contentColor)
                SwitchItem(stringResource(R.string.notification_send_on_service_died), sendOnServiceDied, contentColor) {
                    viewModel.updateSettings { copy(sendOnServiceDied = it.toString()) }
                }
                SettingsDivider(contentColor)
                SwitchItem(stringResource(R.string.notification_include_log_details), includeLogDetails, contentColor) {
                    viewModel.updateSettings { copy(includeLogDetails = it.toString()) }
                }
            }
        }

        // 通知渠道
        item {
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sectionGap))
            SectionHeader(stringResource(R.string.notification_section_channels))
        }

        PROVIDERS.forEach { (id, displayNameRes) ->
            item(key = id) {
                val enabled = id in enabledProviders
                Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
                InfoCard(
                    title = "",
                    contentPadding = MaaDesignTokens.Spacing.md
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = MaaDesignTokens.Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                        ) {
                        Text(
                            text = stringResource(displayNameRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                viewModel.toggleProvider(id, it)
                            }
                        )
                    }
                    AnimatedVisibility(visible = enabled) {
                        Column(modifier = Modifier.padding(top = MaaDesignTokens.Spacing.sm)) {
                            SettingsDivider(contentColor)
                            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
                            ProviderConfig(id, settings, viewModel)
                        }
                    }
                }
            }
        }

        // 测试
        item {
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sectionGap))
            SectionHeader(stringResource(R.string.notification_section_test))
            InfoCard(title = "") {
                Button(
                    onClick = { viewModel.sendTest() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabledProviders.isNotEmpty(),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = ButtonDefaults.ContentPadding
                ) {
                    Text(stringResource(R.string.notification_send_test))
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
    val contentColor = MaterialTheme.colorScheme.onSurface

    when (id) {
        "ServerChan" -> {
            ITextField(
                value = settings.serverChanSendKey,
                onValueChange = { viewModel.updateSettings { copy(serverChanSendKey = it) } },
                label = stringResource(R.string.notification_label_send_key),
                placeholder = "SCT..."
            )
        }

        "Bark" -> {
            ITextField(
                value = settings.barkServer,
                onValueChange = { viewModel.updateSettings { copy(barkServer = it) } },
                label = stringResource(R.string.notification_label_server_url),
                placeholder = "https://api.day.app"
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            ITextField(
                value = settings.barkSendKey,
                onValueChange = { viewModel.updateSettings { copy(barkSendKey = it) } },
                label = stringResource(R.string.notification_label_bark_send_key)
            )
        }

        "Telegram" -> {
            ITextField(
                value = settings.telegramBotToken,
                onValueChange = { viewModel.updateSettings { copy(telegramBotToken = it) } },
                label = stringResource(R.string.notification_label_bot_token)
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            ITextField(
                value = settings.telegramChatId,
                onValueChange = { viewModel.updateSettings { copy(telegramChatId = it) } },
                label = stringResource(R.string.notification_label_chat_id)
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            ITextField(
                value = settings.telegramTopicId,
                onValueChange = { viewModel.updateSettings { copy(telegramTopicId = it) } },
                label = stringResource(R.string.notification_label_topic_id),
                placeholder = stringResource(R.string.notification_placeholder_optional)
            )
        }

        "Discord" -> {
            ITextField(
                value = settings.discordBotToken,
                onValueChange = { viewModel.updateSettings { copy(discordBotToken = it) } },
                label = stringResource(R.string.notification_label_bot_token)
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            ITextField(
                value = settings.discordUserId,
                onValueChange = { viewModel.updateSettings { copy(discordUserId = it) } },
                label = stringResource(R.string.notification_label_user_id)
            )
        }

        "DingTalk" -> {
            ITextField(
                value = settings.dingTalkAccessToken,
                onValueChange = { viewModel.updateSettings { copy(dingTalkAccessToken = it) } },
                label = stringResource(R.string.notification_label_access_token)
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            ITextField(
                value = settings.dingTalkSecret,
                onValueChange = { viewModel.updateSettings { copy(dingTalkSecret = it) } },
                label = stringResource(R.string.notification_label_secret),
                placeholder = stringResource(R.string.notification_placeholder_optional_signing_secret)
            )
        }

        "Discord Webhook" -> {
            ITextField(
                value = settings.discordWebhookUrl,
                onValueChange = { viewModel.updateSettings { copy(discordWebhookUrl = it) } },
                label = stringResource(R.string.notification_label_webhook_url),
                placeholder = "https://discord.com/api/webhooks/..."
            )
        }

        "SMTP" -> {
            ITextField(
                value = settings.smtpServer,
                onValueChange = { viewModel.updateSettings { copy(smtpServer = it) } },
                label = stringResource(R.string.notification_label_smtp_server),
                placeholder = "smtp.example.com"
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            ITextField(
                value = settings.smtpPort,
                onValueChange = { viewModel.updateSettings { copy(smtpPort = it) } },
                label = stringResource(R.string.notification_label_smtp_port),
                placeholder = "465"
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            SwitchItem(
                title = stringResource(R.string.notification_use_ssl),
                checked = settings.smtpUseSsl.toBooleanStrictOrNull() ?: false,
                contentColor = contentColor,
                onCheckedChange = { viewModel.updateSettings { copy(smtpUseSsl = it.toString()) } }
            )
            SettingsDivider(contentColor)
            SwitchItem(
                title = stringResource(R.string.notification_requires_auth),
                checked = settings.smtpRequireAuthentication.toBooleanStrictOrNull() ?: false,
                contentColor = contentColor,
                onCheckedChange = { viewModel.updateSettings { copy(smtpRequireAuthentication = it.toString()) } }
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            ITextField(
                value = settings.smtpUser,
                onValueChange = { viewModel.updateSettings { copy(smtpUser = it) } },
                label = stringResource(R.string.notification_label_smtp_user),
                placeholder = stringResource(R.string.notification_placeholder_optional_required_when_auth)
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            ITextField(
                value = settings.smtpPassword,
                onValueChange = { viewModel.updateSettings { copy(smtpPassword = it) } },
                label = stringResource(R.string.notification_label_smtp_password),
                placeholder = stringResource(R.string.notification_placeholder_optional_required_when_auth)
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            ITextField(
                value = settings.smtpFrom,
                onValueChange = { viewModel.updateSettings { copy(smtpFrom = it) } },
                label = stringResource(R.string.notification_label_from),
                placeholder = "sender@example.com"
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            ITextField(
                value = settings.smtpTo,
                onValueChange = { viewModel.updateSettings { copy(smtpTo = it) } },
                label = stringResource(R.string.notification_label_to),
                placeholder = "receiver@example.com"
            )
        }

        "Qmsg" -> {
            ITextField(
                value = settings.qmsgServer,
                onValueChange = { viewModel.updateSettings { copy(qmsgServer = it) } },
                label = stringResource(R.string.notification_label_qmsg_server),
                placeholder = "https://qmsg.zendee.cn"
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            ITextField(
                value = settings.qmsgKey,
                onValueChange = { viewModel.updateSettings { copy(qmsgKey = it) } },
                label = stringResource(R.string.notification_label_qmsg_key)
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            ITextField(
                value = settings.qmsgUser,
                onValueChange = { viewModel.updateSettings { copy(qmsgUser = it) } },
                label = stringResource(R.string.notification_label_user_qq)
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            ITextField(
                value = settings.qmsgBot,
                onValueChange = { viewModel.updateSettings { copy(qmsgBot = it) } },
                label = stringResource(R.string.notification_label_bot_qq),
                placeholder = stringResource(R.string.notification_placeholder_optional)
            )
        }

        "Gotify" -> {
            ITextField(
                value = settings.gotifyServer,
                onValueChange = { viewModel.updateSettings { copy(gotifyServer = it) } },
                label = stringResource(R.string.notification_label_server),
                placeholder = "https://gotify.example.com"
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            ITextField(
                value = settings.gotifyToken,
                onValueChange = { viewModel.updateSettings { copy(gotifyToken = it) } },
                label = stringResource(R.string.notification_label_application_token)
            )
        }

        "CustomWebhook" -> {
            ITextField(
                value = settings.customWebhookUrl,
                onValueChange = { viewModel.updateSettings { copy(customWebhookUrl = it) } },
                label = stringResource(R.string.notification_label_webhook_url),
                placeholder = "https://..."
            )
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
            ITextField(
                value = settings.customWebhookBody,
                onValueChange = { viewModel.updateSettings { copy(customWebhookBody = it) } },
                label = stringResource(R.string.notification_label_request_body_template),
                singleLine = false,
                placeholder = """{"title":"{title}","content":"{content}"}"""
            )
            Text(
                text = stringResource(R.string.notification_supported_placeholders),
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
                            .padding(vertical = MaaDesignTokens.Spacing.sm),
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
