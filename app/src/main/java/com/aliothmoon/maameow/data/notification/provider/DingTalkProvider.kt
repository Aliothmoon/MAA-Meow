package com.aliothmoon.maameow.data.notification.provider

import android.util.Base64
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class DingTalkProvider(
    private val httpClient: HttpClientHelper,
    private val settingsManager: NotificationSettingsManager
) : NotificationProvider {

    override val id = "DingTalk"

    override suspend fun send(title: String, content: String): Boolean {
        val settings = settingsManager.settings.first()
        val accessToken = settings.dingTalkAccessToken.takeIf { it.isNotEmpty() } ?: return false

        var url = "https://oapi.dingtalk.com/robot/send?access_token=$accessToken"
        val secret = settings.dingTalkSecret.takeIf { it.isNotEmpty() }
        if (secret != null) {
            val timestamp = System.currentTimeMillis()
            val stringToSign = "$timestamp\n$secret"
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val sign = URLEncoder.encode(
                Base64.encodeToString(mac.doFinal(stringToSign.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP),
                "UTF-8"
            )
            url += "&timestamp=$timestamp&sign=$sign"
        }

        val body = JsonUtils.common.encodeToString(
            DingTalkRequest(
                msgtype = "text",
                text = DingTalkText("$title: $content"),
            )
        )

        return runCatching {
            httpClient.post(url, body).use { response ->
                val responseBody = response.body.string()
                response.isSuccessful && JsonUtils.common.decodeFromString<DingTalkResponse>(responseBody).errcode == 0
            }
        }.getOrElse {
            Timber.e(it, "DingTalk send failed")
            false
        }
    }

    @Serializable
    private data class DingTalkRequest(
        val msgtype: String,
        val text: DingTalkText,
    )

    @Serializable
    private data class DingTalkText(
        val content: String,
    )

    @Serializable
    private data class DingTalkResponse(
        val errcode: Int = -1,
    )
}
