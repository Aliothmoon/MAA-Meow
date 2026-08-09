package com.aliothmoon.maameow.data.notification

import com.aliothmoon.preferences.PrefKey
import com.aliothmoon.preferences.PrefSchema
import kotlinx.serialization.Serializable

@Serializable
@PrefSchema(name = "NotificationSettings")
data class NotificationSettings(
    @PrefKey(default = "true") val sendOnComplete: String = "true",
    @PrefKey(default = "true") val sendOnError: String = "true",
    @PrefKey(default = "false") val sendOnServiceDied: String = "false",
    @PrefKey(default = "false") val includeLogDetails: String = "false",
    @PrefKey(default = "") val enabledProviders: String = "",
    @PrefKey(default = "") val serverChanSendKey: String = "",
    @PrefKey(default = "") val discordBotToken: String = "",
    @PrefKey(default = "") val discordUserId: String = "",
    @PrefKey(default = "") val discordWebhookUrl: String = "",
    @PrefKey(default = "") val smtpServer: String = "",
    @PrefKey(default = "") val smtpPort: String = "",
    @PrefKey(default = "") val smtpUser: String = "",
    @PrefKey(default = "") val smtpPassword: String = "",
    @PrefKey(default = "false") val smtpUseSsl: String = "false",
    @PrefKey(default = "false") val smtpRequireAuthentication: String = "false",
    @PrefKey(default = "") val smtpFrom: String = "",
    @PrefKey(default = "") val smtpTo: String = "",
    @PrefKey(default = "https://api.day.app") val barkServer: String = "https://api.day.app",
    @PrefKey(default = "") val barkSendKey: String = "",
    @PrefKey(default = "") val telegramBotToken: String = "",
    @PrefKey(default = "") val telegramChatId: String = "",
    @PrefKey(default = "") val telegramTopicId: String = "",
    @PrefKey(default = "") val dingTalkAccessToken: String = "",
    @PrefKey(default = "") val dingTalkSecret: String = "",
    @PrefKey(default = "") val kookBotToken: String = "",
    @PrefKey(default = "") val kookTargetId: String = "",
    @PrefKey(default = "false") val kookDirectMessage: String = "false",
    @PrefKey(default = "") val qmsgServer: String = "",
    @PrefKey(default = "") val qmsgKey: String = "",
    @PrefKey(default = "") val qmsgUser: String = "",
    @PrefKey(default = "") val qmsgBot: String = "",
    @PrefKey(default = "") val gotifyServer: String = "",
    @PrefKey(default = "") val gotifyToken: String = "",
    @PrefKey(default = "") val customWebhookUrl: String = "",
    @PrefKey(default = "") val customWebhookHeaders: String = "",
    @PrefKey(default = "") val customWebhookBody: String = "",
)
