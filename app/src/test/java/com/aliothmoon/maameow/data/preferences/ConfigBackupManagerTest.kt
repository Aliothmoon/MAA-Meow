package com.aliothmoon.maameow.data.preferences

import com.aliothmoon.maameow.data.notification.NotificationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class ConfigBackupManagerTest {

    // 此函数已两次泄漏凭证，用清单测试钉死：脱敏后空字段集合应恰等于凭证清单
    @Test
    fun exportRedactionBlanksExactlyTheCredentialList() {
        val populated = NotificationSettings(
            sendOnComplete = "true",
            sendOnError = "true",
            sendOnServiceDied = "false",
            includeLogDetails = "true",
            enabledProviders = "smtp,custom_webhook",
            serverChanSendKey = "SCU-key",
            discordBotToken = "discord-token",
            discordUserId = "discord-user-id",
            discordWebhookUrl = "https://discord.example/webhook",
            smtpServer = "smtp.example.com",
            smtpPort = "465",
            smtpUser = "smtp-user",
            smtpPassword = "smtp-pass",
            smtpUseSsl = "true",
            smtpRequireAuthentication = "true",
            smtpFrom = "from@example.com",
            smtpTo = "to@example.com",
            barkServer = "https://bark.example.com",
            barkSendKey = "bark-key",
            telegramBotToken = "telegram-token",
            telegramChatId = "telegram-chat-id",
            telegramTopicId = "telegram-topic-id",
            dingTalkAccessToken = "dingtalk-access-token",
            dingTalkSecret = "dingtalk-secret",
            kookBotToken = "kook-token",
            kookTargetId = "kook-target-id",
            kookDirectMessage = "false",
            qmsgServer = "https://qmsg.example.com",
            qmsgKey = "qmsg-key",
            qmsgUser = "qmsg-user",
            qmsgBot = "qmsg-bot",
            qmsgV3Key = "qmsg-v3-key",
            qmsgV3Group = "qmsg-v3-group",
            gotifyServer = "https://gotify.example.com",
            gotifyToken = "gotify-token",
            customWebhookUrl = "https://webhook.example.com/hook",
            customWebhookHeaders = "Authorization: Bot token",
            customWebhookBody = """{"content": "{content}"}""",
            customWebhookPresetId = "Kook Channel",
        )

        val sanitized = populated.sanitizedForExport()

        val blanked = NotificationSettings::class.java.declaredMethods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == String::class.java }
            .filter { (it.invoke(sanitized) as String).isBlank() }
            .map { it.name.removePrefix("get").replaceFirstChar { c -> c.lowercase() } }
            .toSet()

        assertEquals(
            setOf(
                "serverChanSendKey",
                "discordBotToken",
                "discordWebhookUrl",
                "smtpPassword",
                "barkSendKey",
                "telegramBotToken",
                "dingTalkAccessToken",
                "dingTalkSecret",
                "kookBotToken",
                "qmsgKey",
                "qmsgV3Key",
                "gotifyToken",
                "customWebhookUrl",
                "customWebhookHeaders",
            ),
            blanked,
        )
        // 非凭证字段原样保留
        assertEquals("Kook Channel", sanitized.customWebhookPresetId)
        assertEquals("""{"content": "{content}"}""", sanitized.customWebhookBody)
        assertEquals("kook-target-id", sanitized.kookTargetId)
        assertEquals("telegram-chat-id", sanitized.telegramChatId)
    }

    @Test
    fun importReadAcceptsContentUpToLimit() {
        val text = "备份".repeat(4096)
        val bytes = text.toByteArray()
        assertEquals(text, ByteArrayInputStream(bytes).readTextAtMost(bytes.size))
    }

    // 选错大文件时整读进内存会 OOM（ANDROID-13W）
    @Test
    fun importReadRejectsContentOverLimit() {
        val bytes = ByteArray(64 * 1024 + 1)
        val e = assertThrows(BackupTooLargeException::class.java) {
            ByteArrayInputStream(bytes).readTextAtMost(64 * 1024)
        }
        assertEquals(64 * 1024, e.limitBytes)
    }
}
