package com.aliothmoon.maameow.manager

/** 提权进程的拉起方式（su / Shizuku newProcess），与服务规格解耦 */
interface ProcessSpawner {

    /** 把 launcher 调用串包成完整 shell 命令（后台化或前台 exec） */
    fun wrapCommand(launcherPath: String, invocation: String): String

    /** 执行拉起，失败抛异常；返回探活句柄，拉起方式不支持探活时返回 null */
    fun spawn(command: String): SpawnHandle?

    fun killResidual(processName: String)
}

interface SpawnHandle {

    fun isAlive(): Boolean

    fun exitCode(): Int?
}

class ProcessExitedException(val exitCode: Int?) :
    IllegalStateException("launcher exited early code=${exitCode ?: "?"}")

internal fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

/** SIGTERM 触发服务进程 shutdown hook 优雅清理 */
internal fun killByNameCommand(processName: String): String =
    "kill $(pidof ${shellQuote(processName)}) 2>/dev/null"
