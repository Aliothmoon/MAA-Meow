package com.aliothmoon.maameow.data.config

import android.content.Context
import android.os.Environment
import com.aliothmoon.maameow.constant.MaaFiles.APP_VERSION_FILE
import com.aliothmoon.maameow.constant.MaaFiles.ASSET_VERSION_FILE
import com.aliothmoon.maameow.constant.MaaFiles.CACHE
import com.aliothmoon.maameow.constant.MaaFiles.DEBUG
import com.aliothmoon.maameow.constant.MaaFiles.MAA
import com.aliothmoon.maameow.constant.MaaFiles.OVERRIDES
import com.aliothmoon.maameow.constant.MaaFiles.RESOURCE
import com.aliothmoon.maameow.constant.MaaFiles.SCREENSHOTS
import com.aliothmoon.maameow.constant.MaaFiles.VERSION_FILE
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.CoreDataLocation
import com.aliothmoon.maameow.remote.CoreDataDir
import com.aliothmoon.maameow.remote.ResourceFiles
import timber.log.Timber
import java.io.File

class MaaPathConfig(
    private val context: Context,
    private val appSettings: AppSettingsManager,
) {

    companion object {
        fun toCorePath(path: String, appRoot: String, coreRoot: String): String = when {
            path == appRoot -> coreRoot
            path.startsWith(appRoot + File.separator) -> coreRoot + path.substring(appRoot.length)
            else -> path
        }
    }

    /** 进程内固定；改了设置要重启（MaaCore 状态本就不可回滚） */
    val coreLocation: CoreDataLocation by lazy { appSettings.coreDataLocation.value }

    /** core 在 /data/local/tmp，App 读不到 */
    val isCoreSeparated: Boolean
        get() = coreLocation == CoreDataLocation.LOCAL_TMP

    val coreRootDir: String
        get() = if (isCoreSeparated) CoreDataDir.ROOT else rootDir

    /** core 侧 debug/，asst.log / logcat / 截图落这里 */
    val coreDebugDir: String
        get() = File(coreRootDir, DEBUG).absolutePath

    val coreDebugScreenshotsDir: String
        get() = File(coreDebugDir, SCREENSHOTS).absolutePath

    fun toCorePath(path: String): String =
        if (isCoreSeparated) toCorePath(path, rootDir, CoreDataDir.ROOT) else path

    /**
     * Maa 根目录
     * 存储没就绪时 getExternalFilesDir 返回 null，File(null, "Maa") 是 /Maa 且会被 lazy 缓存住；
     * 退回它本该返回的路径，存储就绪后照常可用
     */
    val rootDir: String by lazy {
        val ext = context.getExternalFilesDir(null)
            ?: expectedExternalFilesDir().also { Timber.w("getExternalFilesDir returned null, falling back to $it") }
        Timber.i("context.getExternalFilesDir $ext")
        File(ext, MAA).absolutePath
    }

    /** 只拼路径不碰存储，取不到真实目录时用 */
    @Suppress("DEPRECATION")
    private fun expectedExternalFilesDir() =
        File(Environment.getExternalStorageDirectory(), "Android/data/${context.packageName}/files")

    /** 资源目录 */
    val resourceDir: String by lazy {
        File(rootDir, RESOURCE).absolutePath
    }

    /** 缓存目录（热更新资源） */
    val cacheDir: String by lazy {
        File(rootDir, CACHE).absolutePath
    }

    /** 缓存资源目录（cache/resource/） */
    val cacheResourceDir: String by lazy {
        File(cacheDir, RESOURCE).absolutePath
    }

    /**
     * 全球服资源目录（resource/global/{clientType}/resource/）
     * 对标 WPF: Path.Combine(mainRes, "global", clientType, "resource")
     */
    fun globalResourceDir(clientType: String): File {
        return File(resourceDir, "global/$clientType/$RESOURCE")
    }

    /**
     * 全球服缓存资源目录（cache/resource/global/{clientType}/resource/）
     * 对标 WPF: Path.Combine(mainCacheRes, "global", clientType, "resource")
     */
    fun globalCacheResourceDir(clientType: String): File {
        return File(cacheResourceDir, "global/$clientType/$RESOURCE")
    }

    /** 调试日志目录 */
    val debugDir: String by lazy {
        File(rootDir, DEBUG).absolutePath
    }

    /** 调试截图目录（debug/screenshots/） */
    val debugScreenshotsDir: String by lazy {
        File(debugDir, SCREENSHOTS).absolutePath
    }

    /**
     * Android 特化覆盖目录（传给 AsstLoadResource 的 parentDir）
     * 对应磁盘路径：{rootDir}/overrides/
     * 加载链末位，优先级最高
     */
    val overridesDir: String by lazy {
        File(rootDir, OVERRIDES).absolutePath
    }

    /**
     * 覆盖用 tasks.json 文件路径
     * {rootDir}/overrides/resource/tasks/tasks.json
     */
    val overrideTasksFile: File
        get() = File(rootDir, "$OVERRIDES/$RESOURCE/tasks/tasks.json")

    /** version.json 路径 */
    private val versionFile: File
        get() = File(resourceDir, VERSION_FILE)

    private val bundledResourceVersion: String? by lazy {
        try {
            context.assets.open(ASSET_VERSION_FILE).bufferedReader().use { ResourceFiles.readLastUpdated(it.readText()) }
        } catch (e: Exception) {
            Timber.w(e, "读取内置资源版本失败")
            null
        }
    }

    /** 资源是否已就绪（资源存在 且 APP 版本匹配 且 内置资源不比磁盘新） */
    val isResourceReady: Boolean
        get() = versionFile.exists()
                && isAppVersionCurrent()
//                && !isBundledResourceNewer()

    private fun isBundledResourceNewer(): Boolean {
        val bundled = bundledResourceVersion ?: return false
        val disk = readDiskResourceVersion() ?: return false
        return ResourceVersionHelper.compareVersions(bundled, disk) > 0
    }

    /** 磁盘资源版本戳（version.json 的 last_updated），缺失或解析失败为 null */
    fun readDiskResourceVersion(): String? = ResourceFiles.readLastUpdated(versionFile)

    private fun isAppVersionCurrent(): Boolean {
        val file = File(rootDir, APP_VERSION_FILE)
        if (!file.exists()) return false
        return try {
            file.readText().trim().toLong() == appVersionCode
        } catch (_: Exception) {
            false
        }
    }

    val appVersionCode: Long by lazy {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    }

    fun markAppVersion() {
        File(rootDir, APP_VERSION_FILE).writeText(appVersionCode.toString())
    }

    fun ensureDirectories(): Boolean {
        return runCatching {
            File(rootDir).mkdirs()
            File(cacheDir).mkdirs()
            File(rootDir, ".nomedia").createNewFile()
        }.getOrDefault(false)
    }
}
