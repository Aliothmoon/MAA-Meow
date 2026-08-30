package com.aliothmoon.maameow.manager

import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RemoteBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object RemoteAccessCoordinator {

    private val initialized = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val listener = RemoteAccessStateListener { refresh() }
    private val _state = MutableStateFlow(snapshot())

    @Volatile
    private var appSettings: AppSettingsManager? = null

    val state: StateFlow<RemoteAccessState> = _state.asStateFlow()

    private val backends = mapOf(
        RemoteBackend.ROOT to RootManager,
        RemoteBackend.SHIZUKU to ShizukuManager
    )

    fun initialize(appSettings: AppSettingsManager) {
        this.appSettings = appSettings
        if (initialized.compareAndSet(false, true)) {
            backends.values.forEach { it.addStateListener(listener) }

            scope.launch {
                val backend = configuredBackend()
                if (backend == RemoteBackend.ROOT) {
                    backends.getValue(RemoteBackend.ROOT).requestPermission()
                }
            }
        }
        refresh()
    }

    fun refresh(): RemoteAccessState {
        val current = snapshot()
        _state.value = current
        return current
    }

    fun isAvailable(backend: RemoteBackend): Boolean {
        return state.value.isAvailable(backend)
    }

    fun isGranted(backend: RemoteBackend): Boolean {
        return state.value.isGranted(backend)
    }

    suspend fun request(backend: RemoteBackend = configuredBackend()): Boolean {
        val current = refresh()
        if (current.isGranted(backend)) {
            return true
        }
        if (!current.isAvailable(backend)) {
            return false
        }
        val granted = backends.getValue(backend).requestPermission()
        val refreshed = refresh()
        return granted && refreshed.isGranted(backend)
    }

    fun configuredBackend(): RemoteBackend {
        return appSettings?.startupBackend?.value ?: RemoteBackend.SHIZUKU
    }

    private fun snapshot(): RemoteAccessState {
        val shizukuAvailable = ShizukuManager.isAvailable()
        val shizukuGranted = ShizukuManager.isGranted()
        val rootAvailable = RootManager.isAvailable()
        val rootGranted = RootManager.isGranted()
        return RemoteAccessState(
            shizukuAvailable = shizukuAvailable,
            shizukuGranted = shizukuGranted,
            rootAvailable = rootAvailable,
            rootGranted = rootGranted,
            configuredBackend = configuredBackend()
        )
    }
}
