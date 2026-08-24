package com.aliothmoon.maameow.manager

import android.os.IBinder
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.remote.RemoteServiceImpl
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

object ShizukuRemoteServiceConnector : RemoteServiceConnectorBackend {

    override val backend: RemoteBackend = RemoteBackend.SHIZUKU

    // tag 进程级唯一，重试靠 version 递增让 server 清旧 record
    private val serviceTag = UUID.randomUUID().toString()
    private val serviceVersion = AtomicInteger(100)

    @Volatile
    private var activeVersion = UNBOUND

    private const val UNBOUND = -1

    override fun connect(callbacks: RemoteServiceConnectorBackend.Callbacks) {
        val version = serviceVersion.incrementAndGet()
        activeVersion = version
        ServiceBootLogger.event("SHIZUKU_BIND_CALL", "version=$version tag=$serviceTag")

        val boundTag = ShizukuUserServiceBinder.bind(
            serviceClass = RemoteServiceImpl::class.java,
            processNameSuffix = "service",
            tag = serviceTag,
            version = version,
            timeoutMs = ShizukuUserServiceBinder.DEFAULT_BIND_TIMEOUT_MS,
            onConnected = { binder ->
                // connected 可能先于 bind 返回到达，靠 version 认领
                if (activeVersion != version) {
                    Timber.w("Ignoring stale Shizuku connection v=%d", version)
                    return@bind
                }
                callbacks.onConnected(backend, binder)
            },
            onDisconnected = {
                if (activeVersion != version) return@bind
                callbacks.onDisconnected(backend)
            },
            onError = { throwable ->
                if (activeVersion != version) return@bind
                activeVersion = UNBOUND
                callbacks.onError(backend, throwable)
            },
        )
        if (boundTag == null) {
            activeVersion = UNBOUND
        }
    }

    override fun disconnect(currentBinder: IBinder?) {
        val version = activeVersion
        activeVersion = UNBOUND
        if (version == UNBOUND) return
        ShizukuUserServiceBinder.unbind(serviceTag, remove = true)
    }
}
