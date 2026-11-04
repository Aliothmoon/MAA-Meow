package com.aliothmoon.maameow.web

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.aliothmoon.maameow.web.auth.WebSession
import com.aliothmoon.maameow.web.auth.WebUiAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber

class WebUiSettingsManager(private val context: Context) : WebUiAuth.SessionStore {

    companion object {
        const val DEFAULT_PORT = 8686
        val PORT_RANGE = 1024..65535

        private val Context.webUiDataStore: DataStore<Preferences> by preferencesDataStore(name = "webui_settings")

        private val json = Json { ignoreUnknownKeys = true }
        private val sessionListSerializer = ListSerializer(WebSession.serializer())

        fun parsePort(raw: String): Int = raw.toIntOrNull()?.takeIf { it in PORT_RANGE } ?: DEFAULT_PORT
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val settings: Flow<WebUiSettings> =
        with(WebUiSettingsSchema) { context.webUiDataStore.flow }

    val enabled: StateFlow<Boolean> = settings
        .map { it.enabled.toBooleanStrictOrNull() ?: false }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)

    val port: StateFlow<Int> = settings
        .map { parsePort(it.port) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_PORT)

    suspend fun setEnabled(value: Boolean) {
        context.webUiDataStore.edit { it[WebUiSettingsSchema.enabled] = value.toString() }
    }

    suspend fun setPort(value: Int) {
        require(value in PORT_RANGE)
        context.webUiDataStore.edit { it[WebUiSettingsSchema.port] = value.toString() }
    }

    override suspend fun load(): List<WebSession> {
        val raw = settings.first().sessions
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString(sessionListSerializer, raw) }
            .onFailure { Timber.w(it, "WebUi: broken session list, dropped") }
            .getOrDefault(emptyList())
    }

    override suspend fun save(sessions: List<WebSession>) {
        val raw = json.encodeToString(sessionListSerializer, sessions)
        context.webUiDataStore.edit { it[WebUiSettingsSchema.sessions] = raw }
    }
}
