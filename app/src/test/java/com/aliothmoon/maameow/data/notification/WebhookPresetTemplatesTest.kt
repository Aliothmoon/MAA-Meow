package com.aliothmoon.maameow.data.notification

import com.aliothmoon.maameow.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookPresetTemplatesTest {

    private fun preconfigured() = NotificationSettings(
        customWebhookUrl = "https://old.example.com/hook",
        customWebhookHeaders = "X-Old: 1",
        customWebhookBody = """{"old":"{content}"}""",
    )

    @Test
    fun applyNtfyOverwritesAllFieldsAndClearsHeaders() {
        val result = preconfigured().withWebhookPreset("ntfy")
        assertEquals("ntfy", result.customWebhookPresetId)
        assertEquals("https://ntfy.sh/<topic>", result.customWebhookUrl)
        assertEquals("", result.customWebhookHeaders)
        assertEquals("""{"message": "{content}", "title": "{title}"}""", result.customWebhookBody)
    }

    @Test
    fun applyKookChannelFillsHeaders() {
        val result = preconfigured().withWebhookPreset("Kook Channel")
        assertEquals("https://www.kookapp.cn/api/v3/message/create", result.customWebhookUrl)
        assertEquals("Authorization: Bot <bot_token>", result.customWebhookHeaders)
        assertTrue(result.customWebhookBody.contains("<channel_id>"))
    }

    @Test
    fun selectCustomOnlyChangesId() {
        val result = preconfigured().withWebhookPreset(WEBHOOK_PRESET_CUSTOM_ID)
        assertEquals(WEBHOOK_PRESET_CUSTOM_ID, result.customWebhookPresetId)
        assertEquals("https://old.example.com/hook", result.customWebhookUrl)
        assertEquals("X-Old: 1", result.customWebhookHeaders)
        assertEquals("""{"old":"{content}"}""", result.customWebhookBody)
    }

    @Test
    fun unknownIdOnlyChangesId() {
        val result = preconfigured().withWebhookPreset("no-such-template")
        assertEquals("no-such-template", result.customWebhookPresetId)
        assertEquals("https://old.example.com/hook", result.customWebhookUrl)
    }

    @Test
    fun templateIdsAreUnique() {
        val ids = WEBHOOK_PRESET_TEMPLATES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun customTemplatePresentWithEmptyFields() {
        val custom = WEBHOOK_PRESET_TEMPLATES.first { it.id == WEBHOOK_PRESET_CUSTOM_ID }
        assertEquals("", custom.url)
        assertEquals("", custom.headers)
        assertEquals("", custom.body)
    }

    @Test
    fun nonCustomTemplatesUseContentPlaceholder() {
        WEBHOOK_PRESET_TEMPLATES
            .filter { it.id != WEBHOOK_PRESET_CUSTOM_ID }
            .forEach { assertTrue("${it.id} body misses {content}", it.body.contains("{content}")) }
    }

    @Test
    fun labelResourcesAreDistinctPerTemplate() {
        val labels = WEBHOOK_PRESET_TEMPLATES.map { it.labelRes }
        assertEquals(labels.size, labels.toSet().size)
        assertEquals(
            R.string.notification_webhook_preset_custom,
            WEBHOOK_PRESET_TEMPLATES.first { it.id == WEBHOOK_PRESET_CUSTOM_ID }.labelRes,
        )
    }

    @Test
    fun generatedDefaultPinsCustomSentinel() {
        assertEquals(WEBHOOK_PRESET_CUSTOM_ID, NotificationSettingsSchema.Defaults.customWebhookPresetId)
    }

    @Test
    fun reapplyFillsBlankFieldsFromTemplate() {
        val restored = NotificationSettings(
            customWebhookBody = """{"message": "{content}", "title": "{title}"}""",
            customWebhookPresetId = "ntfy",
        ).reapplyWebhookPresetIfBlank()
        assertEquals("https://ntfy.sh/<topic>", restored.customWebhookUrl)
        assertEquals("", restored.customWebhookHeaders)
        assertEquals("""{"message": "{content}", "title": "{title}"}""", restored.customWebhookBody)
    }

    @Test
    fun reapplyKeepsCustomizedBody() {
        val restored = NotificationSettings(
            customWebhookBody = """{"custom": "{content}"}""",
            customWebhookPresetId = "Kook Channel",
        ).reapplyWebhookPresetIfBlank()
        assertEquals("https://www.kookapp.cn/api/v3/message/create", restored.customWebhookUrl)
        assertEquals("Authorization: Bot <bot_token>", restored.customWebhookHeaders)
        assertEquals("""{"custom": "{content}"}""", restored.customWebhookBody)
    }

    @Test
    fun reapplySkipsPopulatedSettings() {
        val restored = NotificationSettings(
            customWebhookUrl = "https://ntfy.example.com/my-topic",
            customWebhookHeaders = "X-Keep: 1",
            customWebhookPresetId = "ntfy",
        ).reapplyWebhookPresetIfBlank()
        assertEquals("https://ntfy.example.com/my-topic", restored.customWebhookUrl)
        assertEquals("X-Keep: 1", restored.customWebhookHeaders)
    }

    @Test
    fun reapplySkipsCustomAndUnknownPreset() {
        val custom = NotificationSettings(customWebhookPresetId = WEBHOOK_PRESET_CUSTOM_ID)
            .reapplyWebhookPresetIfBlank()
        assertEquals(WEBHOOK_PRESET_CUSTOM_ID, custom.customWebhookPresetId)
        assertEquals("", custom.customWebhookUrl)
        assertEquals("", custom.customWebhookHeaders)

        val unknown = NotificationSettings(customWebhookPresetId = "gone")
            .reapplyWebhookPresetIfBlank()
        assertEquals("gone", unknown.customWebhookPresetId)
        assertEquals("", unknown.customWebhookUrl)
    }
}
