package com.aliothmoon.maameow.remote.internal

import com.aliothmoon.maameow.remote.CoreDataDir
import com.aliothmoon.maameow.remote.CoreResourceConfigs
import com.aliothmoon.maameow.remote.ResourceFiles
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * 提权进程侧的独立数据目录（/data/local/tmp/maameow）
 *
 * 纯 java.io，不带日志，便于 JVM 单测；日志由 RemoteServiceImpl 打
 * 资源来源是 APK 本身（/data/app 下 0644，shell 可读），按 stamp 判断是否重解
 */
class CoreDataStore(val root: File = File(CoreDataDir.ROOT)) {

    companion object {
        private const val RESOURCE_STAMP_FILE = ".resource_stamp"
        private const val BUFFER = 128 * 1024
    }

    private val lock = Any()

    private val resourceDir get() = File(root, "resource")
    private val stampFile get() = File(root, RESOURCE_STAMP_FILE)

    fun resourceStamp(): String = runCatching { stampFile.readText().trim() }.getOrDefault("")

    /**
     * 内置资源与 stamp 不符时从 APK 重解；成功后才写 stamp，中途被杀下次重来
     * overrides 模板同 App 侧 ResourceInitService：每次重解都覆盖，用户改动随后由投递写回
     */
    fun ensureResources(apk: File, stamp: String): Boolean = synchronized(lock) {
        if (resourceStamp() == stamp && File(resourceDir, ResourceFiles.VERSION_FILE).isFile) return true
        stampFile.delete()
        resourceDir.deleteRecursively()
        resourceDir.mkdirs()
        var count = 0
        try {
            ZipFile(apk).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val rel = when {
                        entry.name.startsWith(CoreDataDir.RESOURCE_ASSET_PREFIX) ->
                            "resource/" + entry.name.removePrefix(CoreDataDir.RESOURCE_ASSET_PREFIX)

                        entry.name == CoreDataDir.OVERRIDES_ASSET_ENTRY -> CoreDataDir.OVERRIDES_TASKS_REL
                        else -> continue
                    }
                    if (!CoreDataDir.isSafeRelPath(rel)) continue
                    val target = File(root, rel)
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { input.copyTo(it, BUFFER) }
                    }
                    count++
                }
            }
            if (count == 0) throw IOException("no bundled resource in $apk")
            stampFile.writeText(stamp)
            true
        } catch (e: Exception) {
            resourceDir.deleteRecursively()
            false
        }
    }

    /** 热更包覆盖 resource/，同 App 侧 UpdateService；失败删 version.json，下次 ensureResources 重解内置 */
    fun applyHotUpdate(zip: InputStream): Boolean = synchronized(lock) {
        val target = resourceDir.apply { mkdirs() }
        try {
            ZipInputStream(BufferedInputStream(zip)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val rel = CoreDataDir.hotUpdateEntryToRelPath(entry.name)
                        if (rel != null && CoreDataDir.isSafeRelPath(rel)) {
                            val file = File(target, rel)
                            file.parentFile?.mkdirs()
                            file.outputStream().use { zis.copyTo(it, BUFFER) }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            deriveTasksJson(target)
            true
        } catch (e: Exception) {
            File(target, ResourceFiles.VERSION_FILE).delete()
            false
        }
    }

    /** resource/version.json 的 last_updated，没有返回空串 */
    fun resourceVersion(): String =
        ResourceFiles.readLastUpdated(File(resourceDir, ResourceFiles.VERSION_FILE)).orEmpty()

    fun putFile(rel: String, input: InputStream): Boolean = synchronized(lock) {
        if (!CoreDataDir.isSafeRelPath(rel)) return false
        runCatching {
            val file = File(root, rel)
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, ".${file.name}.tmp")
            tmp.outputStream().use { input.copyTo(it, BUFFER) }
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) throw IOException("rename failed: $file")
            }
        }.isSuccess
    }

    fun removeStaleResourceConfigs(retainedPaths: List<String>): Boolean = synchronized(lock) {
        runCatching {
            val retained = retainedPaths.toSet()
            val canonicalRoot = root.canonicalFile
            for (rel in CoreResourceConfigs.paths(root)) {
                if (rel in retained) continue
                val file = File(root, rel)
                check(file.canonicalFile.toPath().startsWith(canonicalRoot.toPath())) {
                    "Config escapes Core root: $rel"
                }
                check(file.isFile && file.delete()) { "Cannot remove stale Core config: $rel" }
            }
            true
        }.getOrDefault(false)
    }

    fun listDebugFiles(): List<String> {
        val debug = File(root, "debug")
        if (!debug.isDirectory) return emptyList()
        return debug.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(debug).invariantSeparatorsPath }
            .toList()
    }

    fun debugFile(rel: String): File? {
        if (!CoreDataDir.isSafeRelPath(rel)) return null
        return File(File(root, "debug"), rel).takeIf { it.isFile }
    }

    fun clear(): Boolean = synchronized(lock) { !root.exists() || root.deleteRecursively() }

    /** 全球服子目录一并处理，App 侧 MaaResourceLoader 只处理当前客户端 */
    private fun deriveTasksJson(resource: File) {
        val dirs = mutableListOf(resource)
        File(resource, "global").listFiles()
            ?.filter { it.isDirectory }
            ?.forEach { dirs += File(it, "resource") }
        dirs.forEach { ResourceFiles.deriveTasksJson(it) }
    }
}
