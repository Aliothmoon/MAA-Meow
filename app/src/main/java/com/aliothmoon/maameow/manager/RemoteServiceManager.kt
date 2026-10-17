package com.aliothmoon.maameow.manager

import android.content.Context
import android.os.IBinder
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RemoteBackend
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

object RemoteServiceManager {

    sealed class ServiceState {
        data object Disconnected : ServiceState()
        data object Connecting : ServiceState()
        data object Died : ServiceState()
        data class Connected(val service: RemoteService) : ServiceState()
        data class Error(val exception: Throwable) : ServiceState()
    }

    // 纯兜底：连接器自带超时先行归因，此处按其最坏时长再放余量
    private const val CONNECT_TIMEOUT_GRACE_MS = 2_000L

    // 调用方默认等待须晚于兜底：先于连接器超时就只剩裸超时，launcher 日志尾部等根因全被截胡
    private const val CALLER_WAIT_MARGIN_MS = 1_000L

    private const val SHORT_CAUSE_MAX = 120

    // 状态迁移（boundBackend / currentBinder / _state）统一在此锁内完成
    private val lock = Any()

    private val currentBinder = AtomicReference<IBinder>()
    private var currentDeathRecipient: BindingDeathRecipient? = null // guarded by lock
    private val _state = MutableStateFlow<ServiceState>(ServiceState.Disconnected)

    private val timeoutScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val connectAttempt = AtomicInteger(0)

    private val connectors: Map<RemoteBackend, RemoteServiceConnectorBackend> = mapOf(
        RemoteBackend.SHIZUKU to ShizukuProcessServiceConnector,
        RemoteBackend.ROOT to RootRemoteServiceConnector,
    )

    @Volatile
    private var boundBackend: RemoteBackend? = null

    // 起进程时的身份（uid），guarded by lock
    private var boundIdentity: Int? = null
    val state: StateFlow<ServiceState> = _state.asStateFlow()

    /** 最近连上的提权进程 pid，死后不清 */
    @Volatile
    var lastServicePid: Int? = null
        private set

    // 携带绑定时的 binder，迟到的死亡通知靠身份比对丢弃
    private class BindingDeathRecipient(val binder: IBinder) : IBinder.DeathRecipient {
        override fun binderDied() = onBinderDied(this)
    }

