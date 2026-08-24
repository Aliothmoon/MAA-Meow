package com.aliothmoon.maameow.manager

import android.content.ComponentName
import android.os.Bundle
import android.os.IBinder
import com.aliothmoon.maameow.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import moe.shizuku.server.IShizukuServiceConnection
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** 直连 Shizuku server 的绑定器：binder 线程回调、35s 看门狗、超时归因 */
object ShizukuUserServiceBinder {

    /** server 端 UserServiceRecord 的启动超时，看门狗必须晚于它让 server 先自行清理 */
    const val SERVER_START_TIMEOUT_MS = 30_000L
    const val DEFAULT_BIND_TIMEOUT_MS = 35_000L

    private const val PEEK_RESCUE_WAIT_MS = 800L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private class Binding(
        val component: ComponentName,
        val conn: IShizukuServiceConnection,
        val settled: AtomicBoolean,
        val watchdog: Job,
    ) {
        // 持强引用防 GC 断掉死亡通知，解绑时尝试 unlink
        @Volatile var serviceBinder: IBinder? = null
        @Volatile var deathRecipient: IBinder.DeathRecipient? = null
    }

    // tag -> 当前绑定；清理一律按实例归属（remove(key, value)），迟到的旧回调不得误删新绑定
    private val bindings = ConcurrentHashMap<String, Binding>()

    /** 发起绑定，回调均在 binder 线程执行；立即失败时已回调 [onError] 并返回 null */
    fun bind(
        serviceClass: Class<*>,
        processNameSuffix: String,
        tag: String,
        version: Int,
        timeoutMs: Long = DEFAULT_BIND_TIMEOUT_MS,
        onConnected: (IBinder) -> Unit,
        onDisconnected: () -> Unit,
        onError: (Throwable) -> Unit,
    ): String? {
        val serverBinder = Shizuku.getBinder()
        if (serverBinder == null || !serverBinder.pingBinder()) {
            onError(IllegalStateException("Shizuku binder unavailable"))
            return null
        }
        val server = IShizukuService.Stub.asInterface(serverBinder)

        // 同 tag 未完成的旧绑定由本次取代，防止幽灵看门狗误杀新 record
        bindings.remove(tag)?.let { stale ->
            stale.watchdog.cancel()
            stale.settled.set(true)
        }

        val component = ComponentName(BuildConfig.APPLICATION_ID, serviceClass.name)
        val settled = AtomicBoolean(false)
        // conn/watchdog 先于 Binding 构造，靠此引用在回调里认领自己的 Binding
        val self = AtomicReference<Binding?>()

        val conn = object : IShizukuServiceConnection.Stub() {
            override fun connected(binder: IBinder?) {
                if (!settled.compareAndSet(false, true)) return
                if (binder == null) {
                    fail(server, tag, self, onError,
                        IllegalStateException("user service attached a null binder"))
                    return
                }
                Timber.i("User service connected: tag=%s", tag)
                ServiceBootLogger.event("USER_SERVICE_CONNECTED", "tag=$tag")
                // server 不广播服务进程死亡，须自持 linkToDeath（SDK 封装同款做法）
                val recipient = IBinder.DeathRecipient { died() }
                val linked = runCatching { binder.linkToDeath(recipient, 0) }.isSuccess
                self.get()?.let {
                    it.serviceBinder = binder
                    it.deathRecipient = recipient
                }
                if (!linked) {
                    // binder 到达即死，按断开收敛
                    died()
                    return
                }
                onConnected(binder)
            }

            override fun died() {
                if (settled.compareAndSet(false, true)) {
                    // attach 之前进程就死了
                    fail(server, tag, self, onError,
                        IllegalStateException("user service died before attaching binder"))
                } else {
                    Timber.i("User service died: tag=%s", tag)
                    ServiceBootLogger.event("USER_SERVICE_DIED", "tag=$tag")
                    // 仅当前有效绑定的死讯才上抛，被取代/已解绑的旧死讯静默
                    if (claimBinding(tag, self.get()) != null) onDisconnected()
                }
            }
        }

        val watchdog = scope.launch {
            delay(timeoutMs)
            if (settled.get()) return@launch
            // 末次重试送达：仅覆盖 oneway 回调丢失，record 多半已被 server 清理
            runCatching {
                server.addUserService(conn, optionsBundle(component, tag, version, processNameSuffix, noCreate = true))
            }.onFailure { Timber.w(it, "peek user service failed: tag=%s", tag) }
            delay(PEEK_RESCUE_WAIT_MS)
            if (settled.get()) return@launch
            if (!settled.compareAndSet(false, true)) return@launch
            val hint = withContext(Dispatchers.IO) {
                ShizukuFailureDiagnostics.collectUserServiceTimeoutHint()
            }
            ServiceBootLogger.event(
                "USER_SERVICE_TIMEOUT",
                "tag=$tag after=${timeoutMs}ms hint=${hint ?: "none"}"
            )
            val binding = claimBinding(tag, self.get()) ?: return@launch
            runCatching { server.removeUserService(null, removeBundle(binding.component, tag, remove = true)) }
                .onFailure { Timber.w(it, "removeUserService after timeout failed") }
            onError(TimeoutException(
                "user service binder not attached within ${timeoutMs}ms " +
                    "(server gives up at ${SERVER_START_TIMEOUT_MS}ms)" +
                    if (hint != null) ": $hint" else ""
            ))
        }

        val binding = Binding(component, conn, settled, watchdog)
        self.set(binding)
        bindings[tag] = binding

        return try {
            server.addUserService(conn, optionsBundle(component, tag, version, processNameSuffix))
            tag
        } catch (e: Exception) {
            if (claimBinding(tag, binding) != null) {
                binding.watchdog.cancel()
                Timber.e(e, "addUserService failed: tag=%s", tag)
                ServiceBootLogger.event("USER_SERVICE_ADD_FAIL", "${e.javaClass.simpleName}: ${e.message}")
                onError(e)
            }
            null
        }
    }

