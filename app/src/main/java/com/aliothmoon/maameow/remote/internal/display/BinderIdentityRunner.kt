package com.aliothmoon.maameow.remote.internal.display

import android.os.Handler
import android.os.Looper
import android.os.Process
import android.system.Os
import com.aliothmoon.maameow.third.Ln
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/** VDM Binder 调用所需的主线程调度和 root/shell 身份切换。 */
internal object BinderIdentityRunner {
    private const val TASK_PENDING = 0
    private const val TASK_ABANDONED = 1
    private const val TASK_DELIVERED = 2

    /**
     * 通用内核 6.6 及以前会从线程组主线程读取 Binder sender_euid，因此 Root 进程中的
     * VDM 调用必须在主线程执行。等待方放弃后不打断主 Looper，由迟到任务清理资源。
     */
    fun <T> onOwnerThread(
        action: () -> T,
        onAbandoned: (T) -> Unit,
        onAbandonedFailure: () -> Unit,
        timeoutSeconds: Long = 30L,
    ): T {
        if (Process.myUid() != Process.ROOT_UID) return action()
        val mainLooper = Looper.getMainLooper()
            ?: throw IllegalStateException("Root service has no main Looper")
        if (Looper.myLooper() == mainLooper) return action()

        val delivery = AtomicInteger(TASK_PENDING)
        val task = FutureTask(Callable {
            val result = try {
                action()
            } catch (failure: Throwable) {
                if (!delivery.compareAndSet(TASK_PENDING, TASK_DELIVERED)) {
                    try {
                        onAbandonedFailure()
                    } catch (cleanupFailure: Throwable) {
                        failure.addSuppressed(cleanupFailure)
                    }
                }
                throw failure
            }

            if (delivery.compareAndSet(TASK_PENDING, TASK_DELIVERED)) {
                result
            } else {
                try {
                    onAbandoned(result)
                } catch (cleanupFailure: Throwable) {
                    throw OwnerThreadAbandonedException(
                        "Timed-out VDM creation cleanup failed",
                        cleanupFailure,
                    )
                }
                throw OwnerThreadAbandonedException("Timed-out VDM creation was cleaned up")
            }
        })
        if (!Handler(mainLooper).post(task)) {
            throw IllegalStateException("Root service main Looper rejected VDM work")
        }
        try {
            return task.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (failure: InterruptedException) {
            if (delivery.compareAndSet(TASK_PENDING, TASK_ABANDONED)) {
                Thread.currentThread().interrupt()
                throw OwnerThreadAbandonedException(
                    "Interrupted while waiting for VDM creation; owner task will clean up",
                    failure,
                )
            }
            return try {
                getDeliveredTaskResult(task)
            } finally {
                Thread.currentThread().interrupt()
            }
        } catch (failure: TimeoutException) {
            if (delivery.compareAndSet(TASK_PENDING, TASK_ABANDONED)) {
                throw OwnerThreadAbandonedException(
                    "VDM creation timed out; owner task will clean up",
                    failure,
                )
            }
            return getDeliveredTaskResult(task)
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        }
    }

    /**
     * VDM 根据 Binder sender euid 校验 com.android.shell。Root 主线程切换 euid 会短暂影响
     * 整个进程的 Binder 身份，因此调用方必须把本区块限制在必要的 VDM 调用内。
     */
    @Suppress("DEPRECATION")
    fun <T> withShellIdentity(action: () -> T): T {
        if (Process.myUid() == Process.SHELL_UID) return action()

        if (Os.geteuid() != Process.ROOT_UID) {
            terminateForIdentityFailure("Unexpected effective UID before VDM call")
        }
        var transitionFailure: Throwable? = null
        val enteredShell = try {
            Os.seteuid(Process.SHELL_UID)
            Os.geteuid() == Process.SHELL_UID
        } catch (failure: Throwable) {
            transitionFailure = failure
            false
        }
        if (!enteredShell) {
            if (!restoreRootIdentity()) {
                terminateForIdentityFailure(
                    "Could not restore root Binder identity after a failed shell transition"
                )
            }
            transitionFailure?.let { throw it }
            throw SecurityException("Could not enter shell Binder identity")
        }

        try {
            return action()
        } finally {
            if (!restoreRootIdentity()) {
                terminateForIdentityFailure("Could not restore root Binder identity")
            }
        }
    }

    private fun <T> getDeliveredTaskResult(task: FutureTask<T>): T {
        var interrupted = false
        try {
            while (true) {
                try {
                    return task.get()
                } catch (_: InterruptedException) {
                    // 结果已经赢得交付竞态，先收取结果以免泄漏，再恢复中断标记。
                    interrupted = true
                } catch (failure: ExecutionException) {
                    throw failure.cause ?: failure
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    @Suppress("DEPRECATION")
    private fun restoreRootIdentity(): Boolean = try {
        if (Os.geteuid() != Process.ROOT_UID) Os.seteuid(Process.ROOT_UID)
        Os.geteuid() == Process.ROOT_UID
    } catch (_: Throwable) {
        false
    }

    private fun terminateForIdentityFailure(message: String): Nothing {
        Ln.e("$message; terminating privileged service process")
        Process.killProcess(Process.myPid())
        throw IdentityRestoreError(message)
    }
}

internal class IdentityRestoreError(message: String) : Error(message)

internal class OwnerThreadAbandonedException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
