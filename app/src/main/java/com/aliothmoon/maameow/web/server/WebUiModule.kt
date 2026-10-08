package com.aliothmoon.maameow.web.server

import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.web.api.ErrorBody
import com.aliothmoon.maameow.web.api.LogDelta
import com.aliothmoon.maameow.web.api.PairRequest
import com.aliothmoon.maameow.web.api.WebEvent
import com.aliothmoon.maameow.web.api.WebUiBackend
import com.aliothmoon.maameow.web.api.toWeb
import com.aliothmoon.maameow.web.auth.WebSession
import com.aliothmoon.maameow.web.auth.WebUiAuth
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.defaultForFilePath
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.InputStream
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/** 前端静态文件，路径相对 webui 产物根目录 */
fun interface WebAssetSource {
    fun open(path: String): InputStream?
}

internal const val SESSION_COOKIE = "maameow_session"

internal val WebJson = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

private val SESSION_MAX_AGE = 400.days

private val SecurityHeaders = createApplicationPlugin("WebUiSecurityHeaders") {
    onCall { call ->
        val host = call.request.headers[HttpHeaders.Host].orEmpty()
        call.response.headers.apply {
            append("X-Content-Type-Options", "nosniff")
            append("X-Frame-Options", "DENY")
            append("Referrer-Policy", "no-referrer")
            append(
                "Content-Security-Policy",
                "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; " +
                    "connect-src 'self' ws://$host; frame-ancestors 'none'; base-uri 'none'",
            )
        }
    }
}

fun Application.webUiModule(backend: WebUiBackend, auth: WebUiAuth, assets: WebAssetSource) {
    install(ContentNegotiation) { json(WebJson) }
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
    }

    install(SecurityHeaders)

    routing {
        route("/api") {
            post("/pair") {
                val request = runCatching { call.receive<PairRequest>() }.getOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorBody("bad_request"))
                when (val result = auth.pair(request.code, request.name)) {
                    is WebUiAuth.PairResult.Success -> {
                        call.setSessionCookie(result.token, SESSION_MAX_AGE.inWholeSeconds.toInt())
                        call.respond(HttpStatusCode.NoContent)
                    }

                    WebUiAuth.PairResult.Invalid ->
                        call.respond(HttpStatusCode.Unauthorized, ErrorBody("invalid_code"))

                    WebUiAuth.PairResult.NoCode ->
                        call.respond(HttpStatusCode.Gone, ErrorBody("no_code"))
                }
            }

            post("/logout") {
                call.sessionToken()?.let { auth.revokeToken(it) }
                call.setSessionCookie("", 0)
                call.respond(HttpStatusCode.NoContent)
            }

            get("/snapshot") {
                if (!auth.verify(call.sessionToken())) {
                    return@get call.respond(HttpStatusCode.Unauthorized, ErrorBody("unauthorized"))
                }
                // 按基类型序列化才带 type，与推送格式一致
                call.respond<WebEvent>(snapshot(backend))
            }

            webSocket("/events") {
                // 升级后再拒：浏览器 WebSocket 拿不到握手的状态码，只认关闭码
                if (!call.isSameOrigin()) {
                    return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "origin"))
                }
                val token = call.sessionToken()?.takeIf { auth.verify(it) }
                    ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "unauthorized"))
                streamEvents(backend, auth.sessions, WebUiAuth.hash(token))
            }

            get("/{rest...}") {
                call.respond(HttpStatusCode.NotFound, ErrorBody("not_found"))
            }
        }

        get("/{path...}") {
            call.respondAsset(assets, call.parameters.getAll("path").orEmpty())
        }
    }
}

private suspend fun snapshot(
    backend: WebUiBackend,
    logs: List<LogItem> = backend.logs.value,
) = WebEvent.Snapshot(
    app = backend.app,
    status = backend.status.first(),
    chain = backend.chain.first(),
    logs = logs.map { it.toWeb() },
)

