package com.aliothmoon.maameow.web

import android.content.Context
import com.aliothmoon.maameow.web.api.WebUiBackend
import com.aliothmoon.maameow.web.auth.WebUiAuth
import com.aliothmoon.maameow.web.server.WebAssetSource
import com.aliothmoon.maameow.web.server.webUiModule
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.FileNotFoundException

/** 网页控制台总开关：随设置起停 Ktor，前台服务只负责保活与常驻通知 */
class WebUiController(
    private val context: Context,
    private val settings: WebUiSettingsManager,
    val auth: WebUiAuth,
    private val backend: WebUiBackend,
) {
    sealed interface ServerState {
        data object Stopped : ServerState
        data class Running(val port: Int) : ServerState
        data class Failed(val port: Int, val reason: String) : ServerState
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ServerState>(ServerState.Stopped)
    val state: StateFlow<ServerState> = _state.asStateFlow()

    private val assets = WebAssetSource { path ->
        try {
            context.assets.open("$ASSET_ROOT/$path")
        } catch (_: FileNotFoundException) {
            null
        }
    }

    private var server: EmbeddedServer<*, *>? = null

    fun start() {
        scope.launch {
            auth.load()
            combine(settings.enabled, settings.port, ::Pair)
                .distinctUntilChanged()
                .collectLatest { (enabled, port) ->
                    stopServer()
                    if (enabled) {
                        startServer(port)
                        // 停由服务自己看状态 stopSelf，外部 stopService 可能抢在 startForeground 前
                        if (_state.value is ServerState.Running) WebUiService.start(context)
                    } else {
                        _state.value = ServerState.Stopped
                    }
                }
        }
    }

    fun setEnabled(enabled: Boolean) {
        scope.launch { settings.setEnabled(enabled) }
    }

    private fun startServer(port: Int) {
        _state.value = try {
            server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                webUiModule(backend, auth, assets)
            }.start(wait = false)
            Timber.i("WebUi: listening on %d", port)
            ServerState.Running(port)
        } catch (e: Exception) {
            Timber.w(e, "WebUi: start on %d failed", port)
            server = null
            ServerState.Failed(port, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun stopServer() {
        val current = server ?: return
        server = null
        runCatching { current.stop(gracePeriodMillis = 200, timeoutMillis = 1_000) }
            .onFailure { Timber.w(it, "WebUi: stop failed") }
        _state.value = ServerState.Stopped
    }

    private companion object {
        const val ASSET_ROOT = "webui"
    }
}
