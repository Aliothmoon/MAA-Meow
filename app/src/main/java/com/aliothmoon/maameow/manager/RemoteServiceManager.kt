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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.concurrent.TimeoutException
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

    private const val CONNECT_TIMEOUT_MS = 20_000L

    // 状态迁移（boundBackend / currentBinder / _state）统一在此锁内完成
    private val lock = Any()

    private val currentBinder = AtomicReference<IBinder>()
    private var currentDeathRecipient: BindingDeathRecipient? = null // guarded by lock
    private val _state = MutableStateFlow<ServiceState>(ServiceState.Disconnected)

    private val timeoutScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val connectAttempt = AtomicInteger(0)

    private val connectors: Map<RemoteBackend, RemoteServiceConnectorBackend> = mapOf(
        RemoteBackend.SHIZUKU to ShizukuRemoteServiceConnector,
        RemoteBackend.ROOT to RootRemoteServiceConnector
    )

    @Volatile
    private var boundBackend: RemoteBackend? = null
    val state: StateFlow<ServiceState> = _state.asStateFlow()

    // 携带绑定时的 binder，迟到的死亡通知靠身份比对丢弃
    private class BindingDeathRecipient(val binder: IBinder) : IBinder.DeathRecipient {
        override fun binderDied() = onBinderDied(this)
    }

    private val connectorCallbacks = object : RemoteServiceConnectorBackend.Callbacks {
        override fun onConnected(backend: RemoteBackend, binder: IBinder) {
            val service: RemoteService
            synchronized(lock) {
                if (boundBackend != backend) {
                    ServiceBootLogger.event("CB_ON_CONNECTED_STALE", "backend=$backend bound=$boundBackend")
                    Timber.w("Ignoring stale %s connection", backend)
                    return
                }
                ServiceBootLogger.event("CB_ON_CONNECTED", "backend=$backend")
                clearCurrentBinderLocked()
                val recipient = BindingDeathRecipient(binder)
                try {
                    binder.linkToDeath(recipient, 0)
                } catch (e: Exception) {
                    // binder 送达时已死亡
                    ServiceBootLogger.event("CB_ON_CONNECTED_DEAD", "backend=$backend ${e.message}")
                    Timber.e(e, "RemoteService binder dead on arrival: %s", backend)
                    boundBackend = null
                    _state.value = ServiceState.Error(e)
                    return
                }
                currentBinder.set(binder)
                currentDeathRecipient = recipient
                service = RemoteService.Stub.asInterface(binder)
                _state.value = ServiceState.Connected(service)
                ServiceBootLogger.event("BINDER_CONNECTED", "backend=$backend linkToDeath ok")
            }
            runCatching { service.heartbeat(Process.myPid()) }
                .onFailure { Timber.w(it, "heartbeat failed") }
        }

        override fun onDisconnected(backend: RemoteBackend) {
            synchronized(lock) {
                if (boundBackend != backend) {
                    return
                }
                ServiceBootLogger.event("CB_ON_DISCONNECTED", "backend=$backend")
                Timber.i("RemoteService disconnected: %s", backend)
                // 被动断开由服务进程死亡触发，Connected 态下收敛为 Died 保证终态确定
                val wasConnected = _state.value is ServiceState.Connected
                clearCurrentBinderLocked()
                boundBackend = null
                if (wasConnected) {
                    ServiceBootLogger.event("STATE_DIED")
                    _state.value = ServiceState.Died
                } else {
                    ServiceBootLogger.event("STATE_DISCONNECTED")
                    _state.value = ServiceState.Disconnected
                }
            }
        }

        override fun onError(backend: RemoteBackend, throwable: Throwable) {
            synchronized(lock) {
                if (boundBackend != backend) {
                    return
                }
                ServiceBootLogger.event("CB_ON_ERROR", "backend=$backend ${throwable.javaClass.simpleName}: ${throwable.message}")
                Timber.e(throwable, "RemoteService connection failed: %s", backend)
                clearCurrentBinderLocked()
                boundBackend = null
                _state.value = ServiceState.Error(throwable)
            }
        }
    }

    fun initialize(
        context: Context,
        appSettings: AppSettingsManager,
    ) {
        ServiceBootLogger.init(context)
        ShizukuManager.initSui(context.packageName)
        RemoteAccessCoordinator.initialize(appSettings)
        RootRemoteServiceConnector.initialize(context)
        LogcatServiceManager.initialize(context)
    }

    private fun onBinderDied(recipient: BindingDeathRecipient) {
        synchronized(lock) {
            if (currentBinder.get() !== recipient.binder) {
                ServiceBootLogger.event("BINDER_DIED_STALE")
                return
            }
            ServiceBootLogger.event("BINDER_DIED")
            Timber.w("RemoteService binder died")
            clearCurrentBinderLocked()
            boundBackend = null
            ServiceBootLogger.event("STATE_DIED")
            _state.value = ServiceState.Died
        }
    }

    private fun clearCurrentBinderLocked() {
        val binder = currentBinder.getAndSet(null)
        val recipient = currentDeathRecipient
        currentDeathRecipient = null
        if (binder != null && recipient != null) {
            runCatching {
                binder.unlinkToDeath(recipient, 0)
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
            synchronized(lock) {
                boundBackend = null
                _state.value = ServiceState.Error(exception)
            }
            return
        }

        val attempt: Int
        synchronized(lock) {
            if (_state.value is ServiceState.Connecting && boundBackend == backend) {
                ServiceBootLogger.event("BIND_SKIP", "already connecting backend=$backend")
                return
            }

            if (boundBackend != null) {
                Timber.i("Unbinding old service before binding new one")
                unbindLocked()
            }

            boundBackend = backend
            attempt = connectAttempt.incrementAndGet()
            ServiceBootLogger.event("BIND", "backend=$backend attempt=$attempt")
            _state.value = ServiceState.Connecting
            ServiceBootLogger.event("CONNECTING", "backend=$backend attempt=$attempt")
            connectors.getValue(backend).connect(connectorCallbacks)
        }
        startConnectTimeout(attempt, backend)
    }

    /** 连接超时兜底，主要覆盖无超时机制的 Shizuku 路径（Root 连接器自带 15s 超时先行） */
    private fun startConnectTimeout(attempt: Int, backend: RemoteBackend) {
        timeoutScope.launch {
            delay(CONNECT_TIMEOUT_MS)
            synchronized(lock) {
                if (connectAttempt.get() != attempt ||
                    _state.value !is ServiceState.Connecting ||
                    boundBackend != backend
                ) {
                    return@launch
                }
                ServiceBootLogger.event(
                    "CONNECT_TIMEOUT",
                    "still CONNECTING after ${CONNECT_TIMEOUT_MS}ms (backend=$backend attempt=$attempt) — 服务进程疑似启动失败/未回投 binder，见 service_boot_debug.log"
                )
                runCatching {
                    connectors.getValue(backend).disconnect(currentBinder.get())
                }.onFailure {
                    Timber.w(it, "disconnect after connect timeout failed")
                }
                clearCurrentBinderLocked()
                boundBackend = null
                _state.value = ServiceState.Error(
                    TimeoutException("connect timeout after ${CONNECT_TIMEOUT_MS}ms (backend=$backend)")
                )
            }
        }
    }

    private fun unbindLocked() {
        val backend = boundBackend ?: return
        connectors.getValue(backend).disconnect(currentBinder.get())
        clearCurrentBinderLocked()
        boundBackend = null
    }

    fun unbind() {
        synchronized(lock) {
            if (_state.value == ServiceState.Disconnected && boundBackend == null) {
                return
            }
            unbindLocked()
            // 主动解绑不覆盖待消费的 Died 信号
            if (_state.value != ServiceState.Died) {
                ServiceBootLogger.event("STATE_DISCONNECTED")
                _state.value = ServiceState.Disconnected
            }
        }
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
