package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import timber.log.Timber

class ServerChanProvider(
    private val httpClient: HttpClientHelper,
    private val settingsManager: NotificationSettingsManager
) : NotificationProvider {

    override val id = "ServerChan"

    override suspend fun send(title: String, content: String): Boolean {
        val settings = settingsManager.settings.first()
        val sendKey = settings.serverChanSendKey.takeIf { it.isNotEmpty() } ?: return false
        val normalizedTitle = title.replace("\n", "").take(32)

        val url = if (sendKey.startsWith("sctp")) {
            val match = Regex("""^sctp(\d+)t""").find(sendKey)
                ?: throw IllegalArgumentException("Invalid key format for sctp.")
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
                response.isSuccessful && JsonUtils.common.decodeFromString<ServerChanResponse>(body).code == 0
            }
        }.getOrElse {
            Timber.e(it, "ServerChan send failed")
            false
        }
    }

    @Serializable
    private data class ServerChanResponse(
        val code: Int = -1,
    )
}
