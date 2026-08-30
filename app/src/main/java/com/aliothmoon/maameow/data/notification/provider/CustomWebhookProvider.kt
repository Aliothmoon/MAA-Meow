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
            .replace("{title}", escapeJsonString(title))
            .replace("{content}", escapeJsonString(content))
            .replace("{time}", now)

        val headers = settings.customWebhookHeaders
            .replace("\r", "")
            .split("\n")
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx <= 0) return@mapNotNull null
                val name = line.substring(0, idx).trim()
                // 用户误按 JSON 格式填写时名称会含大括号引号等分隔符，OkHttp 会直接抛异常
                if (!isValidHeaderName(name)) {
                    Timber.w("CustomWebhook skipped header with invalid name: %s", name)
                    return@mapNotNull null
                }
                name to line.substring(idx + 1).trim()
            }
            .toMap()

        return runCatching {
            httpClient.post(url, body, headers = headers).use { response ->
                if (response.isSuccessful) {
                    NotificationSendResult.Success
                } else {
                    val responseBody = response.body.string()
                    Timber.w(
                        "CustomWebhook rejected: HTTP %d, body=%s",
                        response.code,
                        responseBody
                    )
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

    private companion object {
        /** RFC 9110 里不允许出现在 header 名中的分隔符 */
        const val HEADER_NAME_DELIMITERS = "()<>@,;:\\\"/[]?={}"
        const val DEL = '\u007F'

        fun isValidHeaderName(name: String): Boolean = name.isNotEmpty() &&
                name.all { it > ' ' && it < DEL && it !in HEADER_NAME_DELIMITERS }

        /** 标题和内容原样嵌进 JSON 模板的字符串字面量，不转义会破坏 JSON 结构 */
        fun escapeJsonString(value: String): String = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "")
            .replace("\n", "\\n")
    }
}
