package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber

class TelegramProvider(
    private val httpClient: HttpClientHelper,
    private val settingsManager: NotificationSettingsManager
) : NotificationProvider {

    override val id = "Telegram"

    override suspend fun send(title: String, content: String): Boolean {
        val settings = settingsManager.settings.first()
        val botToken = settings.telegramBotToken.takeIf { it.isNotEmpty() } ?: return false
        val chatId = settings.telegramChatId.takeIf { it.isNotEmpty() } ?: return false

        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        val topicId = settings.telegramTopicId.takeIf { it.isNotEmpty() }
        val body = JsonUtils.common.encodeToString(
            TelegramRequest(
                chatId = chatId,
                text = "$title: $content",
                messageThreadId = topicId,
            )
        )

        return runCatching {
            httpClient.post(url, body).use { response ->
                val responseBody = response.body.string()
                response.isSuccessful && JsonUtils.common.decodeFromString<TelegramResponse>(responseBody).ok
            }
        }.getOrElse {
            Timber.e(it, "Telegram send failed")
            false
        }
    }

    @Serializable
    private data class TelegramRequest(
        @SerialName("chat_id") val chatId: String,
        val text: String,
        @SerialName("message_thread_id") val messageThreadId: String? = null,
    )

    @Serializable
    private data class TelegramResponse(
        val ok: Boolean = false,
    )
}
