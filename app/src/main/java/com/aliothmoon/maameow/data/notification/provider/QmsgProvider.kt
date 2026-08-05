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

class QmsgProvider(
    private val httpClient: HttpClientHelper,
    private val settingsManager: NotificationSettingsManager
) : NotificationProvider {

    override val id = "Qmsg"

    override suspend fun send(title: String, content: String): NotificationSendResult {
        val settings = settingsManager.settings.first()
        val server = settings.qmsgServer.takeIf { it.isNotBlank() }?.trimEnd('/')
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_qmsg_server_empty)
            )
        val key = settings.qmsgKey.takeIf { it.isNotBlank() }
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_qmsg_key_empty)
            )
        val body = JsonUtils.common.encodeToString(
            QmsgRequest(
                msg = content,
                qq = settings.qmsgUser,
                bot = settings.qmsgBot,
            )
        )

        return runCatching {
            httpClient.post("$server/jsend/$key", body).use { response ->
                val responseBody = response.body.string()
                if (response.isSuccessful &&
                    JsonUtils.common.decodeFromString<QmsgResponse>(responseBody).success
                ) {
                    NotificationSendResult.Success
                } else {
                    Timber.w("Qmsg rejected: HTTP %d, body=%s", response.code, responseBody)
                    NotificationSendResult.Failed(
                        uiTextOf(R.string.notification_err_http_status, response.code),
                    )
                }
            }
        }.getOrElse {
            Timber.e(it, "Qmsg send failed")
            NotificationSendResult.Transient(uiTextOf(R.string.notification_err_network))
        }
    }

    @Serializable
    private data class QmsgRequest(
        val msg: String,
        val qq: String,
        val bot: String,
    )

    @Serializable
    private data class QmsgResponse(
        @SerialName("success") val success: Boolean = false,
    )
}
