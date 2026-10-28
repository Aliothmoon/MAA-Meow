package com.aliothmoon.maameow.manager

/** 提权进程的拉起方式（su / Shizuku newProcess），与服务规格解耦 */
interface ProcessSpawner {

    /** 把 launcher 调用串包成完整 shell 命令（后台化或前台 exec） */
    fun wrapCommand(launcherPath: String, invocation: String): String

    /** 执行拉起，失败抛异常；返回探活句柄，拉起方式不支持探活时返回 null */
    fun spawn(command: String): SpawnHandle?

    fun killResidual(processName: String)

    /** 同名进程及其已运行时长，查不了返回空 */
    fun findProcesses(processName: String): List<ResidualProcess>

    /** 按 pid 硬杀：被 SIGSTOP 停住的也杀得掉 */
    fun forceKill(pids: List<Int>)
}

data class ResidualProcess(val pid: Int, val elapsed: String)

interface SpawnHandle {

    fun isAlive(): Boolean

    fun exitCode(): Int?
}

class ProcessExitedException(val exitCode: Int?) :
    IllegalStateException("launcher exited early code=${exitCode ?: "?"}")

internal fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

/**
 * root 执行时把 [dir] 下不归 shell 的条目修回 2000：root 建的目录降到 shell 后写不进去
 * 以 shell 身份执行时没权限也用不着，直接跳过
 */
internal fun shellOwnershipRepairCommand(dir: String): String =
    $$"[ \"$(id -u)\" = 0 ] && find $${shellQuote(dir)} ! -user 2000 -exec chown -h 2000:2000 {} + 2>/dev/null"

/** 硬杀残留进程：ART 收到 SIGTERM 不跑 shutdown hook，清理不依赖此路径 */
internal fun killByNameCommand(processName: String): String =
    "kill $(pidof ${shellQuote(processName)}) 2>/dev/null"

/** 没有同名进程时 pidof 失败、无输出 */
internal fun findProcessesCommand(processName: String): String =
    $$"p=$(pidof $${shellQuote(processName)}) && ps -o PID,ETIME -p ${p// /,}"

/** 跳过表头，取 PID 与 ETIME 两列 */
internal fun parseProcesses(lines: List<String>): List<ResidualProcess> = lines.mapNotNull { line ->
    val cols = line.trim().split(Regex("\\s+"))
    val pid = cols.firstOrNull()?.toIntOrNull() ?: return@mapNotNull null
    ResidualProcess(pid, cols.getOrNull(1).orEmpty())
}

internal fun forceKillCommand(pids: List<Int>): String = "kill -9 ${pids.joinToString(" ")} 2>/dev/null"
