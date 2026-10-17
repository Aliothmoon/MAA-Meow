package com.aliothmoon.maameow.domain.service

import android.content.Context
import android.os.ParcelFileDescriptor
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.constant.MaaFiles
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.remote.CoreDataDir
import com.aliothmoon.maameow.remote.CoreResourceConfigs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * MaaCore 用独立目录时，App 向提权进程投递它自己写不进去的东西，见 [CoreDataDir]
 *
 * 投递类方法在 [MaaPathConfig.isCoreSeparated] 为 false 时直接返回成功；
 * [clearRemote] 不看模式，切回应用目录后也要能清残留
 */
class CoreDataPusher(
    private val context: Context,
    private val pathConfig: MaaPathConfig,
) {
    private val mutex = Mutex()

    private val apkPath: String
        get() = context.applicationInfo.sourceDir

    /** 同一 APK 装两次（debug 覆盖安装）versionCode 不变，带上 mtime */
    private val resourceStamp: String by lazy {
        "${pathConfig.appVersionCode}:${File(apkPath).lastModified()}"
    }

    private val lastUpdateZip: File
        get() = File(pathConfig.rootDir, MaaFiles.LAST_RESOURCE_UPDATE_ZIP)

    /** LoadResource 前调用：资源、热更、用户文件全部就位才 true */
    suspend fun prepare(srv: RemoteService): Boolean {
        if (!pathConfig.isCoreSeparated) return true
        return locked {
            val start = System.currentTimeMillis()
            if (!srv.ensureCoreResources(apkPath, resourceStamp)) {
                Timber.e("ensureCoreResources failed: apk=%s stamp=%s", apkPath, resourceStamp)
                return@locked false
            }
            Timber.i("core resources ready (stamp=%s) in %dms", resourceStamp, System.currentTimeMillis() - start)
            pushHotUpdateIfNeededLocked(srv) && pushResourceConfigsLocked(srv) && pushUserDataLocked(srv)
        }
    }

    /** 任务下发前调用，把用户刚改过的作业 / 基建文件送过去 */
    suspend fun pushUserData(): Boolean {
        if (!pathConfig.isCoreSeparated) return true
        return withRemote(false) { pushUserDataLocked(it) }
    }

    /** 热更新完成后调用；未连接则留到下次 prepare */
    suspend fun pushHotUpdateIfNeeded() {
        if (!pathConfig.isCoreSeparated) return
        withRemote(Unit) { pushHotUpdateIfNeededLocked(it) }
    }

    /** 设置页「清除独立目录数据」：需要提权服务在线 */
    suspend fun clearRemote(): Boolean = withRemote(false) { srv ->
        runCatching { srv.clearCoreData() }
            .onFailure { Timber.e(it, "clearCoreData failed") }
            .getOrDefault(false)
    }

    private suspend fun <T> withRemote(default: T, block: (RemoteService) -> T): T {
        val srv = RemoteServiceManager.getInstanceOrNull() ?: return default
        return locked { block(srv) }
    }

    private suspend fun <T> locked(block: suspend () -> T): T =
        mutex.withLock { withContext(Dispatchers.IO) { block() } }

    /** 两侧 resource/version.json 不一致且留有热更包才投递；没留档只能提示用户重跑资源更新，不算失败 */
    private fun pushHotUpdateIfNeededLocked(srv: RemoteService): Boolean {
        val local = pathConfig.readDiskResourceVersion().orEmpty()
        val remote = runCatching { srv.coreResourceVersion }.getOrDefault("")
        if (local.isEmpty() || remote == local) return true
        val zip = lastUpdateZip
        if (!zip.isFile) {
            Timber.w("core resource '%s' != app '%s' and no update zip kept; re-run resource update to sync", remote, local)
            return true
        }
        val start = System.currentTimeMillis()
        val ok = sendFile(zip, "applyCoreHotUpdate") { srv.applyCoreHotUpdate(it) }
        Timber.i("core hot update push %s (%s) in %dms", if (ok) "done" else "FAILED", local, System.currentTimeMillis() - start)
        return ok
    }

    /** Manual config edits must reach separated Core storage even when resource versions match. */
    private fun pushResourceConfigsLocked(srv: RemoteService): Boolean {
        val root = File(pathConfig.rootDir)
        return runCatching {
            val paths = CoreResourceConfigs.paths(root)
            for (rel in paths) {
                if (!sendFile(File(root, rel), "putCoreFile") { srv.putCoreFile(rel, it) }) return false
            }
            srv.removeStaleCoreResourceConfigs(paths)
        }.onFailure { Timber.w(it, "Core config sync failed") }.getOrDefault(false)
    }

    private fun pushUserDataLocked(srv: RemoteService): Boolean {
        val root = File(pathConfig.rootDir)
        var ok = true
        var count = 0
        for (dir in CoreDataDir.USER_DATA_DIRS) {
            val top = File(root, dir)
            if (!top.isDirectory) continue
            top.walkTopDown().filter { it.isFile }.forEach { file ->
                val rel = file.relativeTo(root).invariantSeparatorsPath
                if (sendFile(file, "putCoreFile") { srv.putCoreFile(rel, it) }) count++ else ok = false
            }
        }
        Timber.d("core user data pushed: %d files ok=%s", count, ok)
        return ok
    }

    private inline fun sendFile(file: File, what: String, send: (ParcelFileDescriptor) -> Boolean): Boolean =
        runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use(send) }
            .onFailure { Timber.w(it, "%s failed: %s", what, file.name) }
            .getOrDefault(false)
}
