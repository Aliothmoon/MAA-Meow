package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.utils.JsonUtils
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.net.URI

class GotifyProvider(
    private val httpClient: HttpClientHelper,
    private val settingsManager: NotificationSettingsManager
) : NotificationProvider {

    override val id = "Gotify"

    override suspend fun send(title: String, content: String): NotificationSendResult {
        val settings = settingsManager.settings.first()
        val rawServer = settings.gotifyServer.takeIf { it.isNotBlank() }?.trim()?.trimEnd('/')
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_gotify_server_empty)
            )
        val token = settings.gotifyToken.takeIf { it.isNotBlank() }
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_gotify_token_empty)
            )
        val baseUri = runCatching { URI.create("$rawServer/") }.getOrNull()
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_gotify_scheme)
            )
        if (baseUri.scheme !in setOf("http", "https")) {
            return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_gotify_scheme)
            )
        }

        val url = baseUri.resolve("message").toString()
        val body = JsonUtils.common.encodeToString(
            GotifyRequest(
                title = title,
                message = content,
            )
        )

        return runCatching {
            httpClient.post(
                url = url,
                body = body,
                headers = mapOf("X-Gotify-Key" to token)
            ).use { response ->
                val responseBody = response.body.string()
                if (response.isSuccessful &&
                    JsonUtils.common.decodeFromString<GotifyResponse>(responseBody).id != null
                ) {
                    NotificationSendResult.Success
                } else {
                    Timber.w("Gotify rejected: HTTP %d, body=%s", response.code, responseBody)
                    NotificationSendResult.Failed(
                        uiTextOf(R.string.notification_err_http_status, response.code),
                    )
                }
            }
        }.getOrElse {
            Timber.e(it, "Gotify send failed")
            NotificationSendResult.Transient(uiTextOf(R.string.notification_err_network))
        }
    }

    @Serializable
    private data class GotifyRequest(
        val title: String,
        val message: String,
    )

    @Serializable
    private data class GotifyResponse(
        val id: Int? = null,
    )
}
