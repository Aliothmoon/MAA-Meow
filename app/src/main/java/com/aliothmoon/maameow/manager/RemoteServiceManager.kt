package com.aliothmoon.maameow.manager

import android.content.Context
import android.os.IBinder
import android.os.Process
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.service.AchievementReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

object RemoteServiceManager {

    sealed class ServiceState {
        data object Disconnected : ServiceState()
        data object Connecting : ServiceState()
        data object Died : ServiceState()
        data class Connected(val service: RemoteService) : ServiceState()
        data class Error(val exception: Throwable) : ServiceState()
    }

    private const val CONNECT_WATCHDOG_MS = 20_000L

    private val currentBinder = AtomicReference<IBinder>()
    private val unbindingIntentionally = AtomicBoolean(false)
    private val _state = MutableStateFlow<ServiceState>(ServiceState.Disconnected)
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())

    // 仅用于诊断日志看门狗：不参与状态机，只在长时间停留 Connecting 时补一条 STUCK。
    private val watchdogScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val connectAttempt = AtomicInteger(0)

    private val connectors: Map<RemoteBackend, RemoteServiceConnectorBackend> = mapOf(
        RemoteBackend.SHIZUKU to ShizukuRemoteServiceConnector,
        RemoteBackend.ROOT to RootRemoteServiceConnector
    )

    private var boundBackend: RemoteBackend? = null
    private var achievementReporter: AchievementReporter? = null

    val state: StateFlow<ServiceState> = _state.asStateFlow()

    private val connectorCallbacks = object : RemoteServiceConnectorBackend.Callbacks {
        override fun onConnected(backend: RemoteBackend, binder: IBinder) {
            if (boundBackend != backend) {
                ServiceBootLogger.event("CB_ON_CONNECTED_STALE", "backend=$backend bound=$boundBackend")
                Timber.w("Ignoring stale %s connection", backend)
                return
            }
            ServiceBootLogger.event("CB_ON_CONNECTED", "backend=$backend")
            onBinderConnected(backend, binder)
        }

        override fun onDisconnected(backend: RemoteBackend) {
            if (boundBackend != backend) {
                return
            }
            if (unbindingIntentionally.get()) {
                return
            }
            ServiceBootLogger.event("CB_ON_DISCONNECTED", "backend=$backend")
            Timber.i("RemoteService disconnected: %s", backend)
            handleDisconnect()
        }

        override fun onError(backend: RemoteBackend, throwable: Throwable) {
            if (boundBackend != backend) {
                return
            }
            ServiceBootLogger.event("CB_ON_ERROR", "backend=$backend ${throwable.javaClass.simpleName}: ${throwable.message}")
            Timber.e(throwable, "RemoteService connection failed: %s", backend)
            achievementReporter?.reportRemoteConnectFailed(backend)
            clearCurrentBinder()
            boundBackend = null
            _state.value = ServiceState.Error(throwable)
        }
    }

    private val deathRecipient = IBinder.DeathRecipient {
        ServiceBootLogger.event("BINDER_DIED", "intentional=${unbindingIntentionally.get()}")
        Timber.w("RemoteService binder died")
        if (unbindingIntentionally.compareAndSet(true, false)) {
            handleDisconnect()
        } else {
            handleBinderDeath()
        }
    }

    fun initialize(
        context: Context,
        appSettings: AppSettingsManager,
        achievementReporter: AchievementReporter,
    ) {
        ServiceBootLogger.init(context)
        RemoteAccessCoordinator.initialize(appSettings)
        this.achievementReporter = achievementReporter
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
        ServiceBootLogger.event("BINDER_CONNECTED", "backend=$backend linkToDeath ok, heartbeat sent")
        achievementReporter?.reportRemoteConnected(backend)
        service.heartbeat(Process.myPid())
    }

    private fun handleBinderDeath() {
        clearCurrentBinder()
        boundBackend = null
        ServiceBootLogger.event("STATE_DIED")
        _state.value = ServiceState.Died
    }

    private fun handleDisconnect() {
        if (_state.value == ServiceState.Died) {
            return
        }
        clearCurrentBinder()
        boundBackend = null
        ServiceBootLogger.event("STATE_DISCONNECTED")
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
            ServiceBootLogger.event("BIND_DENIED", "backend=$backend not granted")
            Timber.w(exception)
            boundBackend = null
            _state.value = ServiceState.Error(exception)
            return
        }

        if (_state.value is ServiceState.Connecting && boundBackend == backend) {
            ServiceBootLogger.event("BIND_SKIP", "already connecting backend=$backend")
            return
        }

        if (boundBackend != null) {
            Timber.i("Unbinding old service before binding new one")
            unbindInternal()
            handleDisconnect()
        }

        boundBackend = backend
        val attempt = connectAttempt.incrementAndGet()
        ServiceBootLogger.event("BIND", "backend=$backend attempt=$attempt")
        _state.value = ServiceState.Connecting
        ServiceBootLogger.event("CONNECTING", "backend=$backend attempt=$attempt")
        connectors.getValue(backend).connect(connectorCallbacks)
        startConnectWatchdog(attempt, backend)
    }

    /**
     * 仅记日志的看门狗：等待 [CONNECT_WATCHDOG_MS] 后若仍是同一次连接尝试且仍停在 Connecting，
     * 补一条 STUCK，明示"服务进程长时间未回投 binder"。不触碰状态机、不做任何重连/解绑。
     */
    private fun startConnectWatchdog(attempt: Int, backend: RemoteBackend) {
        watchdogScope.launch {
            delay(CONNECT_WATCHDOG_MS)
            if (connectAttempt.get() == attempt && _state.value is ServiceState.Connecting) {
                ServiceBootLogger.event(
                    "STUCK",
                    "still CONNECTING after ${CONNECT_WATCHDOG_MS}ms (backend=$backend attempt=$attempt) — 服务进程疑似启动失败/未回投 binder，见 service_boot_debug.log"
                )
            }
        }
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
        return try {
            withTimeout(timeoutMs) {
                _state.first { it is ServiceState.Connected || it is ServiceState.Error }
                    .let { currentState ->
                        when (currentState) {
                            is ServiceState.Connected -> currentState.service
                            is ServiceState.Error -> throw currentState.exception
                            else -> error("Unexpected state: $currentState")
                        }
                    }
            }
        } catch (e: TimeoutCancellationException) {
            ServiceBootLogger.event("GET_INSTANCE_TIMEOUT", "after ${timeoutMs}ms state=${_state.value}")
            throw e
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
