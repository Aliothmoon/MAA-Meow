package com.aliothmoon.maameow.manager

import com.topjohnwu.superuser.Shell
import timber.log.Timber

/**
 * libsu 共享 root shell 拉起：必须后台化，前台会阻塞整个 shell
 * 代价是 launcher 退出状态不可见，只能等 binder 超时
 */
object SuSpawner : ProcessSpawner {

    /**
     * su 默认继承 App 的挂载命名空间，降权后的 shell 在部分存储栈下进不了 Android/data；
     * 进 init 的命名空间，与 Shizuku（adbd 拉起）同一环境。nsenter 不可用时照旧
     */
    override fun wrapCommand(launcherPath: String, invocation: String): String =
        "if nsenter -t 1 -m -- true 2>/dev/null; then " +
                "nsenter -t 1 -m -- $invocation >/dev/null 2>&1 & " +
                "else $invocation >/dev/null 2>&1 & fi"

    override fun spawn(command: String): SpawnHandle? {
        val result = Shell.cmd(command).exec()
        check(result.code == 0) {
            result.err.joinToString("\n").ifBlank { "exit code=${result.code}" }
        }
        return null
    }

    override fun killResidual(processName: String) {
        runCatching { Shell.cmd(killByNameCommand(processName)).exec() }
            .onFailure { Timber.w(it, "kill residual %s failed", processName) }
    }

    override fun findProcesses(processName: String): List<ResidualProcess> =
        runCatching { parseProcesses(Shell.cmd(findProcessesCommand(processName)).exec().out) }
            .onFailure { Timber.w(it, "find %s failed", processName) }
            .getOrDefault(emptyList())

    override fun forceKill(pids: List<Int>) {
        runCatching { Shell.cmd(forceKillCommand(pids)).exec() }
            .onFailure { Timber.w(it, "force kill %s failed", pids) }
    }
}
