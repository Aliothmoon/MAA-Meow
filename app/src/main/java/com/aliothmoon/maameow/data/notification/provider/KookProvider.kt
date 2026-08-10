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

private const val CHANNEL_URL = "https://www.kookapp.cn/api/v3/message/create"
private const val DIRECT_URL = "https://www.kookapp.cn/api/v3/direct-message/create"

/** KOOK 消息类型 9 = KMarkdown */
private const val KMARKDOWN = 9

/**
 * KOOK 机器人推送，对齐上游 WebhookPresetTemplate 的 KOOK Channel / KOOK Direct 两条预置模板
 * 频道和私聊只差一个接口地址与 target_id 的含义
 */
class KookProvider(
    private val httpClient: HttpClientHelper,
    private val settingsManager: NotificationSettingsManager
) : NotificationProvider {

    override val id = "KOOK"

    override suspend fun send(title: String, content: String): NotificationSendResult {
        val settings = settingsManager.settings.first()
        val botToken = settings.kookBotToken.takeIf { it.isNotEmpty() }
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_kook_token_empty)
            )
        val targetId = settings.kookTargetId.takeIf { it.isNotEmpty() }
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_kook_target_empty)
            )
        val direct = settings.kookDirectMessage.toBooleanStrictOrNull() == true

        val body = JsonUtils.common.encodeToString(
            KookRequest(
                type = KMARKDOWN,
                targetId = targetId,
                content = "**$title**\n$content",
            )
        )

        return runCatching {
            httpClient.post(
                url = if (direct) DIRECT_URL else CHANNEL_URL,
                body = body,
                headers = mapOf("Authorization" to "Bot $botToken"),
            ).use { response ->
                val responseBody = response.body.string()
                val parsed = runCatching {
                    JsonUtils.common.decodeFromString<KookResponse>(responseBody)
                }.getOrNull()
                when {
                    response.isSuccessful && parsed?.code == 0 -> NotificationSendResult.Success

                    response.code == 429 || response.code >= 500 -> {
                        Timber.w("KOOK transient: HTTP %d, body=%s", response.code, responseBody)
                        NotificationSendResult.Transient(
                            uiTextOf(R.string.notification_err_http_status, response.code),
                        )
                    }

                    // 鉴权失败、target 不存在都是 HTTP 200 + 业务码，只报 HTTP 码等于没报
                    parsed != null -> {
                        Timber.w("KOOK rejected: HTTP %d, body=%s", response.code, responseBody)
                        NotificationSendResult.Failed(
                            uiTextOf(
                                R.string.notification_err_kook_api,
                                parsed.code,
                                parsed.message.ifBlank { "-" },
                            ),
                        )
                    }

                    else -> {
                        Timber.w("KOOK rejected: HTTP %d, body=%s", response.code, responseBody)
                        NotificationSendResult.Failed(
                            uiTextOf(R.string.notification_err_http_status, response.code),
                        )
                    }
                }
            }
        }.getOrElse {
            Timber.e(it, "KOOK send failed")
            NotificationSendResult.Transient(uiTextOf(R.string.notification_err_network))
        }
    }

    @Serializable
    private data class KookRequest(
        val type: Int,
        @SerialName("target_id")
        val targetId: String,
        val content: String,
    )

    @Serializable
    private data class KookResponse(
        val code: Int = -1,
        val message: String = "",
    )
}
