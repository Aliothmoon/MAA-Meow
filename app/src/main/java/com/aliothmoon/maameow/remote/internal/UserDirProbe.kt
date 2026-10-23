package com.aliothmoon.maameow.remote.internal

import android.system.Os
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * AsstSetUserDir 前先用 Java IO 探一遍 userDir
 *
 * libMaaUtils 对不可访问路径 stat 会抛未捕获 C++ 异常直接 abort 整个提权进程，
 * 这里失败只返回原因，不做任何会崩的事。不按 SDK 版本判断，只看实际权限。
 *
 * #298：根目录和 debug 可写，但 resource/ 丢了 ext_data_rw 组继承时，MaaCore 去 stat
 * onnx 仍会 abort。已经存在的 resource 子树在这里用当前进程身份列目录、只读打开普通文件；
 * 目录真的还没有（独立目录首次解压前）则放行。exists() 在权限被拒时也会是 false，不能拿它当不存在。
 * 不读文件内容，不跟随逃出 resource/ 的链接；目录成环就停，链接自身成环算失败。
 * 这只是把崩溃换成已有的换目录失败（Refs #298），并不恢复 APP_DIR 权限，原生异常边界也还在。
 */
object UserDirProbe {

    private const val PROBE_FILE = ".probe"
    private const val RESOURCE_DIR = "resource"

    /** 与内核 MAXSYMLINKS 一致，超过就会 ELOOP */
    private const val MAX_LINK_HOPS = 40

    /** 探测通过返回 null，否则返回可读的失败原因，附带属主与挂载诊断 */
    fun probe(userDir: File): String? = firstFailure(userDir)?.let { "$it (${diagnose(userDir)})" }

    private fun firstFailure(userDir: File): String? {
        // App 侧可能还没建出来，能自己建也算通过
        if (!userDir.isDirectory && !runCatching { userDir.mkdirs() }.getOrDefault(false)) {
            return "mkdirs failed: $userDir"
        }
        if (!userDir.canRead()) return "not readable: $userDir"
        if (!userDir.canWrite()) return "not writable: $userDir"

        // canRead/canWrite 只查 DAC 位，FUSE 层拒绝要真写一次才知道
        val debugDir = File(userDir, "debug")
        if (!debugDir.isDirectory && !runCatching { debugDir.mkdirs() }.getOrDefault(false)) {
            return "mkdirs failed: $debugDir"
        }
        val probe = File(debugDir, PROBE_FILE)
        try {
            probe.writeText(System.currentTimeMillis().toString())
            probe.readText()
            probe.delete()
        } catch (e: IOException) {
            return "write probe failed: ${e.message}"
        } catch (e: SecurityException) {
            return "write probe denied: ${e.message}"
        }
        return resourceSubtreeFailure(userDir)
    }

    /**
     * 父目录列得出来的 resource 就算存在，哪怕子项 exists()==false。
     * 列不出来、打不开都是失败，避免后面原生 stat 直接把进程打崩。
     */
    private fun resourceSubtreeFailure(userDir: File): String? {
        val names = try {
            userDir.list()
        } catch (e: SecurityException) {
            return "list denied: $userDir: ${e.message}"
        } ?: return "cannot list: $userDir"
        if (RESOURCE_DIR !in names) return null
        return resourceTreeFailure(File(userDir, RESOURCE_DIR))
    }

    /** LoadResource 前对 [parentDir]/resource 做同样的探测，cache、overrides 等资源树不经过 setup */
    fun resourceFailure(parentDir: File): String? = resourceSubtreeFailure(parentDir)

    private fun resourceTreeFailure(resource: File): String? {
        // resource 被普通文件占了，原生 stat 子路径会 ENOTDIR
        if (query { resource.isFile }) return "not a directory: $resource"
        val root = resource.absoluteFile.toPath().normalize()
        return try {
            visitResource(resource, root, mutableSetOf())
        } catch (e: IOException) {
            "inaccessible: $resource: ${e.message}"
        } catch (e: SecurityException) {
            "access denied: $resource: ${e.message}"
        }
    }

