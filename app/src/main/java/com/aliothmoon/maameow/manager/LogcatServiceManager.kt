package com.aliothmoon.maameow.manager

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
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
import rikka.shizuku.Shizuku
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
    private var currentServiceArgs: Shizuku.UserServiceArgs? = null

    private val shizukuConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Timber.i("LogcatService connected via Shizuku: %s", name)
            _service.value = ILogcatService.Stub.asInterface(binder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.i("LogcatService disconnected via Shizuku: %s", name)
            _service.value = null
        }
    }

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
        if (_service.value != null) return
        when (RemoteAccessCoordinator.configuredBackend()) {
            RemoteBackend.SHIZUKU -> bindViaShizuku()
            RemoteBackend.ROOT -> bindViaRoot()
        }
    }

    fun unbind() {
        // Shizuku
        val args = currentServiceArgs
        if (args != null) {
            currentServiceArgs = null
            runCatching {
                Shizuku.unbindUserService(args, shizukuConnection, true)
            }.onFailure {
                Timber.w(it, "unbind logcat shizuku service failed")
            }
        }

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
        val args = Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, LogcatCaptureServiceImpl::class.java.name)
        ).apply {
            processNameSuffix("logcat")
            daemon(false)
            tag(serviceTag)
            version(serviceVersion.incrementAndGet())
            debuggable(BuildConfig.DEBUG)
        }
        currentServiceArgs = args

        try {
            Shizuku.bindUserService(args, shizukuConnection)
        } catch (e: Exception) {
            Timber.e(e, "bindLogcatService via Shizuku failed")
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
