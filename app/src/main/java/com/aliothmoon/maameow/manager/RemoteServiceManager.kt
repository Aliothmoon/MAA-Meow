package com.aliothmoon.maameow.manager

import android.content.Context
import android.os.IBinder
import android.os.Process
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RemoteBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object RemoteServiceManager {

    sealed class ServiceState {
        data object Disconnected : ServiceState()
        data object Connecting : ServiceState()
        data object Died : ServiceState()
        data class Connected(val service: RemoteService) : ServiceState()
        data class Error(val exception: Throwable) : ServiceState()
    }

    private val currentBinder = AtomicReference<IBinder>()
    private val unbindingIntentionally = AtomicBoolean(false)
    private val _state = MutableStateFlow<ServiceState>(ServiceState.Disconnected)
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())

    private val connectors: Map<RemoteBackend, RemoteServiceConnectorBackend> = mapOf(
        RemoteBackend.SHIZUKU to ShizukuRemoteServiceConnector,
        RemoteBackend.ROOT to RootRemoteServiceConnector
    )

    private var boundBackend: RemoteBackend? = null

    val state: StateFlow<ServiceState> = _state.asStateFlow()

    private val connectorCallbacks = object : RemoteServiceConnectorBackend.Callbacks {
        override fun onConnected(backend: RemoteBackend, binder: IBinder) {
            if (boundBackend != backend) {
                Timber.w("Ignoring stale %s connection", backend)
                return
            }
            onBinderConnected(backend, binder)
        }

        override fun onDisconnected(backend: RemoteBackend) {
            if (boundBackend != backend) {
                return
            }
            if (unbindingIntentionally.get()) {
                return
            }
            Timber.i("RemoteService disconnected: %s", backend)
            handleDisconnect()
        }

        override fun onError(backend: RemoteBackend, throwable: Throwable) {
            if (boundBackend != backend) {
                return
            }
            Timber.e(throwable, "RemoteService connection failed: %s", backend)
            clearCurrentBinder()
            boundBackend = null
            _state.value = ServiceState.Error(throwable)
        }
    }

    private val deathRecipient = IBinder.DeathRecipient {
        Timber.w("RemoteService binder died")
        if (unbindingIntentionally.compareAndSet(true, false)) {
            handleDisconnect()
        } else {
            handleBinderDeath()
        }
    }

    fun initialize(context: Context, appSettings: AppSettingsManager) {
        RemoteAccessCoordinator.initialize(appSettings)
        RootRemoteServiceConnector.initialize(context)
        LogcatServiceManager.initialize(context)
    }

    private fun onBinderConnected(backend: RemoteBackend, binder: IBinder) {
        clearCurrentBinder()
        currentBinder.set(binder)
        binder.linkToDeath(deathRecipient, 0)
        boundBackend = backend
        val service = RemoteService.Stub.asInterface(binder)
        _state.value = ServiceState.Connected(service)
        service.heartbeat(Process.myPid())
    }

    private fun handleBinderDeath() {
        clearCurrentBinder()
        boundBackend = null
        _state.value = ServiceState.Died
    }

    private fun handleDisconnect() {
        if (_state.value == ServiceState.Died) {
            return
        }
        clearCurrentBinder()
        boundBackend = null
        _state.value = ServiceState.Disconnected
    }

    private fun clearCurrentBinder() {
        currentBinder.getAndSet(null)?.let { binder ->
            runCatching {
                binder.unlinkToDeath(deathRecipient, 0)
            }.onFailure {
                Timber.w(it, "unlinkToDeath failed")
            }
        }
    }

    fun bind() {
        val backend = RemoteAccessCoordinator.refresh().configuredBackend
        if (!RemoteAccessCoordinator.isGranted(backend)) {
            val exception = IllegalStateException("${backend.display} permission not granted")
            Timber.w(exception)
            boundBackend = null
            _state.value = ServiceState.Error(exception)
            return
        }

        if (_state.value is ServiceState.Connecting && boundBackend == backend) {
            return
        }

        if (boundBackend != null) {
            Timber.i("Unbinding old service before binding new one")
            unbindInternal()
            handleDisconnect()
        }

        boundBackend = backend
        _state.value = ServiceState.Connecting
        connectors.getValue(backend).connect(connectorCallbacks)
    }

    private fun unbindInternal() {
        val backend = boundBackend ?: return
        val binder = currentBinder.get()
        connectors.getValue(backend).disconnect(binder)
        clearCurrentBinder()
        boundBackend = null
    }

    fun unbind() {
        if (_state.value == ServiceState.Disconnected && boundBackend == null) {
            return
        }
        unbindingIntentionally.set(true)
        unbindInternal()
        handleDisconnect()
        unbindingIntentionally.set(false)
    }

    suspend fun getInstance(timeoutMs: Long = 10_000): RemoteService {
        getInstanceOrNull()?.let { return it }

        bind()
        return withTimeout(timeoutMs) {
            _state.first { it is ServiceState.Connected || it is ServiceState.Error }
                .let { currentState ->
                    when (currentState) {
                        is ServiceState.Connected -> currentState.service
                        is ServiceState.Error -> throw currentState.exception
                        else -> error("Unexpected state: $currentState")
                    }
                }
        }
    }

    fun getInstanceOrNull(): RemoteService? {
        val current = _state.value
        return if (current is ServiceState.Connected) current.service else null
    }


    suspend fun <R> useRemoteService(
        timeoutMs: Long = 12_000,
        action: suspend (RemoteService) -> R
    ): R {
        var accessState = RemoteAccessCoordinator.refresh()
        var backend = accessState.configuredBackend
        if (!accessState.isGranted(backend)) {
            val granted = RemoteAccessCoordinator.request(backend)
            accessState = RemoteAccessCoordinator.refresh()
            backend = accessState.configuredBackend
            if (!granted || !accessState.isGranted(backend)) {
                throw IllegalStateException("${backend.display} permission not granted")
            }
        }

        if (boundBackend != null && boundBackend != backend) {
            Timber.i("Rebinding remote service from %s to %s", boundBackend, backend)
            unbind()
        }

        val service = getInstance(timeoutMs)
        return action(service)
    }
}
