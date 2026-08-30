package com.aliothmoon.maameow.data.notification

import com.aliothmoon.maameow.R

/**
 * 自定义 Webhook 预置模板
 * 占位符与 CustomWebhookProvider 一致：{title} {content} {time}
 * 尖括号片段（<topic> 等）留给用户替换
 */
data class WebhookPresetTemplate(
    val id: String,
    val labelRes: Int,
    val url: String = "",
    val headers: String = "",
    val body: String = "",
)

const val WEBHOOK_PRESET_CUSTOM_ID = "__custom__"

val WEBHOOK_PRESET_TEMPLATES: List<WebhookPresetTemplate> = listOf(
    WebhookPresetTemplate(
        id = WEBHOOK_PRESET_CUSTOM_ID,
        labelRes = R.string.notification_webhook_preset_custom,
    ),
    WebhookPresetTemplate(
        id = "Discord Webhook",
        labelRes = R.string.notification_webhook_preset_discord,
        body = """{"content": "{content}"}""",
    ),
    WebhookPresetTemplate(
        id = "Kook Channel",
        labelRes = R.string.notification_webhook_preset_kook_channel,
        url = "https://www.kookapp.cn/api/v3/message/create",
        headers = "Authorization: Bot <bot_token>",
        body = """{"type": 9, "target_id": "<channel_id>", "content": "**{title}**\n{content}"}""",
    ),
    WebhookPresetTemplate(
        id = "Kook Direct",
        labelRes = R.string.notification_webhook_preset_kook_direct,
        url = "https://www.kookapp.cn/api/v3/direct-message/create",
        headers = "Authorization: Bot <bot_token>",
        body = """{"type": 9, "target_id": "<user_id>", "content": "**{title}**\n{content}"}""",
    ),
    WebhookPresetTemplate(
        id = "meow",
        labelRes = R.string.notification_webhook_preset_meow,
        url = "https://api.chuckfang.com/<nickname>",
        body = """{"title":"{title}","msg":"{content}\n{time}"}""",
    ),
    WebhookPresetTemplate(
        id = "ntfy",
        labelRes = R.string.notification_webhook_preset_ntfy,
        url = "https://ntfy.sh/<topic>",
        body = """{"message": "{content}", "title": "{title}"}""",
    ),
    WebhookPresetTemplate(
        id = "WeCom",
        labelRes = R.string.notification_webhook_preset_wecom,
        url = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=<key>",
        body = """{"msgtype": "text", "text": {"content": "{content}"}}""",
    ),
)

// 模板无 headers 时清空，避免残留上一个模板的凭证
fun NotificationSettings.withWebhookPreset(id: String): NotificationSettings {
    val template = WEBHOOK_PRESET_TEMPLATES.firstOrNull { it.id == id }
    if (template == null || template.id == WEBHOOK_PRESET_CUSTOM_ID) {
        return copy(customWebhookPresetId = id)
    }
    return copy(
        customWebhookPresetId = id,
        customWebhookUrl = template.url,
        customWebhookHeaders = template.headers,
        customWebhookBody = template.body,
    )
}

// 导入恢复：导出脱敏清掉了 URL/Headers，选中预置且两字段皆空则按模板补回，Body 未脱敏不动
fun NotificationSettings.reapplyWebhookPresetIfBlank(): NotificationSettings {
    if (customWebhookUrl.isNotBlank() || customWebhookHeaders.isNotBlank()) return this
    val template =
        WEBHOOK_PRESET_TEMPLATES.firstOrNull { it.id == customWebhookPresetId } ?: return this
    return copy(customWebhookUrl = template.url, customWebhookHeaders = template.headers)
}
