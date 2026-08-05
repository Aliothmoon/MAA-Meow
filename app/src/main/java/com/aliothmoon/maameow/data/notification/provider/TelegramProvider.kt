package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.utils.JsonUtils
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber

class TelegramProvider(
    private val httpClient: HttpClientHelper,
    private val settingsManager: NotificationSettingsManager
) : NotificationProvider {

    override val id = "Telegram"

    override suspend fun send(title: String, content: String): NotificationSendResult {
        val settings = settingsManager.settings.first()
        val botToken = settings.telegramBotToken.takeIf { it.isNotEmpty() }
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_telegram_token_empty)
            )
        val chatId = settings.telegramChatId.takeIf { it.isNotEmpty() }
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_telegram_chat_empty)
            )

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
                if (response.isSuccessful &&
                    JsonUtils.common.decodeFromString<TelegramResponse>(responseBody).ok
                ) {
                    NotificationSendResult.Success
                } else {
                    Timber.w("Telegram rejected: HTTP %d, body=%s", response.code, responseBody)
                    NotificationSendResult.Failed(
                        uiTextOf(R.string.notification_err_http_status, response.code),
                    )
                }
            }
        }.getOrElse {
            Timber.e(it, "Telegram send failed")
            NotificationSendResult.Transient(uiTextOf(R.string.notification_err_network))
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
