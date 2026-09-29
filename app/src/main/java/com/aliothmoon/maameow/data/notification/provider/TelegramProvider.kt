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
                text = truncate("$title: $content"),
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

    /**
     * 裁到 [MAX_TEXT_LENGTH] 以内
     * 开「通知含详细日志」后完成通知会带上本轮全部日志，超限时 Telegram 直接 400 整条发不出去
     * 保留末尾：用时、配置与出错清单等正文排在日志之后
     */
    private fun truncate(text: String): String {
        if (text.length <= MAX_TEXT_LENGTH) return text

        // 起点落在代理项对（如 emoji）中间时前半已被裁掉，剩下的低位代理项要一并丢掉，否则序列化成 JSON 会变成替换字符
        return TRUNCATED_MARK + text.takeLast(MAX_TEXT_LENGTH - TRUNCATED_MARK.length)
            .trimStart { it.isLowSurrogate() }
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

    private companion object {
        /** sendMessage 的 text 上限，超出时接口返回 400 message is too long */
        const val MAX_TEXT_LENGTH = 4096
        const val TRUNCATED_MARK = "[...]\n"
    }
}
