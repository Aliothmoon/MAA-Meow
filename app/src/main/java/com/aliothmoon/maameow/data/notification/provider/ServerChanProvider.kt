package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.utils.JsonUtils
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import timber.log.Timber

class ServerChanProvider(
    private val httpClient: HttpClientHelper,
    private val settingsManager: NotificationSettingsManager
) : NotificationProvider {

    override val id = "ServerChan"

    override suspend fun send(title: String, content: String): NotificationSendResult {
        val settings = settingsManager.settings.first()
        val sendKey = settings.serverChanSendKey.takeIf { it.isNotEmpty() }
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_serverchan_key_empty)
            )
        val normalizedTitle = title.replace("\n", "").take(32)

        val url = if (sendKey.startsWith("sctp")) {
            val match = Regex("""^sctp(\d+)t""").find(sendKey)
                ?: return NotificationSendResult.Failed(
                    uiTextOf(R.string.notification_err_serverchan_sctp_fmt)
                )
            "https://${match.groupValues[1]}.push.ft07.com/send/$sendKey.send"
        } else {
            "https://sctapi.ftqq.com/$sendKey.send"
        }

        return runCatching {
            httpClient.postForm(
                url,
                mapOf("text" to normalizedTitle, "desp" to content)
            ).use { response ->
                val body = response.body.string()
                if (response.isSuccessful &&
                    JsonUtils.common.decodeFromString<ServerChanResponse>(body).code == 0
                ) {
                    NotificationSendResult.Success
                } else {
                    Timber.w("ServerChan rejected: HTTP %d, body=%s", response.code, body)
                    NotificationSendResult.Failed(
                        uiTextOf(R.string.notification_err_http_status, response.code),
                    )
                }
            }
        }.getOrElse {
            Timber.e(it, "ServerChan send failed")
            NotificationSendResult.Transient(uiTextOf(R.string.notification_err_network))
        }
    }

    @Serializable
    private data class ServerChanResponse(
        val code: Int = -1,
    )
}
