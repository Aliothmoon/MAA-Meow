package com.aliothmoon.maameow.data.config

import android.content.Context
import com.aliothmoon.maameow.constant.MaaFiles.CACHE
import com.aliothmoon.maameow.constant.MaaFiles.DEBUG
import com.aliothmoon.maameow.constant.MaaFiles.MAA
import com.aliothmoon.maameow.constant.MaaFiles.RESOURCE
import com.aliothmoon.maameow.constant.MaaFiles.VERSION_FILE
import java.io.File

class MaaPathConfig(private val context: Context) {


    /** Maa 根目录 */
    val rootDir: String by lazy {
        File(context.getExternalFilesDir(null), MAA).absolutePath
    }

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

    /** version.json 路径 */
    private val versionFile: File
        get() = File(resourceDir, VERSION_FILE)

    /** 资源是否已就绪 */
    val isResourceReady: Boolean
        get() = versionFile.exists()

    fun ensureDirectories(): Boolean {
        return runCatching {
            File(rootDir).mkdirs()
            File(cacheDir).mkdirs()
            File(rootDir, ".nomedia").createNewFile()
        }.getOrDefault(false)
    }
}
