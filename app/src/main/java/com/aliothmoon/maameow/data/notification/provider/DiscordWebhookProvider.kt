package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.utils.JsonUtils
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import timber.log.Timber

class DiscordWebhookProvider(
    private val httpClient: HttpClientHelper,
    private val settingsManager: NotificationSettingsManager
) : NotificationProvider {

    override val id = "Discord Webhook"

    override suspend fun send(title: String, content: String): NotificationSendResult {
        val settings = settingsManager.settings.first()
        val webhookUrl = settings.discordWebhookUrl.takeIf { it.isNotBlank() }
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_discord_webhook_empty)
            )
        val body = JsonUtils.common.encodeToString(DiscordWebhookRequest(content = content))

        return runCatching {
            httpClient.post(webhookUrl, body).use { response ->
                val responseBody = response.body.string()
                if (response.isSuccessful && responseBody.isEmpty()) {
                    return@use NotificationSendResult.Success
                }

                val errorResponse = runCatching {
                    JsonUtils.common.decodeFromString<DiscordWebhookErrorResponse>(responseBody)
                }.getOrNull()

                if (errorResponse == null) {
                    Timber.w("Discord Webhook failed with non-JSON response: %s", responseBody)
                } else {
                    Timber.w(
                        "Discord Webhook failed: %s (%s)",
                        errorResponse.message,
                        errorResponse.code
                    )
                }
                NotificationSendResult.Failed(
                    uiTextOf(R.string.notification_err_http_status, response.code),
                )
            }
        }.getOrElse {
            Timber.e(it, "Discord Webhook send failed")
            NotificationSendResult.Transient(uiTextOf(R.string.notification_err_network))
        }
    }

    @Serializable
    private data class DiscordWebhookRequest(
        val content: String,
    )

    @Serializable
    private data class DiscordWebhookErrorResponse(
        val message: String? = null,
        val code: Int? = null,
    )
}
