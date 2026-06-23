package com.aliothmoon.maameow.manager

import android.content.Context
import android.os.Build
import android.os.IBinder
import android.os.Process
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.constant.MaaFiles
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.remote.RemoteServiceImpl
import com.aliothmoon.maameow.root.RootServiceBootstrapRegistry
import com.aliothmoon.maameow.root.RootServiceStarter
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object RootRemoteServiceConnector : RemoteServiceConnectorBackend {

    override val backend: RemoteBackend = RemoteBackend.ROOT

    private const val ROOT_BIND_TIMEOUT_MS = 15_000L

    private val initialized = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())

    private lateinit var appContext: Context

    @Volatile
    private var activeLaunch: ActiveLaunch? = null

    fun initialize(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            appContext = context.applicationContext
        }
    }

    override fun connect(callbacks: RemoteServiceConnectorBackend.Callbacks) {
        ensureInitialized()

        val token = UUID.randomUUID().toString()
        val deferred = RootServiceBootstrapRegistry.register(token)
        val job = scope.launch {
            val startResult = withContext(Dispatchers.IO) {
                startRemoteService(token)
            }
            val active = activeLaunch
            if (active?.token != token) {
                RootServiceBootstrapRegistry.unregister(token)
                return@launch
            }

            val startError = startResult.exceptionOrNull()
            if (startError != null) {
                activeLaunch = null
                RootServiceBootstrapRegistry.unregister(token)
                callbacks.onError(backend, startError)
                return@launch
            }

            runCatching {
                withTimeout(ROOT_BIND_TIMEOUT_MS) {
                    deferred.await()
                }
            }.onSuccess { binder ->
                if (activeLaunch?.token != token) {
                    RootServiceBootstrapRegistry.unregister(token)
                    return@onSuccess
                }

                try {
                    binder.linkToDeath({
                        Timber.e("Root process died unexpectedly.")
                        callbacks.onDisconnected(backend)
                    }, 0)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to link to death for root binder")
                }

                Timber.i("RemoteService connected by root bootstrap")
                callbacks.onConnected(backend, binder)
            }.onFailure { throwable ->
                RootServiceBootstrapRegistry.unregister(token)
                if (activeLaunch?.token == token) {
                    activeLaunch = null
                    dumpDebugLog()
                    callbacks.onError(backend, throwable)
                }
            }
        }

        activeLaunch = ActiveLaunch(token, job)
    }

    override fun disconnect(currentBinder: IBinder?) {
        val active = activeLaunch
        activeLaunch = null
        active?.job?.cancel()
        active?.token?.let(RootServiceBootstrapRegistry::unregister)
        currentBinder?.let { binder ->
            runCatching {
                RemoteService.Stub.asInterface(binder)?.destroy()
            }.onFailure {
                Timber.w(it, "destroy root remote service failed")
            }
        }
    }

    private fun startRemoteService(token: String): Result<Unit> {
        return runCatching {
            val command = buildStartCommand(token)
            val result = Shell.cmd(command).exec()
            if (result.code != 0) {
                error(result.err.joinToString("\n").ifBlank { "exit code=${result.code}" })
            }
        }.onFailure {
            Timber.e(it, "startRemoteService failed")
        }
    }

    private fun buildStartCommand(token: String): String {
        val processName = "${appContext.packageName}:root_service"
        val launcherFile = File(
            appContext.applicationInfo.nativeLibraryDir,
            "liblauncher.so"
        )
        check(launcherFile.exists()) { "root launcher not found: ${launcherFile.absolutePath}" }
        val launcherPath = launcherFile.absolutePath
        val uid = Process.myUid()
        val logFile = debugLogFile()
        return buildString {
            append(shellQuote(launcherPath))
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
            append(shellQuote(RemoteServiceImpl::class.java.name))
            append(" --uid=")
            append(uid)
            if (RootServiceUidPolicy.shouldKeepRoot(Build.VERSION.SDK_INT)) {
                append(" --keep-root")
            }
            append(" --log-file=")
            append(shellQuote(logFile.absolutePath))
            if (BuildConfig.DEBUG) {
                append(" --debug-name=")
                append(shellQuote(processName))
            }
            append(" >/dev/null 2>&1 &")
        }
    }

    private fun debugLogFile(): File {
        val dir = File(appContext.getExternalFilesDir(null), "${MaaFiles.MAA}/${MaaFiles.DEBUG}")
        dir.mkdirs()
        return File(dir, "root_launch_debug.log")
    }

    private fun dumpDebugLog() {
        val log = debugLogFile()
        if (!log.exists()) {
            Timber.e("Root launch debug log not found: %s", log.absolutePath)
            return
        }
        val content = runCatching { log.readText().trim() }.getOrNull()
        if (content.isNullOrBlank()) {
            Timber.e("Root launch debug log is empty (launcher may have crashed before opening it)")
        } else {
            Timber.e("Root launch debug log (%s):\n%s", log.absolutePath, content)
        }
    }

    private fun ensureInitialized() {
        check(initialized.get()) { "RootRemoteServiceConnector is not initialized" }
    }

    private fun shellQuote(value: String): String {
        return "'${value.replace("'", "'\"'\"'")}'"
    }

    private data class ActiveLaunch(
        val token: String,
        val job: Job
    )
}
