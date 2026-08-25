package com.aliothmoon.maameow.manager

import android.content.Context
import android.os.IBinder
import com.aliothmoon.maameow.ILogcatService
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.remote.LogcatCaptureServiceImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/** logcat 服务与主服务同走进程路径，不依赖 Shizuku manager App 中转 */
object LogcatServiceManager {

    // startCapture 等待须晚于连接器超时，否则拿不到带根因的失败日志
    private const val CONNECT_WAIT_GRACE_MS = 2_000L

    private val _service = MutableStateFlow<ILogcatService?>(null)

    private val connectors: Map<RemoteBackend, LogcatConnector> = mapOf(
        RemoteBackend.SHIZUKU to LogcatConnector(RemoteBackend.SHIZUKU, ShizukuSpawner),
        RemoteBackend.ROOT to LogcatConnector(RemoteBackend.ROOT, SuSpawner),
    )

    @Volatile
    private var boundBackend: RemoteBackend? = null

    private val callbacks = object : RemoteServiceConnectorBackend.Callbacks {
        override fun onConnected(backend: RemoteBackend, binder: IBinder) {
            if (boundBackend != backend) return
            Timber.i("LogcatService connected via %s", backend)
            _service.value = ILogcatService.Stub.asInterface(binder)
        }

        override fun onDisconnected(backend: RemoteBackend) {
            if (boundBackend != backend) return
            Timber.w("LogcatService process died, clearing binder")
            _service.value = null
        }

        override fun onError(backend: RemoteBackend, throwable: Throwable) {
            if (boundBackend != backend) return
            // 失败只记日志，_service 保持 null 由 startCapture 超时兜底
            boundBackend = null
            Timber.e(throwable, "bind LogcatService via %s failed", backend)
            ServiceBootLogger.event(
                "LOGCAT_BIND_ERROR",
                "backend=$backend ${throwable.javaClass.simpleName}: ${throwable.message}"
            )
        }
    }

    fun initialize(context: Context) {
        connectors.values.forEach { it.initialize(context) }
    }

    fun bind() {
        val existing = _service.value
        if (existing != null) {
            // 死 binder 不能当已绑定，否则永远早退
            if (existing.asBinder()?.isBinderAlive == true) return
            Timber.i("LogcatService binder is dead, rebinding")
            _service.value = null
        }
        val backend = RemoteAccessCoordinator.configuredBackend()
        val connector = connectors.getValue(backend)
        // 拉起是异步的，进行中别重复起进程
        if (boundBackend == backend && connector.isConnecting) return
        if (boundBackend != null && boundBackend != backend) unbind()

        boundBackend = backend
        ServiceBootLogger.event("LOGCAT_BIND_CALL", "backend=$backend")
        connector.connect(callbacks)
    }

    fun unbind() {
        val backend = boundBackend ?: return
        boundBackend = null
        connectors.getValue(backend).disconnect(_service.value?.asBinder())
        _service.value = null
    }

    suspend fun startCapture(appPid: Int, servicePid: Int, userDir: String) {
        val backend = boundBackend ?: RemoteAccessCoordinator.configuredBackend()
        val waitMs = connectors.getValue(backend).worstCaseConnectMs + CONNECT_WAIT_GRACE_MS
        withTimeout(waitMs) {
            _service.first { it != null }
        }?.startCapture(appPid, servicePid, userDir)
    }

    private class LogcatConnector(
        override val backend: RemoteBackend,
        spawner: ProcessSpawner,
    ) : ProcessServiceConnectorBackend(spawner) {
        override val eventPrefix = "${backend.name}_LOGCAT"
        override val processNameSuffix = "${backend.name.lowercase()}_logcat"
        override val serviceClass: Class<*> = LogcatCaptureServiceImpl::class.java
        override val logFileName = "${processNameSuffix}_launch_debug.log"

        // logcat 无需输入注入，shell 身份自带 log 组权限
        override val keepRoot: Boolean get() = false

        override fun destroyRemote(binder: IBinder) {
            ILogcatService.Stub.asInterface(binder)?.destroy()
        }
    }
}