    private val connectorCallbacks = object : RemoteServiceConnectorBackend.Callbacks {
        override fun onConnected(backend: RemoteBackend, binder: IBinder) {
            val service: RemoteService
            synchronized(lock) {
                if (boundBackend != backend) {
                    ServiceBootLogger.event(
                        "CB_ON_CONNECTED_STALE",
                        "backend=$backend bound=$boundBackend"
                    )
                    Timber.w("Ignoring stale %s connection", backend)
                    // 没人认领的进程不收就成了孤儿
                    runCatching { RemoteService.Stub.asInterface(binder).destroy() }
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
            runCatching { service.pid() }
                .onSuccess {
                    lastServicePid = it
                    ServiceBootLogger.event("SERVICE_PID", "pid=$it")
                }
                .onFailure { Timber.w(it, "query service pid failed") }
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
                ServiceBootLogger.event(
                    "CB_ON_ERROR",
                    "backend=$backend ${throwable.javaClass.simpleName}: ${throwable.message}"
                )
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
        pathConfig: MaaPathConfig,
    ) {
        // App 进程写的落 App 目录，launcher 以 shell 身份写的落 core 目录
        val coreDebugDir = File(pathConfig.coreDebugDir)
        ServiceBootLogger.init(File(pathConfig.debugDir))
        ShizukuManager.initSui(context.packageName)
        RemoteAccessCoordinator.initialize(appSettings)
        RootRemoteServiceConnector.initialize(context, coreDebugDir)
        ShizukuProcessServiceConnector.initialize(context, coreDebugDir)
        LogcatServiceManager.initialize(context, coreDebugDir)
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

        val identity = spawnIdentity(backend)
        val attempt: Int
        synchronized(lock) {
            if (_state.value is ServiceState.Connecting && boundBackend == backend) {
                ServiceBootLogger.event("BIND_SKIP", "already connecting backend=$backend")
                return
            }
            // Shizuku / su 重启不影响已起的进程，身份没变就别杀了重起
            if (_state.value is ServiceState.Connected && boundBackend == backend &&
                currentBinder.get()?.isBinderAlive == true &&
                identity != null && identity == boundIdentity
            ) {
                ServiceBootLogger.event("BIND_SKIP", "already connected backend=$backend uid=$identity")
                Timber.i("Keep connected %s service (uid=%d)", backend, identity)
                return
            }

            if (boundBackend != null) {
                Timber.i("Unbinding old service before binding new one")
                unbindLocked()
            }

            boundBackend = backend
            boundIdentity = identity
            attempt = connectAttempt.incrementAndGet()
            ServiceBootLogger.event("BIND", "backend=$backend attempt=$attempt")
            _state.value = ServiceState.Connecting
            ServiceBootLogger.event("CONNECTING", "backend=$backend attempt=$attempt")
            connectors.getValue(backend).connect(connectorCallbacks)
        }
        startConnectTimeout(attempt, backend)
    }

    /** 现在新起的进程会是什么身份，adb ↔ root 切换后旧进程不能沿用 */
    private fun spawnIdentity(backend: RemoteBackend): Int? = when (backend) {
        RemoteBackend.SHIZUKU -> ShizukuManager.serverUid()
        RemoteBackend.ROOT -> 0
    }

    private fun startConnectTimeout(attempt: Int, backend: RemoteBackend) {
        val timeoutMs = fallbackTimeoutMs(backend)
        timeoutScope.launch {
            delay(timeoutMs.milliseconds)
            synchronized(lock) {
                if (connectAttempt.get() != attempt ||
                    _state.value !is ServiceState.Connecting ||
                    boundBackend != backend
                ) {
                    return@launch
                }
                ServiceBootLogger.event(
                    "CONNECT_TIMEOUT",
                    "still CONNECTING after ${timeoutMs}ms (backend=$backend attempt=$attempt) — 服务进程疑似启动失败/未回投 binder，见 service_boot_debug.log"
                )
                runCatching {
                    connectors.getValue(backend).disconnect(currentBinder.get())
                }.onFailure {
                    Timber.w(it, "disconnect after connect timeout failed")
                }
                clearCurrentBinderLocked()
                boundBackend = null
                _state.value = ServiceState.Error(
                    TimeoutException("connect timeout after ${timeoutMs}ms (backend=$backend)")
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

    private fun fallbackTimeoutMs(backend: RemoteBackend): Long =
        connectors.getValue(backend).worstCaseConnectMs + CONNECT_TIMEOUT_GRACE_MS

    /** 调用方默认等待：覆盖连接器超时与兜底超时，保证拿到的是带根因的 Error 而非裸超时 */
    private fun defaultWaitMs(backend: RemoteBackend): Long =
        fallbackTimeoutMs(backend) + CALLER_WAIT_MARGIN_MS

    /** [timeoutMs] 为 null 时按当前后端推算默认等待 */
    suspend fun getInstance(timeoutMs: Long? = null): RemoteService {
        getInstanceOrNull()?.let { return it }

        bind()
        val waitMs =
            timeoutMs ?: defaultWaitMs(boundBackend ?: RemoteAccessCoordinator.configuredBackend())
        return try {
            withTimeout(waitMs.milliseconds) {
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
            ServiceBootLogger.event(
                "GET_INSTANCE_TIMEOUT",
                "after ${waitMs}ms state=${_state.value}"
            )
            throw e
        }
    }

    fun getInstanceOrNull(): RemoteService? {
        val current = _state.value
        return if (current is ServiceState.Connected) current.service else null
    }

    /** 当前已连接的后端；未连接返回 null */
    fun connectedBackendOrNull(): RemoteBackend? =
        if (_state.value is ServiceState.Connected) boundBackend else null


    /**
     * [timeoutMs] 为 null 时按当前后端推算默认等待
     * 整体切到 IO：[action] 里的同步 Binder 调用不能落在调用方（主）线程
     */
    suspend fun <R> useRemoteService(
        timeoutMs: Long? = null,
        action: suspend (RemoteService) -> R
    ): R = withContext(Dispatchers.IO) {
        // 已连着当前后端就直接用：Shizuku / su 挂了，已起的进程照样能用
        if (boundBackend == RemoteAccessCoordinator.configuredBackend()) {
            getInstanceOrNull()?.let { return@withContext action(it) }
        }

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
        action(service)
    }

    /** 连不上返回原因而不抛；调用方被取消照常抛 */
    suspend fun awaitConnected(): Throwable? = try {
        useRemoteService { }
        null
    } catch (e: TimeoutCancellationException) {
        e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e
    }

    /** 去掉 launcher 日志尾巴，全文已进 Timber 与 bind 日志 */
    fun shortCause(e: Throwable): String =
        e.message?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }?.take(SHORT_CAUSE_MAX)
            ?: e.javaClass.simpleName
}
