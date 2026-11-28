package com.aliothmoon.maameow.web.server

import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.web.api.WebAppInfo
import com.aliothmoon.maameow.web.api.WebChain
import com.aliothmoon.maameow.web.api.WebChainNode
import com.aliothmoon.maameow.web.api.WebEvent
import com.aliothmoon.maameow.web.api.WebStatus
import com.aliothmoon.maameow.web.api.WebUiBackend
import com.aliothmoon.maameow.web.auth.WebSession
import com.aliothmoon.maameow.web.auth.WebUiAuth
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebUiModuleTest {

    private val status = MutableStateFlow(WebStatus("IDLE", "Connected", "BACKGROUND"))
    private val chain = MutableStateFlow(
        WebChain("p1", "日常", listOf(WebChainNode("n1", "刷理智", true, "COMBAT")))
    )
    private val logs = MutableStateFlow(listOf(LogItem(id = 1, content = "hello")))

    private val backend = object : WebUiBackend {
        override val app = WebAppInfo("1.0", "Test")
        override val status = this@WebUiModuleTest.status
        override val chain = this@WebUiModuleTest.chain
        override val logs = this@WebUiModuleTest.logs
    }

    private val auth = WebUiAuth(object : WebUiAuth.SessionStore {
        override suspend fun load() = emptyList<WebSession>()
        override suspend fun save(sessions: List<WebSession>) = Unit
    })

    private val files = mapOf(
        "index.html" to "<!doctype html><title>MaaMeow</title>",
        "assets/index-abc.js" to "console.log(1)",
    )

    private fun withApp(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        application {
            webUiModule(backend, auth, WebAssetSource { files[it]?.byteInputStream() })
        }
        val client = createClient {
            install(HttpCookies)
            install(WebSockets)
        }
        block(client)
    }

    private suspend fun HttpClient.pair(code: String) = post("/api/pair") {
        contentType(ContentType.Application.Json)
        setBody("""{"code":"$code","name":"JUnit"}""")
    }

    private suspend fun HttpClient.pairOk() {
        val response = pair(auth.newPairingCode().code)
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    private suspend fun DefaultClientWebSocketSession.nextEvent(): WebEvent = withTimeout(5_000) {
        val frame = incoming.receive() as Frame.Text
        WebJson.decodeFromString(WebEvent.serializer(), frame.readText())
    }

    @Test
    fun `api requires a paired session`() = withApp { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/snapshot").status)
    }

    @Test
    fun `wrong or missing code is rejected`() = withApp { client ->
        assertEquals(HttpStatusCode.Gone, client.pair("123456").status)

        val code = auth.newPairingCode().code
        val wrong = if (code == "000000") "111111" else "000000"
        val response = client.pair(wrong)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.bodyAsText().contains("invalid_code"))
    }

    @Test
    fun `pairing sets a strict http-only cookie and unlocks snapshot`() = withApp { client ->
        val response = client.pair(auth.newPairingCode().code)
        val cookie = response.headers[HttpHeaders.SetCookie].orEmpty()
        assertTrue(cookie, cookie.contains("HttpOnly"))
        assertTrue(cookie, cookie.contains("SameSite=Strict"))

        val snapshot = client.get("/api/snapshot")
        assertEquals(HttpStatusCode.OK, snapshot.status)
        val event = WebJson.decodeFromString(WebEvent.serializer(), snapshot.bodyAsText())
        event as WebEvent.Snapshot
        assertEquals("刷理智", event.chain.nodes.single().name)
        assertEquals("hello", event.logs.single().content)
        assertEquals("DEFAULT", event.logs.single().color)
    }

    @Test
    fun `logout revokes the session`() = withApp { client ->
        client.pairOk()
        assertEquals(HttpStatusCode.NoContent, client.post("/api/logout").status)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/snapshot").status)
        assertTrue(auth.sessions.value.isEmpty())
    }

    @Test
    fun `websocket pushes snapshot then deltas`() = withApp { client ->
        client.pairOk()
        client.webSocket("/api/events") {
            assertTrue(nextEvent() is WebEvent.Snapshot)

            logs.value = logs.value + LogItem(id = 2, content = "world")
            val delta = nextEvent() as WebEvent.Logs
            assertFalse(delta.reset)
            assertEquals(listOf("world"), delta.items.map { it.content })

            status.value = status.value.copy(execution = "RUNNING")
            assertEquals("RUNNING", (nextEvent() as WebEvent.Status).status.execution)

            logs.value = emptyList()
            val cleared = nextEvent() as WebEvent.Logs
            assertTrue(cleared.reset)
            assertTrue(cleared.items.isEmpty())
        }
    }

    @Test
    fun `revoking the session closes an open websocket`() = withApp { client ->
        client.pairOk()
        client.webSocket("/api/events") {
            assertTrue(nextEvent() is WebEvent.Snapshot)
            auth.revokeAll()

            val reason = withTimeout(5_000) { closeReason.await() }
            assertEquals("unauthorized", reason?.message)
        }
    }

    @Test
    fun `websocket without session is closed`() = withApp { client ->
        client.webSocket("/api/events") {
            val reason = withTimeout(5_000) { closeReason.await() }
            assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, reason?.code)
        }
    }

    @Test
    fun `websocket from another origin is closed`() = withApp { client ->
        client.pairOk()
        client.webSocket("/api/events", request = { header(HttpHeaders.Origin, "http://evil.example") }) {
            val reason = withTimeout(5_000) { closeReason.await() }
            assertEquals("origin", reason?.message)
        }
    }

    @Test
    fun `static files and spa fallback`() = withApp { client ->
        val index = client.get("/")
        assertEquals(HttpStatusCode.OK, index.status)
        assertEquals("no-cache", index.headers[HttpHeaders.CacheControl])
        assertTrue(index.headers["Content-Security-Policy"].orEmpty().contains("default-src 'self'"))

        val js = client.get("/assets/index-abc.js")
        assertTrue(js.headers[HttpHeaders.ContentType].orEmpty().contains("javascript"))
        assertTrue(js.headers[HttpHeaders.CacheControl].orEmpty().contains("immutable"))

        assertTrue(client.get("/logs/today").bodyAsText().contains("<title>MaaMeow"))
        assertEquals(HttpStatusCode.NotFound, client.get("/missing.js").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/nope").status)
    }
}
