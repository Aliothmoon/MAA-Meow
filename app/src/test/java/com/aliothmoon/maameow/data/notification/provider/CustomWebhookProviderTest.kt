package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettings
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.utils.i18n.UiText
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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