    private fun visitResource(file: File, root: Path, visited: MutableSet<Path>): String? {
        val path = file.absoluteFile.toPath().normalize()
        if (!visited.add(path)) return null
        if (Files.isSymbolicLink(path)) return followLink(file, path, root, visited)

        val names = try {
            file.list()
        } catch (e: SecurityException) {
            return "list denied: $file: ${e.message}"
        }
        if (names != null) {
            for (name in names) {
                visitResource(File(file, name), root, visited)?.let { return it }
            }
            return null
        }
        // list()==null 既可能是普通文件，也可能是目录权限不够。后者不能当成空目录放行
        if (query { file.isDirectory }) return "cannot list: $file"
        if (query { file.isFile } || !query { file.exists() }) return openReadOnly(file)
        return "inaccessible: $file"
    }

    private fun followLink(file: File, path: Path, root: Path, visited: MutableSet<Path>): String? {
        // 链接指向链接时一路解到底：回到链上已有的链接就是 ELOOP，原生 stat 会失败
        val chain = mutableSetOf(path)
        var current = path
        while (true) {
            val target = try {
                Files.readSymbolicLink(current)
            } catch (e: IOException) {
                return "link unresolved: $file: ${e.message}"
            } catch (e: SecurityException) {
                return "link denied: $file: ${e.message}"
            }
            val parent = current.parent ?: return "link unresolved: $file"
            val resolved = parent.resolve(target).normalize()
            // 先做词法判断，不去 stat 目标，避免跟着链接走出 resource/
            if (!resolved.startsWith(root)) return "link escapes: $file"
            // 词法在内，但中间某段可能是指向外面的链接，按真实路径再确认一次父目录
            if (resolved != root && !resolved.parent.toRealPath().startsWith(root.toRealPath())) {
                return "link escapes: $file"
            }
            if (!Files.isSymbolicLink(resolved)) return visitResource(resolved.toFile(), root, visited)
            if (!chain.add(resolved) || chain.size > MAX_LINK_HOPS) return "link loop: $file"
            current = resolved
        }
    }

    private fun openReadOnly(file: File): String? = try {
        file.inputStream().use {
            // 确认 fd 能打开即可，不读模型内容
        }
        null
    } catch (e: IOException) {
        "open failed: $file: ${e.message}"
    } catch (e: SecurityException) {
        "open denied: $file: ${e.message}"
    }

    private inline fun query(block: () -> Boolean): Boolean = try {
        block()
    } catch (_: SecurityException) {
        false
    }

    /** 分清是属主不对（root 建的目录）还是存储栈拦的（挂载方式） */
    private fun diagnose(path: File): String {
        val existing = generateSequence(path.absoluteFile) { it.parentFile }.firstOrNull { it.exists() }
        val owner = existing?.let { runCatching { Os.stat(it.path).st_uid }.getOrNull() }
        val mounts = runCatching { File("/proc/self/mountinfo").readLines() }.getOrDefault(emptyList())
        return "owner of $existing=${owner ?: "?"}, mount=${mountOf(path.absolutePath, mounts)}"
    }

    /** mountinfo 里覆盖 [path] 的最深挂载点及其文件系统类型 */
    internal fun mountOf(path: String, mountInfo: List<String>): String {
        var best: Pair<String, String>? = null
        for (line in mountInfo) {
            val fields = line.split(' ')
            val point = fields.getOrNull(4) ?: continue
            val separator = fields.indexOf("-")
            val fsType = fields.getOrNull(separator + 1)?.takeIf { separator > 4 } ?: continue
            val covers = point == "/" || path == point || path.startsWith("$point/")
            if (covers && point.length >= (best?.first?.length ?: -1)) best = point to fsType
        }
        return best?.let { "${it.first} ${it.second}" } ?: "?"
    }
}