private suspend fun DefaultWebSocketServerSession.streamEvents(
    backend: WebUiBackend,
    sessions: Flow<List<WebSession>>,
    sessionHash: String,
) {
    suspend fun send(event: WebEvent) {
        outgoing.send(Frame.Text(WebJson.encodeToString(WebEvent.serializer(), event)))
    }

    // 快照与增量基线必须是同一份表
    val logs = backend.logs.value
    val delta = LogDelta().apply { next(logs) }
    val initial = snapshot(backend, logs)
    send(initial)

    coroutineScope {
        val jobs = listOf(
            launch {
                var last = initial.status
                backend.status.collect {
                    if (it == last) return@collect
                    last = it
                    send(WebEvent.Status(it))
                }
            },
            launch {
                var last = initial.chain
                backend.chain.collect {
                    if (it == last) return@collect
                    last = it
                    send(WebEvent.Chain(it))
                }
            },
            // App 里移除了这个浏览器就立刻断开，不等它下次重连
            launch {
                sessions.first { list -> list.none { it.hash == sessionHash } }
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "unauthorized"))
            },
            launch {
                // StateFlow 自带合并，慢客户端只会拿到最新整表
                backend.logs.collect { logs ->
                    val change = delta.next(logs) ?: return@collect
                    send(WebEvent.Logs(change.items.map { it.toWeb() }, change.reset))
                }
            },
        )
        // 网页只读不发，排空入站帧直到断开
        incoming.consumeEach { }
        jobs.forEach { it.cancel() }
    }
}

private fun ApplicationCall.sessionToken(): String? =
    request.cookies[SESSION_COOKIE]?.takeIf { it.isNotEmpty() }

private fun ApplicationCall.setSessionCookie(value: String, maxAge: Int) {
    response.cookies.append(
        Cookie(
            name = SESSION_COOKIE,
            value = value,
            maxAge = maxAge,
            path = "/",
            httpOnly = true,
            extensions = mapOf("SameSite" to "Strict"),
        )
    )
}

/** 浏览器跨站发起的 WebSocket 会带别家 Origin */
private fun ApplicationCall.isSameOrigin(): Boolean {
    val origin = request.headers[HttpHeaders.Origin] ?: return true
    val host = request.headers[HttpHeaders.Host] ?: return false
    val parsed = runCatching { Url(origin) }.getOrNull() ?: return false
    val authority = if (parsed.specifiedPort == 0) parsed.host else "${parsed.host}:${parsed.specifiedPort}"
    return authority.equals(host, ignoreCase = true)
}

private suspend fun ApplicationCall.respondAsset(assets: WebAssetSource, segments: List<String>) {
    if (segments.any { it == ".." || it == "." || '\\' in it }) {
        return respond(HttpStatusCode.NotFound)
    }
    val path = segments.filter { it.isNotEmpty() }.joinToString("/").ifEmpty { INDEX }
    val bytes = assets.open(path)?.use { it.readBytes() }
    if (bytes != null) {
        // Vite 产物带内容哈希，可长缓存
        val cache = if (path.startsWith("assets/")) "public, max-age=31536000, immutable" else "no-cache"
        response.headers.append(HttpHeaders.CacheControl, cache)
        return respondBytes(bytes, ContentType.defaultForFilePath(path))
    }
    if ('.' in segments.lastOrNull().orEmpty()) {
        return respond(HttpStatusCode.NotFound)
    }
    // 前端路由回落到 index.html
    val index = assets.open(INDEX)?.use { it.readBytes() } ?: NOT_BUNDLED_PAGE.toByteArray()
    response.headers.append(HttpHeaders.CacheControl, "no-cache")
    respondBytes(index, ContentType.Text.Html.withParameter("charset", "utf-8"))
}

private const val INDEX = "index.html"

// 本地构建缺 pnpm 时前端不会打进包
private val NOT_BUNDLED_PAGE = """
    <!doctype html><html><head><meta charset="utf-8"><title>MaaMeow</title></head>
    <body><p>网页前端未打包进本安装包 / Web UI is not bundled in this build.</p></body></html>
""".trimIndent()
