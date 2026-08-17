package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettings
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.utils.i18n.UiText
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CustomWebhookProviderTest {

    private val httpClient = mockk<HttpClientHelper>()
    private val settingsManager = mockk<NotificationSettingsManager>()

    private fun providerWith(settings: NotificationSettings): CustomWebhookProvider {
        every { settingsManager.settings } returns flowOf(settings)
        return CustomWebhookProvider(httpClient, settingsManager)
    }

    private val UiText.resIdValue: Int
        get() = (this as UiText.Resource).resId

    @Test
    fun emptyUrl_returnsMissingConfig() = runBlocking {
        val provider = providerWith(NotificationSettings(customWebhookBody = "{title}"))
        val result = provider.send("t", "c")
        assertTrue(result is NotificationSendResult.Failed)
        assertEquals(
            R.string.notification_err_webhook_url_empty,
            (result as NotificationSendResult.Failed).message.resIdValue
        )
    }

    @Test
    fun emptyBody_returnsMissingConfig() = runBlocking {
        val provider = providerWith(
            NotificationSettings(customWebhookUrl = "http://x", customWebhookBody = "")
        )
        val result = provider.send("t", "c")
        assertTrue(result is NotificationSendResult.Failed)
        assertEquals(
            R.string.notification_err_webhook_body_empty,
            (result as NotificationSendResult.Failed).message.resIdValue
        )
    }

    @Test
    fun httpError_returnsFailed() = runBlocking {
        every { settingsManager.settings } returns flowOf(
            NotificationSettings(customWebhookUrl = "http://x", customWebhookBody = "{title}")
        )
        coEvery {
            httpClient.post(any(), any(), any(), any())
        } returns buildResponse(500, "boom")
        val provider = CustomWebhookProvider(httpClient, settingsManager)
        val result = provider.send("t", "c")
        assertTrue("expected Failed but was $result", result is NotificationSendResult.Failed)
    }

    @Test
    fun ioException_returnsTransient() = runBlocking {
        every { settingsManager.settings } returns flowOf(
            NotificationSettings(customWebhookUrl = "http://x", customWebhookBody = "{title}")
        )
        coEvery { httpClient.post(any(), any(), any(), any()) } throws IOException("timeout")
        val provider = CustomWebhookProvider(httpClient, settingsManager)
        val result = provider.send("t", "c")
        assertTrue("expected Transient but was $result", result is NotificationSendResult.Transient)
    }

    @Test
    fun placeholders_areJsonEscaped() = runBlocking {
        val body = slot<String>()
        every { settingsManager.settings } returns flowOf(
            NotificationSettings(
                customWebhookUrl = "http://x",
                customWebhookBody = """{"text":"{title}\n{content}"}""",
            )
        )
        coEvery {
            httpClient.post(any(), capture(body), any(), any())
        } returns buildResponse(200, "ok")

        CustomWebhookProvider(httpClient, settingsManager)
            .send("""带"引号"的标题""", """路径 C:\Users\x
第二行""")

        // 未转义时这里会解析失败——正是上游修的那个 bug
        val parsed = Json.parseToJsonElement(body.captured).jsonObject
        val text = parsed["text"]!!.jsonPrimitive.content
        assertTrue(text, text.contains("""带"引号"的标题"""))
        assertTrue(text, text.contains("""C:\Users\x"""))
        assertTrue(text, text.contains("第二行"))
    }

    @Test
    fun headers_areParsedAndInvalidNamesDropped() = runBlocking {
        val headers = slot<Map<String, String>>()
        every { settingsManager.settings } returns flowOf(
            NotificationSettings(
                customWebhookUrl = "http://x",
                customWebhookBody = "{title}",
                customWebhookHeaders = "Content-Type: application/json; charset=utf-8\n" +
                        "{\"Authorization\": Bearer abc\n" +
                        "X-Token: abc",
            )
        )
        coEvery {
            httpClient.post(any(), any(), any(), capture(headers))
        } returns buildResponse(200, "ok")

        CustomWebhookProvider(httpClient, settingsManager).send("t", "c")

        assertEquals(
            mapOf(
                "Content-Type" to "application/json; charset=utf-8",
                "X-Token" to "abc",
            ),
            headers.captured,
        )
    }

    private fun buildResponse(code: Int, body: String): Response {
        val request = Request.Builder().url("http://localhost").build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(body.toResponseBody(null))
            .build()
    }
}
