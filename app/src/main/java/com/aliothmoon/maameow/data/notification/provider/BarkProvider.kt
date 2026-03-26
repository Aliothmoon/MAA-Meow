package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.net.URI

class BarkProvider(
    private val httpClient: HttpClientHelper,
    private val settingsManager: NotificationSettingsManager
) : NotificationProvider {

    override val id = "Bark"

    override suspend fun send(title: String, content: String): Boolean {
        val settings = settingsManager.settings.first()
        val barkServer = settings.barkServer.takeIf { it.isNotEmpty() } ?: return false
        val sendKey = settings.barkSendKey.takeIf { it.isNotEmpty() } ?: return false

        val url = URI.create(barkServer).resolve("/push").toString()
        val body = JsonUtils.common.encodeToString(
            BarkRequest(
                title = title,
                body = content,
                deviceKey = sendKey,
            )
        )

        return runCatching {
            httpClient.post(url, body).use { response ->
                val responseBody = response.body.string()
                response.isSuccessful && JsonUtils.common.decodeFromString<BarkResponse>(responseBody).code == 200
            }
        }.getOrElse {
            Timber.e(it, "Bark send failed")
            false
        }
    }

    @Serializable
    private data class BarkRequest(
        val title: String,
        val body: String,
        @SerialName("device_key") val deviceKey: String,
        val group: String = "MaaAssistantArknights",
        val icon: String = "https://cdn.jsdelivr.net/gh/MaaAssistantArknights/design@main/logo/maa-logo_256x256.png",
    )

    @Serializable
    private data class BarkResponse(
        val code: Int = -1,
    )
}
