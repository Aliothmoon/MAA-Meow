package com.aliothmoon.maameow.manager

import android.content.Context
import android.os.IBinder
import android.os.Process
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.RemoteService
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
                        Timber.e("Root process died unexpectedly! Executing emergency cleanup.")
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
        val userId = Process.myUid() / 100000
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
            append(" --user-id=")
            append(userId)
            if (BuildConfig.DEBUG) {
                append(" --debug-name=")
                append(shellQuote(processName))
            }
            append(" >/dev/null 2>&1 &")
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
