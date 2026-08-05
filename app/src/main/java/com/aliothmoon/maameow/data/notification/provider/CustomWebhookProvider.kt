package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CustomWebhookProvider(
    private val httpClient: HttpClientHelper,
    private val settingsManager: NotificationSettingsManager
) : NotificationProvider {

    override val id = "CustomWebhook"

    override suspend fun send(title: String, content: String): NotificationSendResult {
        val settings = settingsManager.settings.first()
        val url = settings.customWebhookUrl.takeIf { it.isNotEmpty() }
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_webhook_url_empty)
            )
        val bodyTemplate = settings.customWebhookBody.takeIf { it.isNotEmpty() }
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_webhook_body_empty)
            )

        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val body = bodyTemplate
            .replace("{title}", title.replace("\r", "").replace("\n", ""))
            .replace("{content}", content.replace("\r", "").replace("\n", "\\n"))
            .replace("{time}", now)

        val headers = settings.customWebhookHeaders
            .replace("\r", "")
            .split("\n")
            .filter { it.contains(":") }
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx <= 0) null
                else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
            .toMap()

        return runCatching {
            httpClient.post(url, body, headers = headers).use { response ->
                if (response.isSuccessful) {
                    NotificationSendResult.Success
                } else {
                    val responseBody = response.body.string()
                    Timber.w("CustomWebhook rejected: HTTP %d, body=%s", response.code, responseBody)
                    NotificationSendResult.Failed(
                        uiTextOf(R.string.notification_err_http_status, response.code),
                    )
                }
            }
        }.getOrElse {
            Timber.e(it, "CustomWebhook send failed")
            NotificationSendResult.Transient(uiTextOf(R.string.notification_err_network))
        }
    }
}
