package com.aliothmoon.maameow.manager

import android.content.Context
import android.os.IBinder
import android.os.Process
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.ILogcatService
import com.aliothmoon.maameow.constant.MaaFiles
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.remote.LogcatCaptureServiceImpl
import com.aliothmoon.maameow.root.RootServiceBootstrapRegistry
import com.aliothmoon.maameow.root.RootServiceStarter
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object LogcatServiceManager {

    private const val ROOT_BIND_TIMEOUT_MS = 15_000L

    private val _service = MutableStateFlow<ILogcatService?>(null)

    // --- Shizuku ---
    private val serviceTag = UUID.randomUUID().toString()
    private val serviceVersion = AtomicInteger(100)

    // 进行中的绑定版本，迟到的旧回调靠它丢弃
    private val shizukuActiveVersion = AtomicInteger(0)

    // --- Root ---
    private val initialized = AtomicBoolean(false)
    private lateinit var appContext: Context
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())

    @Volatile
    private var rootActiveLaunch: RootActiveLaunch? = null

    fun initialize(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            appContext = context.applicationContext
        }
    }

    fun bind() {
        val existing = _service.value
        if (existing != null) {
            // 死 binder 不能当已绑定，否则永远早退
            if (existing.asBinder()?.isBinderAlive == true) return
            Timber.i("LogcatService binder is dead, rebinding")
            _service.value = null
            rootActiveLaunch = null
        }
        // root 拉起是异步的，进行中别重复起进程
        if (rootActiveLaunch?.job?.isActive == true) return
        when (RemoteAccessCoordinator.configuredBackend()) {
            RemoteBackend.SHIZUKU -> bindViaShizuku()
            RemoteBackend.ROOT -> bindViaRoot()
        }
    }

    fun unbind() {
        // Shizuku
        shizukuActiveVersion.set(0)
        ShizukuUserServiceBinder.unbind(serviceTag, remove = true)

        // Root
        val active = rootActiveLaunch
        if (active != null) {
            rootActiveLaunch = null
            active.job.cancel()
            RootServiceBootstrapRegistry.unregister(active.token)
            _service.value?.let { service ->
                runCatching { service.destroy() }
                    .onFailure { Timber.w(it, "destroy root logcat service failed") }
            }
        }

        _service.value = null
    }

    suspend fun startCapture(appPid: Int, servicePid: Int, userDir: String) {
        withTimeout(10_000) {
            _service.first { it != null }
        }?.startCapture(appPid, servicePid, userDir)
    }

    // --- Shizuku 绑定 ---

    private fun bindViaShizuku() {
        // 进行中别重复拉起，server 侧 version 递增会杀旧进程起新进程
        if (ShizukuUserServiceBinder.isBinding(serviceTag)) return
        val version = serviceVersion.incrementAndGet()
        shizukuActiveVersion.set(version)
        ServiceBootLogger.event("LOGCAT_BIND_CALL", "version=$version tag=$serviceTag")

        val boundTag = ShizukuUserServiceBinder.bind(
            serviceClass = LogcatCaptureServiceImpl::class.java,
            processNameSuffix = "logcat",
            tag = serviceTag,
            version = version,
            onConnected = { binder ->
                if (shizukuActiveVersion.get() != version) return@bind
                Timber.i("LogcatService connected via Shizuku")
                _service.value = ILogcatService.Stub.asInterface(binder)
            },
            onDisconnected = {
                if (shizukuActiveVersion.get() != version) return@bind
                Timber.i("LogcatService disconnected via Shizuku")
                _service.value = null
            },
            onError = { throwable ->
                if (shizukuActiveVersion.get() != version) return@bind
                // 维持旧语义：失败只记日志，_service 保持 null 由 startCapture 超时兜底
                Timber.e(throwable, "bindLogcatService via Shizuku failed")
                ServiceBootLogger.event(
                    "LOGCAT_BIND_ERROR",
                    "${throwable.javaClass.simpleName}: ${throwable.message}"
                )
            },
        )
        if (boundTag == null) {
            shizukuActiveVersion.set(0)
        }
    }

    // --- Root 绑定 ---

    private fun bindViaRoot() {
        check(initialized.get()) { "LogcatServiceManager is not initialized for root mode" }

        val token = UUID.randomUUID().toString()
        val deferred = RootServiceBootstrapRegistry.register(token)

        val job = scope.launch {
            val startResult = withContext(Dispatchers.IO) {
                startRootService(token)
            }

            val active = rootActiveLaunch
            if (active?.token != token) {
                RootServiceBootstrapRegistry.unregister(token)
                return@launch
            }

            val startError = startResult.exceptionOrNull()
            if (startError != null) {
                rootActiveLaunch = null
                RootServiceBootstrapRegistry.unregister(token)
                Timber.e(startError, "Root logcat service start failed")
                dumpLaunchDebugLog()
                return@launch
            }

            runCatching {
                withTimeout(ROOT_BIND_TIMEOUT_MS) {
                    deferred.await()
                }
            }.onSuccess { binder ->
                if (rootActiveLaunch?.token != token) {
                    RootServiceBootstrapRegistry.unregister(token)
                    return@onSuccess
                }
                // 按 token 认领，旧进程死讯不能清掉新连接
                runCatching {
                    binder.linkToDeath({
                        if (rootActiveLaunch?.token != token) {
                            Timber.i("Stale root logcat binder death ignored (token=%s)", token)
                            return@linkToDeath
                        }
                        Timber.w("Root logcat process died, clearing binder")
                        rootActiveLaunch = null
                        _service.value = null
                    }, 0)
                }.onFailure { Timber.w(it, "Failed to link to death for root logcat binder") }
                Timber.i("LogcatService connected via root bootstrap")
                _service.value = ILogcatService.Stub.asInterface(binder)
            }.onFailure { throwable ->
                RootServiceBootstrapRegistry.unregister(token)
                if (rootActiveLaunch?.token == token) {
                    rootActiveLaunch = null
                    Timber.e(throwable, "Root logcat service bind timeout")
                    dumpLaunchDebugLog()
                }
            }
        }

        rootActiveLaunch = RootActiveLaunch(token, job)
    }

    private fun startRootService(token: String): Result<Unit> {
        return runCatching {
            val command = buildRootStartCommand(token)
            val result = Shell.cmd(command).exec()
            if (result.code != 0) {
                error(result.err.joinToString("\n").ifBlank { "exit code=${result.code}" })
            }
        }.onFailure {
            Timber.e(it, "startRootLogcatService failed")
        }
    }

    private fun buildRootStartCommand(token: String): String {
        val processName = "${appContext.packageName}:root_logcat"
        val launcherFile = File(
            appContext.applicationInfo.nativeLibraryDir,
            "liblauncher.so"
        )
        check(launcherFile.exists()) { "root launcher not found: ${launcherFile.absolutePath}" }
        val uid = Process.myUid()
        return buildString {
            append(shellQuote(launcherFile.absolutePath))
            append(" --apk=")
            append(shellQuote(appContext.applicationInfo.sourceDir))
            append(" --process-name=")
            append(shellQuote(processName))
            append(" --starter-class=")
            append(shellQuote(RootServiceStarter::class.java.name))
            append(" --token=")
            append(shellQuote(token))
            append(" --package=")
            append(shellQuote(appContext.packageName))
            append(" --class=")
            append(shellQuote(LogcatCaptureServiceImpl::class.java.name))
            append(" --uid=")
            append(uid)
            // 有意不加 --keep-root：logcat 无需输入注入，shell 身份自带 log 组权限
            append(" --log-file=")
            append(shellQuote(debugLogFile().absolutePath))
            if (BuildConfig.DEBUG) {
                append(" --debug-name=")
                append(shellQuote(processName))
            }
            append(" >/dev/null 2>&1 &")
        }
    }

    // 与主服务日志分开：launcher 以 O_TRUNC 打开，共用会互相覆盖
    private fun debugLogFile(): File {
        val dir = File(appContext.getExternalFilesDir(null), "${MaaFiles.MAA}/${MaaFiles.DEBUG}")
        dir.mkdirs()
        return File(dir, "root_logcat_launch_debug.log")
    }

    private fun dumpLaunchDebugLog() {
        val log = debugLogFile()
        if (!log.exists()) {
            Timber.e("Root logcat launch debug log not found: %s", log.absolutePath)
            return
        }
        val content = runCatching { log.readText().trim() }.getOrNull()
        if (content.isNullOrBlank()) {
            Timber.e("Root logcat launch debug log is empty (launcher may have crashed before opening it)")
        } else {
            Timber.e("Root logcat launch debug log (%s):\n%s", log.absolutePath, content)
        }
    }

    private fun shellQuote(value: String): String {
        return "'${value.replace("'", "'\"'\"'")}'"
    }

    private data class RootActiveLaunch(
        val token: String,
        val job: Job,
    )
}
