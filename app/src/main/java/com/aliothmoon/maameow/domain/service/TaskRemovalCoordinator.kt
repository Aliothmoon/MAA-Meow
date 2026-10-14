package com.aliothmoon.maameow.domain.service

import android.os.Build
import android.os.Process
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.manager.RemoteServiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

/** 统一处理 Activity 任务从最近任务中移除后的任务、App 与提权进程生命周期。 */
class TaskRemovalCoordinator internal constructor(
    private val appSettings: AppSettingsManager,
    private val compositionService: MaaCompositionService,
    private val sdkInt: Int,
    private val stopTimeoutMs: Long,
    private val unbindRemote: () -> Unit,
    private val terminateApp: () -> Unit,
    private val cleanupScope: CoroutineScope,
) {

    constructor(
        appSettings: AppSettingsManager,
        compositionService: MaaCompositionService,
    ) : this(
        appSettings = appSettings,
        compositionService = compositionService,
        sdkInt = Build.VERSION.SDK_INT,
        stopTimeoutMs = STOP_TIMEOUT_MS,
        unbindRemote = RemoteServiceManager::unbind,
        terminateApp = { Process.killProcess(Process.myPid()) },
        cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private val cleanupLock = Any()
    private val lifecycleLock = Any()
    private var activeCleanup: Deferred<Boolean>? = null
    private var activeCleanupGeneration: Long = -1L
    private val foregroundGeneration = AtomicLong(0L)
    private var appInForeground: Boolean = false

    /** 新 Activity 已进入前台；旧任务移除事件不得再清理它建立的新会话。 */
    fun onAppForeground() {
        // 先发布代际，再等待旧清理的临界区；这样最终 terminate 也能看到正在重开的 Activity。
        foregroundGeneration.incrementAndGet()
        synchronized(lifecycleLock) {
            appInForeground = true
        }
    }

    fun onAppBackground() = synchronized(lifecycleLock) {
        appInForeground = false
    }

    /**
     * 合并 Activity 与前台 Service 对同一次移除事件的回调。
     * [onFinished] 为 false 表示设置要求保留，或旧事件已被重开 App 取代。
     */
    fun requestTaskRemoval(onFinished: (cleaned: Boolean) -> Unit = {}) {
        val generationAtRemoval = foregroundGeneration.get()
        val cleanup = synchronized(cleanupLock) {
            val previous = activeCleanup?.takeUnless { it.isCompleted }
            if (previous != null && activeCleanupGeneration == generationAtRemoval) {
                previous
            } else {
                cleanupScope.async(start = CoroutineStart.LAZY) {
                    if (sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        Timber.i("Ignoring task removal cleanup below Android 14")
                        return@async false
                    }
                    previous?.await()
                    appSettings.awaitLoaded()
                    if (shouldContinueAfterTaskRemoved() || wasReopened(generationAtRemoval)) {
                        Timber.i("Ignoring task removal because the task should be kept or the app reopened")
                        false
                    } else {
                        performCleanup(generationAtRemoval)
                    }
                }
            }.also { created ->
                activeCleanup = created
                activeCleanupGeneration = generationAtRemoval
                created.invokeOnCompletion {
                    synchronized(cleanupLock) {
                        if (activeCleanup === created) {
                            activeCleanup = null
                            activeCleanupGeneration = -1L
                        }
                    }
                }
            }
        }
        cleanupScope.launch {
            runCatching { cleanup.await() }
                .onSuccess(onFinished)
        }
        cleanup.start()
    }

    private suspend fun performCleanup(generationAtRemoval: Long): Boolean {
        Timber.i("Recent task removed, stopping task and remote process")
        val watchdog = startTerminationWatchdog()
        var stopTimedOut = false
        try {
            if (!compositionService.state.value.isTerminal()) {
                // stopJob 不属于 withTimeout：同步 Binder 卡住时，等待方仍能按时继续清理。
                val stopTask = cleanupScope.async {
                    compositionService.stop(MaaCompositionService.StopOrigin.USER)
                }
                if (withTimeoutOrNull(stopTimeoutMs) { stopTask.await(); true } != true) {
                    Timber.w("Timed out stopping task after recent task removal")
                    stopTimedOut = true
                    stopTask.cancel()
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Stopping task after recent task removal failed")
        } finally {
            if (stopTimedOut) {
                // 同步 Binder 可能仍持有 runMutex；重开也不能继承这把永久卡住的锁。
                terminateApp()
            } else {
                // 与 onAppForeground 共用锁：新 Activity 要等旧清理完成，不能在检查后插入新会话。
                synchronized(lifecycleLock) {
                    if (!wasReopenedLocked(generationAtRemoval)) {
                        runCatching(unbindRemote)
                            .onFailure { Timber.w(it, "Remote cleanup after recent task removal failed") }
                        runCatching {
                            runBlocking { compositionService.completeStopAfterRemoteExit() }
                        }.onFailure { Timber.w(it, "Finishing local state after task removal failed") }
                    }
                }
            }
            watchdog.interrupt()
            if (!stopTimedOut) terminateUnlessReopened(generationAtRemoval)
        }
        return !wasReopened(generationAtRemoval)
    }

    /** Binder 同步调用若不可取消，最终通过结束 App 触发远程生命周期 Binder 清理。 */
    private fun startTerminationWatchdog(): Thread = Thread {
        try {
            Thread.sleep(stopTimeoutMs + WATCHDOG_GRACE_MS)
            Timber.w("Task removal cleanup watchdog expired, terminating app process")
            // 不能因快速重开跳过：旧 Binder 若持有 runMutex 卡死，新会话也无法恢复。
            terminateApp()
        } catch (_: InterruptedException) {
            // 正常清理完成。
        }
    }.apply {
        name = "task-removal-watchdog"
        isDaemon = true
        start()
    }

    private fun terminateUnlessReopened(generationAtRemoval: Long) {
        if (wasReopened(generationAtRemoval)) {
            Timber.i("Skip app process termination because a new app task was opened")
        } else {
            terminateApp()
        }
    }

    private fun wasReopened(generationAtRemoval: Long): Boolean = synchronized(lifecycleLock) {
        wasReopenedLocked(generationAtRemoval)
    }

    private fun wasReopenedLocked(generationAtRemoval: Long): Boolean =
        appInForeground || foregroundGeneration.get() != generationAtRemoval

    private fun shouldContinueAfterTaskRemoved(): Boolean =
        appSettings.continueTaskAfterTaskRemoved.value

    private fun MaaExecutionState.isTerminal(): Boolean =
        this == MaaExecutionState.IDLE || this == MaaExecutionState.ERROR

    private companion object {
        const val STOP_TIMEOUT_MS = 10_000L
        const val WATCHDOG_GRACE_MS = 2_000L
    }
}
