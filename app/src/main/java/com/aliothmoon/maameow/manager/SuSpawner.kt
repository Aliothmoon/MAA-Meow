package com.aliothmoon.maameow.manager

import com.topjohnwu.superuser.Shell
import timber.log.Timber

/**
 * libsu 共享 root shell 拉起：必须后台化，前台会阻塞整个 shell
 * 代价是 launcher 退出状态不可见，只能等 binder 超时
 */
object SuSpawner : ProcessSpawner {

    override fun wrapCommand(launcherPath: String, invocation: String): String =
        "$invocation >/dev/null 2>&1 &"

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
}