    /** tag 是否存在未完成的绑定，调用方可据此短路重复拉起 */
    fun isBinding(tag: String): Boolean =
        bindings[tag]?.let { !it.settled.get() } == true

    /** 按 tag 解绑；remove=true 时 server 侧杀进程清 record */
    fun unbind(tag: String, remove: Boolean = true) {
        val binding = bindings.remove(tag) ?: return
        binding.watchdog.cancel()
        binding.settled.set(true)
        val binder = binding.serviceBinder
        val recipient = binding.deathRecipient
        if (binder != null && recipient != null) {
            runCatching { binder.unlinkToDeath(recipient, 0) }
        }
        val serverBinder = Shizuku.getBinder() ?: return
        val server = IShizukuService.Stub.asInterface(serverBinder)
        runCatching {
            server.removeUserService(
                if (remove) null else binding.conn,
                removeBundle(binding.component, tag, remove)
            )
        }.onFailure { Timber.w(it, "removeUserService failed: tag=%s", tag) }
    }

    /** 认领归属：仅当 map 中仍是本绑定时移除并返回，被取代/已解绑时返回 null */
    private fun claimBinding(tag: String, binding: Binding?): Binding? =
        binding?.takeIf { bindings.remove(tag, it) }

    /** 仅当 map 中仍是本绑定时才清 server record 并上报，被取代的旧绑定静默退场 */
    private fun fail(
        server: IShizukuService,
        tag: String,
        self: AtomicReference<Binding?>,
        onError: (Throwable) -> Unit,
        error: Exception,
    ) {
        ServiceBootLogger.event("USER_SERVICE_FAIL", "${error.javaClass.simpleName}: ${error.message}")
        val binding = claimBinding(tag, self.get()) ?: return
        runCatching { server.removeUserService(null, removeBundle(binding.component, tag, remove = true)) }
            .onFailure { Timber.w(it, "removeUserService after failure failed") }
        onError(error)
    }

    private fun optionsBundle(
        component: ComponentName,
        tag: String,
        version: Int,
        processNameSuffix: String,
        noCreate: Boolean = false,
    ): Bundle {
        return Bundle().apply {
            putParcelable(ShizukuApiConstants.USER_SERVICE_ARG_COMPONENT, component)
            putInt(ShizukuApiConstants.USER_SERVICE_ARG_VERSION_CODE, version)
            putString(ShizukuApiConstants.USER_SERVICE_ARG_TAG, tag)
            putString(ShizukuApiConstants.USER_SERVICE_ARG_PROCESS_NAME, processNameSuffix)
            putBoolean(ShizukuApiConstants.USER_SERVICE_ARG_DAEMON, false)
            putBoolean(ShizukuApiConstants.USER_SERVICE_ARG_DEBUGGABLE, BuildConfig.DEBUG)
            if (noCreate) putBoolean(ShizukuApiConstants.USER_SERVICE_ARG_NO_CREATE, true)
        }
    }

    private fun removeBundle(component: ComponentName, tag: String, remove: Boolean): Bundle {
        return Bundle().apply {
            putParcelable(ShizukuApiConstants.USER_SERVICE_ARG_COMPONENT, component)
            putString(ShizukuApiConstants.USER_SERVICE_ARG_TAG, tag)
            putBoolean(ShizukuApiConstants.USER_SERVICE_ARG_REMOVE, remove)
        }
    }
}
