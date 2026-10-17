package com.aliothmoon.maameow.manager

import android.content.Context
import android.os.IBinder
import android.os.Process
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.remote.CoreDataDir
import com.aliothmoon.maameow.root.BootstrapRegistry
import com.aliothmoon.maameow.root.ProcessExit
import com.aliothmoon.maameow.root.RootServiceBootstrapRegistry
import com.aliothmoon.maameow.root.RemoteServiceStarter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.milliseconds

/**
 * 自研 starter 进程路径的公共基座：launcher 拉起 app_process，
 * binder 回投经 MaaMeow 自己的 bootstrap provider 按 token 认领，
 * 生命周期由 appLifecycleBinder linkToDeath + lifeline 管道 EOF 自管
 *
 * 拉起方式由 [spawner] 决定（su 后台化 / Shizuku 前台 exec），服务规格由子类给出
 */
abstract class ProcessServiceConnectorBackend(
    private val spawner: ProcessSpawner,
    private val registry: BootstrapRegistry = RootServiceBootstrapRegistry,
) : RemoteServiceConnectorBackend {

    protected open val spawnTimeoutMs: Long = 15_000L

    /** 同名残留的退出宽限：比服务退出期限长，正在收尾的旧进程不误杀 */
    protected open val residualGraceMs: Long = ProcessExit.DEADLINE_MS + 1_000L

    // spawn 同步调用、残留等待与超时后的 kill / dump 不在 withTimeout 内，另计余量
    override val worstCaseConnectMs: Long
        get() = spawnTimeoutMs + residualGraceMs + SPAWN_OVERHEAD_MS

    protected abstract val eventPrefix: String

    /** 同时是残留清理的匹配键 */
    protected abstract val processNameSuffix: String

    protected abstract val serviceClass: Class<*>

    /** 各连接器独立，launcher O_TRUNC 打开，共用会互相覆盖 */
    protected abstract val logFileName: String

    // 单并发：spawn 与 killResidual 都是阻塞调用，独占队列位，先入队者先做完
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())

    @Volatile
    private var activeLaunch: ActiveLaunch? = null

    private lateinit var appContext: Context

    /** launcher 以 shell 身份写 --log-file，落 core 侧 debug/ */
    private lateinit var debugDir: File

    /** 拉起进行中（binder 尚未回投） */
    val isConnecting: Boolean get() = activeLaunch?.job?.isActive == true

    private val processName: String get() = "${appContext.packageName}:$processNameSuffix"

    fun initialize(context: Context, debugDir: File) {
        appContext = context.applicationContext
        this.debugDir = debugDir
    }

    override fun connect(callbacks: RemoteServiceConnectorBackend.Callbacks) {
        check(::appContext.isInitialized) { "${javaClass.simpleName} is not initialized" }
        // 取代进行中的旧拉起：旧 token 即刻注销，旧进程回投会被 provider 拒绝而自退
        dropActiveLaunch()
        val token = UUID.randomUUID().toString()
        val deferred = registry.register(token)

        val job = scope.launch {
            val logFile = debugLogFile()
            var spawned = false
            // 整段包住：check(launcherFile) 等异常逃逸会让协程未捕获致崩溃
            val result = runCatching {
                reapResidual()
                // 先清旧日志，确保 dump 到的一定是本次启动的产物
                logFile.delete()
                val command = buildStartCommand(token, logFile)
                ServiceBootLogger.event("${eventPrefix}_SPAWN_CALL", "token=$token")
                val handle = spawner.spawn(command)
                spawned = true
                awaitBinder(deferred, handle)
            }
            if (activeLaunch?.token != token) {
                registry.unregister(token)
                // 被取代前已回投的进程没人认领，不收就成了孤儿
                arrivedBinder(result, deferred)?.let { binder -> runCatching { destroyRemote(binder) } }
                return@launch
            }
            result.onSuccess { binder ->
                onBinderArrived(token, binder, callbacks)
            }.onFailure { throwable ->
                activeLaunch = null
                registry.unregister(token)
                // 进程路径没有 server 端 record 代杀，卡在半路的服务进程只能自己清
                if (spawned) spawner.killResidual(processName)
                reportFailure(token, throwable, logFile, callbacks)
            }
        }
        activeLaunch = ActiveLaunch(token, job)
    }

    override fun disconnect(currentBinder: IBinder?) {
        dropActiveLaunch()
        if (currentBinder != null) {
            runCatching { destroyRemote(currentBinder) }
                .onFailure { Timber.w(it, "destroy %s remote service failed", backend) }
        } else {
            // 没连上就断开：进程可能卡在半路，排进单并发队列先清后起
            scope.launch { spawner.killResidual(processName) }
        }
    }

    protected open fun destroyRemote(binder: IBinder) {
        RemoteService.Stub.asInterface(binder)?.destroy()
    }

    protected open fun buildStartCommand(token: String, logFile: File): String {
        val launcher = File(appContext.applicationInfo.nativeLibraryDir, "liblauncher.so")
        check(launcher.exists()) { "launcher not found: ${launcher.absolutePath}" }
        val invocation = buildString {
            append(shellQuote(launcher.absolutePath))
            append(" --apk=").append(shellQuote(appContext.applicationInfo.sourceDir))
            append(" --process-name=").append(shellQuote(processName))
            append(" --starter-class=").append(shellQuote(RemoteServiceStarter::class.java.name))
            append(" --token=").append(shellQuote(token))
            append(" --package=").append(shellQuote(appContext.packageName))
            append(" --class=").append(shellQuote(serviceClass.name))
            append(" --uid=").append(Process.myUid())
            append(" --log-file=").append(shellQuote(logFile.absolutePath))
            if (BuildConfig.DEBUG) {
                append(" --debug-name=").append(shellQuote(processName))
            }
        }
        // 独立目录下 debug/ 在 /data/local/tmp，App 进程建不了，交给 shell
        val mkdir = "mkdir -p ${shellQuote(logFile.parentFile!!.absolutePath)} 2>/dev/null; "
        // 上面 root 建出的目录归 0，降权后写不进去，连旧版建坏的一并修回
        return mkdir + "${shellOwnershipRepairCommand(CoreDataDir.ROOT)}; " +
                spawner.wrapCommand(launcher.absolutePath, invocation)
    }

    /** 取消会盖掉 await 的结果，回投是否已到还得看 deferred */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun arrivedBinder(result: Result<IBinder>, deferred: Deferred<IBinder>): IBinder? =
        result.getOrNull() ?: deferred.takeIf { it.isCompleted && !it.isCancelled }?.getCompleted()

    /**
     * 拉起前清同名残留：正常退出的进程宽限内会自己走，超期还在就是孤儿，硬杀并留痕
     * 所在队列单并发，不会误杀本连接器随后拉起的新进程
     */
    private suspend fun reapResidual() {
        var residual = spawner.findProcesses(processName)
        if (residual.isEmpty()) return
        val found = residual.joinToString { "pid=${it.pid} etime=${it.elapsed}" }
        ServiceBootLogger.event("${eventPrefix}_RESIDUAL_FOUND", found)
        Timber.w("Residual %s found before spawn: %s", processName, found)
        repeat((residualGraceMs / RESIDUAL_POLL_MS).toInt()) {
            delay(RESIDUAL_POLL_MS.milliseconds)
            residual = spawner.findProcesses(processName)
            if (residual.isEmpty()) {
                ServiceBootLogger.event("${eventPrefix}_RESIDUAL_EXITED", found)
                return
            }
        }
        val pids = residual.map { it.pid }
        ServiceBootLogger.event("${eventPrefix}_RESIDUAL_KILLED", "pids=$pids")
        Timber.e("Residual %s still alive after %dms, force killing %s", processName, residualGraceMs, pids)
        spawner.forceKill(pids)
    }

    protected open fun debugLogFile(): File {
        runCatching { debugDir.mkdirs() }
        return File(debugDir, logFileName)
    }

    private fun dropActiveLaunch() {
        val previous = activeLaunch ?: return
        activeLaunch = null
        previous.job.cancel()
        registry.unregister(previous.token)
    }

    /** binder 回投与进程存活竞速：进程先退出立即失败并携带退出码，不等满超时 */
    private suspend fun awaitBinder(deferred: Deferred<IBinder>, handle: SpawnHandle?): IBinder =
        withTimeout(spawnTimeoutMs.milliseconds) {
            if (handle == null) return@withTimeout deferred.await()
            coroutineScope {
                val watcher = launch {
                    while (isActive) {
                        if (!handle.isAlive()) throw ProcessExitedException(handle.exitCode())
                        delay(ALIVE_POLL_INTERVAL_MS.milliseconds)
                    }
                }
                try {
                    deferred.await()
                } finally {
                    watcher.cancel()
                }
            }
        }

    private fun onBinderArrived(
        token: String,
        binder: IBinder,
        callbacks: RemoteServiceConnectorBackend.Callbacks,
    ) {
        // token 认领：旧进程死讯不能误伤新连接
        runCatching {
            binder.linkToDeath({
                if (activeLaunch?.token != token) {
                    Timber.i("Stale %s process binder death ignored (token=%s)", processName, token)
                    return@linkToDeath
                }
                Timber.e("%s process died unexpectedly.", processName)
                callbacks.onDisconnected(backend)
            }, 0)
        }.onFailure {
            Timber.w(it, "linkToDeath failed for %s process binder", processName)
        }

        Timber.i("%s connected by %s bootstrap", processName, backend)
        ServiceBootLogger.event("${eventPrefix}_PROCESS_CONNECTED", "token=$token")
        callbacks.onConnected(backend, binder)
    }

    private fun reportFailure(
        token: String,
        throwable: Throwable,
        logFile: File,
        callbacks: RemoteServiceConnectorBackend.Callbacks,
    ) {
        val error = describeFailure(throwable, dumpSpawnDebugLog(logFile))
        ServiceBootLogger.event(
            "${eventPrefix}_PROCESS_FAIL",
            "token=$token ${error.javaClass.simpleName}: ${error.message}"
        )
        Timber.e(error, "%s start failed: token=%s", processName, token)
        callbacks.onError(backend, error)
    }

    /** 失败原因带上 launcher 日志尾部，日志导出里能直接看到进程侧死因 */
    private fun describeFailure(throwable: Throwable, logTail: String?): Throwable {
        val suffix = if (logTail.isNullOrBlank()) "" else "; launcher log tail: $logTail"
        return when {
            throwable is TimeoutCancellationException ->
                TimeoutException("$processName binder not attached within ${spawnTimeoutMs}ms$suffix")

            suffix.isEmpty() -> throwable
            else -> IllegalStateException("${throwable.message}$suffix", throwable)
        }
    }

    /** 全量打进 Timber，返回尾部几行供拼进错误信息 */
    private fun dumpSpawnDebugLog(log: File): String? {
        if (!log.exists()) {
            if (log.absolutePath.startsWith(CoreDataDir.ROOT)) {
                Timber.e("%s launch debug log is in core dir, unreadable from app: %s (adb shell cat it, or export logs)", processName, log.absolutePath)
            } else {
                Timber.e("%s launch debug log not found: %s", processName, log.absolutePath)
            }
            return null
        }
        val lines =
            runCatching { log.readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())
        if (lines.isEmpty()) {
            Timber.e(
                "%s launch debug log is empty (launcher may have crashed before opening it)",
                processName
            )
            return null
        }
        Timber.e(
            "%s launch debug log (%s):\n%s",
            processName,
            log.absolutePath,
            lines.joinToString("\n")
        )
        return lines.takeLast(LOG_TAIL_LINES).joinToString(" | ")
    }

    private data class ActiveLaunch(
        val token: String,
        val job: Job,
    )

    private companion object {
        const val SPAWN_OVERHEAD_MS = 3_000L
        const val ALIVE_POLL_INTERVAL_MS = 400L
        const val RESIDUAL_POLL_MS = 300L
        const val LOG_TAIL_LINES = 5
    }
}
