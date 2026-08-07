package com.aliothmoon.maameow.remote

import com.aliothmoon.maameow.third.Ln
import java.io.File

/**
 * 提权进程（Shizuku=shell / Root=uid0）与 App 进程 uid 不同。
 * MaaCore 的 asst.log / logcat 等以提权身份落盘的 debug 日志可能因受限 umask（如 077）
 * 变成对 App uid 不可读，导致 App 侧导出日志时 EACCES。
 * 递归放开读权限，保证双进程都能读取这些日志。
 */
object DebugLogPermissionFixer {

    private const val TAG = "DebugLogPermissionFixer"

    /**
     * 递归将 [dir] 下所有目录设为可读可执行、所有文件设为可读（ownerOnly=false，对全体放开）。
     */
    fun makeReadableForApp(dir: File) {
        if (!dir.exists()) return
        try {
            var fixed = 0
            dir.walkTopDown().forEach { f ->
                f.setReadable(true, false)
                if (f.isDirectory) {
                    f.setExecutable(true, false)
                }
                fixed++
            }
            Ln.i("$TAG: fixed $fixed entries under ${dir.absolutePath}")
        } catch (e: Exception) {
            Ln.e("$TAG: makeReadableForApp error: ${e.message}")
        }
    }
}
