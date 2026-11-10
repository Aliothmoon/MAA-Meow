package com.aliothmoon.maameow.telemetry

import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.data.notification.NotificationSettings
import com.aliothmoon.maameow.domain.models.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretRedactionTest {

    private fun redact(text: String, vararg secrets: String) =
        SecretRedaction.redact(text, SecretRedaction.redactable(secrets.toList()))

    @Test
    fun `账号与汇报 ID 按键名整段换掉`() {
        assertEquals(
            """Assistant::append_task | StartUp {"account_name":"***","client_type":"Official"}""",
            redact("""Assistant::append_task | StartUp {"account_name":"138","client_type":"Official"}"""),
        )
        assertEquals(
            """{"penguin_id": "***", "yituliu_id":"***","server":"CN"}""",
            redact("""{"penguin_id": "12345678", "yituliu_id":"12345678","server":"CN"}"""),
        )
    }

    @Test
    fun `转义过的引号也认`() {
        assertEquals(
            """{"content":"[TaskParams] StartUp: {\"client_type\":\"Official\",\"account_name\":\"***\"}"}""",
            redact("""{"content":"[TaskParams] StartUp: {\"client_type\":\"Official\",\"account_name\":\"abc@x.com\"}"}"""),
        )
    }

    @Test
    fun `别的键原样留着`() {
        val text = """{"stage":"1-7","client_type":"Official","name":"account_name"}"""
        assertEquals(text, redact(text))
    }

    @Test
    fun `密钥按值换掉，长的先换`() {
        assertEquals(
            "POST https://api.day.app/*** token=***",
            redact("POST https://api.day.app/abcd1234 token=abcd1234-long", "abcd1234", "abcd1234-long"),
        )
    }

    @Test
    fun `太短的值不按值换`() {
        assertEquals("abc is everywhere", redact("abc is everywhere", "abc", " ", ""))
    }

    @Test
    fun `收集当前保存的各类密钥，解锁 PIN 不在内`() {
        val secrets = TelemetrySecrets.collect(
            app = AppSettings(
                penguinId = "12345678",
                yituliuOpenApiToken = "yituliu-token",
                mirrorChyanCdk = "CDK-0001",
                wakeCredential = "2026",
            ),
            notification = NotificationSettings(telegramBotToken = "123:telegram", barkSendKey = "bark-key"),
            chains = listOf(
                listOf(TaskChainNode(name = "开始唤醒", config = WakeUpConfig(accountName = "13800000000"))),
                listOf(TaskChainNode(name = "开始唤醒", config = WakeUpConfig())),
            ),
            yituliuProfileTokens = listOf("yituliu-token-a", "yituliu-token-b", ""),
        )

        assertEquals(
            setOf(
                "12345678", "yituliu-token", "yituliu-token-a", "yituliu-token-b", "CDK-0001",
                "123:telegram", "bark-key", "13800000000",
            ),
            secrets.toSet(),
        )
        assertFalse("2026" in secrets)
        assertTrue(secrets.none(String::isBlank))
    }
}
