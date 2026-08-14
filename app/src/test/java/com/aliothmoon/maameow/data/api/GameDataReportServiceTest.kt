package com.aliothmoon.maameow.data.api

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.service.CoreReportRequest
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameDataReportServiceTest {

    private val penguinIdState = MutableStateFlow("")
    private val appSettings = mockk<AppSettingsManager>(relaxUnitFun = true) {
        every { penguinId } returns penguinIdState
    }
    private val taskChainState = mockk<TaskChainState> {
        every { clientType } returns "Official"
    }
    private val sessionLogger = mockk<MaaSessionLogger>(relaxUnitFun = true)
    private val context = mockk<Context> {
        every { getString(R.string.report_http_failed) } returns "report failed"
    }

    @Test
    fun penguin200_savesIdWhenLocalEmpty() = runBlocking {
        val urls = mutableListOf<String>()
        var captured: okhttp3.Request? = null
        val service = service { request ->
            captured = request
            urls += request.url.toString()
            ok(request.url.toString(), penguinId = "888")
        }

        assertTrue(service.post(penguinRequest()))
        assertEquals(listOf("https://penguin-stats.io/PenguinStats/api/v2/report"), urls)
        assertEquals("application/json", captured!!.header("Accept"))
        assertTrue(captured.headers("User-Agent").any { it.startsWith("MaaMeow/") })
        assertTrue(captured.headers("User-Agent").contains("MaaAssistantArknights/dev"))
        coVerify { appSettings.setPenguinId("888") }
    }

    @Test
    fun penguin200_doesNotOverwriteExistingId() = runBlocking {
        penguinIdState.value = "111"
        val service = service { request ->
            ok(request.url.toString(), penguinId = "888")
        }

        assertTrue(service.post(penguinRequest()))
        coVerify(exactly = 0) { appSettings.setPenguinId(any()) }
    }

    @Test
    fun penguinIoFails_fallsBackToCn() = runBlocking {
        val urls = mutableListOf<String>()
        val service = service { request ->
            urls += request.url.toString()
            if (request.url.host.contains(".io")) {
                error(request.url.toString(), 503)
            } else {
                ok(request.url.toString(), penguinId = "5")
            }
        }

        assertTrue(service.post(penguinRequest()))
        assertTrue(urls.any { it.contains("penguin-stats.io") })
        assertTrue(urls.any { it.contains("penguin-stats.cn") })
        coVerify { appSettings.setPenguinId("5") }
    }

    @Test
    fun yituliuFailure_isTreatedAsSuccess() = runBlocking {
        val service = service { request -> error(request.url.toString(), 500) }

        assertTrue(
            service.post(
                CoreReportRequest(
                    url = "https://backend.yituliu.cn/maa/upload/stageDrop",
                    headers = emptyMap(),
                    body = "{}",
                    subtask = GameDataReportService.SUBTASK_YITULIU,
                ),
            ),
        )
        verify(exactly = 0) { sessionLogger.append(any(), any(), any()) }
    }

    @Test
    fun txwy_skipsPenguinHttp() = runBlocking {
        every { taskChainState.clientType } returns "txwy"
        var called = 0
        val service = service {
            called += 1
            ok(it.url.toString())
        }

        assertTrue(service.post(penguinRequest()))
        assertEquals(0, called)
    }

    private fun service(
        handler: (okhttp3.Request) -> Response,
    ): GameDataReportService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain -> handler(chain.request()) }
            .build()
        return GameDataReportService(
            appContext = context,
            httpClient = client,
            appSettings = appSettings,
            taskChainState = taskChainState,
            sessionLogger = sessionLogger,
            delayMs = {},
        )
    }

    private fun penguinRequest() = CoreReportRequest(
        url = "https://penguin-stats.io/PenguinStats/api/v2/report",
        headers = mapOf("User-Agent" to "MaaAssistantArknights/dev"),
        body = """{"server":"CN"}""",
        subtask = GameDataReportService.SUBTASK_PENGUIN,
    )

    private fun ok(url: String, penguinId: String? = null): Response {
        val builder = Response.Builder()
            .request(okhttp3.Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("ok".toResponseBody("application/json".toMediaType()))
        if (penguinId != null) {
            builder.header(GameDataReportService.PENGUIN_ID_HEADER, penguinId)
        }
        return builder.build()
    }

    private fun error(url: String, code: Int): Response = Response.Builder()
        .request(okhttp3.Request.Builder().url(url).build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("ERR")
        .body("err".toResponseBody("text/plain".toMediaType()))
        .build()
}
