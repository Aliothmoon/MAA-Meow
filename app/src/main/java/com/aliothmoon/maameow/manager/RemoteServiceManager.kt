package com.aliothmoon.maameow.manager

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.manager.ShizukuManager.requireShizukuPermissionGranted
import com.aliothmoon.maameow.remote.RemoteServiceImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import timber.log.Timber
import java.util.UUID
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


    private val serviceTag = UUID.randomUUID().toString()
    private val serviceVersion = AtomicInteger(100)

    /**
     * 每次调用时生成新的 serviceArgs，确保参数始终是最新的
     */
    private fun createServiceArgs(): Shizuku.UserServiceArgs {
        return Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, RemoteServiceImpl::class.java.name)
        ).apply {
            processNameSuffix("service")
            daemon(false)
            tag(serviceTag)
            version(serviceVersion.incrementAndGet())
            debuggable(BuildConfig.DEBUG)
        }
    }

    private var currentServiceArgs: Shizuku.UserServiceArgs? = null

    private val _state = MutableStateFlow<ServiceState>(ServiceState.Disconnected)
    val state: StateFlow<ServiceState> = _state.asStateFlow()

    private val currentBinder: AtomicReference<IBinder> = AtomicReference()

    private val unbindingIntentionally = AtomicBoolean(false)

    private val deathRecipient = IBinder.DeathRecipient {
        Timber.w("RemoteService binder died")
        // 检查是否是主动断开
        if (unbindingIntentionally.compareAndSet(true, false)) {
            handleDisconnect()
        } else {
            handleBinderDeath()
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Timber.i("RemoteService connected: %s", name)
            currentBinder.set(binder)
            binder?.linkToDeath(deathRecipient, 0)
            _state.value = ServiceState.Connected(
                RemoteService.Stub.asInterface(binder)
            )
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.i("RemoteService disconnected: %s", name)
            handleDisconnect()
        }
    }

    /** 服务进程异常死亡 */
    private fun handleBinderDeath() {
        currentBinder.get()?.unlinkToDeath(deathRecipient, 0)
        currentBinder.set(null)
        _state.value = ServiceState.Died
    }

    /** 正常断开连接 */
    private fun handleDisconnect() {
        if (_state.value == ServiceState.Died) {
            return
        }
        currentBinder.get()?.unlinkToDeath(deathRecipient, 0)
        currentBinder.set(null)
        _state.value = ServiceState.Disconnected
    }

    fun bind() {
        if (_state.value is ServiceState.Connecting) {
            return
        }

        // 如果已连接，先解绑旧服务
        if (isConnected()) {
            Timber.i("Unbinding old service before binding new one")
            unbindInternal()
        }

        _state.value = ServiceState.Connecting
        try {
            // 生成新的 serviceArgs
            val newArgs = createServiceArgs()
            currentServiceArgs = newArgs
            Timber.i("Binding service with new args: tag=${newArgs}")
            Shizuku.bindUserService(newArgs, connection)
        } catch (e: Exception) {
            Timber.e(e, "bindUserService failed")
            _state.value = ServiceState.Error(e)
        }
    }

    private fun unbindInternal() {
        val args = currentServiceArgs ?: return
        runCatching {
            Shizuku.unbindUserService(args, connection, true)
        }.onFailure {
            Timber.w(it, "unbindUserService failed")
        }
        currentBinder.get()?.unlinkToDeath(deathRecipient, 0)
        currentBinder.set(null)
        currentServiceArgs = null
    }

    fun unbind() {
        if (_state.value == ServiceState.Disconnected) {
            return
        }
        // 标记为主动断开，避免 deathRecipient 误判为异常死亡
        unbindingIntentionally.set(true)
        unbindInternal()
        handleDisconnect()
    }

    suspend fun getInstance(timeoutMs: Long = 10_000): RemoteService {
        getInstanceOrNull()?.let { return it }

        bind()
        return withTimeout(timeoutMs) {
            _state.first { it is ServiceState.Connected || it is ServiceState.Error }
                .let { state ->
                    when (state) {
                        is ServiceState.Connected -> state.service
                        is ServiceState.Error -> throw state.exception
                        else -> error("Unexpected state: $state")
                    }
                }
        }
    }

    fun getInstanceOrNull(): RemoteService? {
        val current = _state.value
        if (current is ServiceState.Connected) {
            return current.service
        }
        return null
    }

    fun isConnected(): Boolean = getInstanceOrNull() != null

    suspend inline fun <R> useRemoteService(
        timeoutMs: Long = 5_000,
        crossinline action: suspend (RemoteService) -> R
    ): R {
        return requireShizukuPermissionGranted {
            val service = getInstance(timeoutMs)
            action(service)
        }
    }
}
