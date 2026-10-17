package com.aliothmoon.maameow.manager

import android.os.ParcelFileDescriptor
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import timber.log.Timber

/**
 * Shizuku newProcess 前台拉起：IRemoteProcess 追踪到的是 launcher，launcher fork 出服务进程后 waitpid，
 * 服务进程退出即 launcher 退出，经 alive 轮询秒级可见
 * 必须隔这一层：Shizuku 在 App 死亡时会 SIGTERM 追踪的进程，ART 不跑 shutdown hook，
 * 让 launcher 挨这一刀，服务进程靠 binder 死亡回调完成静音恢复等清理
 *
 * 不经 Shizuku manager App 中转，规避 OEM 杀后台断链与 MTK makeApplication 崩溃
 * Shizuku.newProcess 已 private（API 14 计划移除），直接走 IShizukuService AIDL
 */
object ShizukuSpawner : ProcessSpawner {

    // test -x：DAC 层不可执行直接 126 退出，不白等超时
    // stdio 断开防止管道写满卡住服务进程，日志全走 --log-file
    override fun wrapCommand(launcherPath: String, invocation: String): String =
        "test -x ${shellQuote(launcherPath)} || exit 126; exec $invocation </dev/null >/dev/null 2>&1"

    override fun spawn(command: String): SpawnHandle =
        RemoteProcessHandle(requireServer().newProcess(arrayOf("sh", "-c", command), null, null))

    override fun killResidual(processName: String) {
        runCatching { run(killByNameCommand(processName)) }
            .onFailure { Timber.w(it, "kill residual %s failed", processName) }
    }

    override fun findProcesses(processName: String): List<ResidualProcess> =
        runCatching { parseProcesses(run(findProcessesCommand(processName))) }
            .onFailure { Timber.w(it, "find %s failed", processName) }
            .getOrDefault(emptyList())

    override fun forceKill(pids: List<Int>) {
        runCatching { run(forceKillCommand(pids)) }
            .onFailure { Timber.w(it, "force kill %s failed", pids) }
    }

    /** 同步跑完再返回，避免与下一次拉起交错 */
    private fun run(command: String): List<String> {
        val process = requireServer().newProcess(arrayOf("sh", "-c", command), null, null)
        try {
            val out = ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
                .bufferedReader().use { it.readLines() }
            runCatching { process.waitFor() }
            return out
        } finally {
            runCatching { process.destroy() }
        }
    }

    private fun requireServer(): IShizukuService {
        val binder = Shizuku.getBinder() ?: error("Shizuku binder unavailable")
        return IShizukuService.Stub.asInterface(binder)
    }

    private class RemoteProcessHandle(private val process: IRemoteProcess) : SpawnHandle {

        // server 侧异常（如 Shizuku 自身重启）判不了生死，按存活继续等
        override fun isAlive(): Boolean = runCatching { process.alive() }.getOrDefault(true)

        override fun exitCode(): Int? = runCatching { process.exitValue() }.getOrNull()
    }
}
