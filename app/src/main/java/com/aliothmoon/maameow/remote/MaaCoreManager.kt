package com.aliothmoon.maameow.remote

import com.aliothmoon.maameow.maa.MaaCoreLibrary
import com.aliothmoon.maameow.third.Ln
import com.sun.jna.Native

object MaaCoreManager {
    private const val TAG = "MaaCoreManager"

    private val contextLazy: Lazy<MaaCoreLibrary?> = lazy {
        runCatching {
            System.setProperty("jna.tmpdir", "/data/local/tmp")
            RemoteBootTrace.mark("MAA_LOAD_BEGIN", "jna.tmpdir=/data/local/tmp")
            Ln.i("$TAG: Loading MaaCore...")
            Native.load("MaaCore", MaaCoreLibrary::class.java).also {
                RemoteBootTrace.mark("MAA_LOAD_OK")
                Ln.i("$TAG: MaaCore loaded successfully")
            }
        }.onFailure {
            RemoteBootTrace.mark("MAA_LOAD_FAIL", "${it.javaClass.simpleName}: ${it.message}")
            Ln.e("$TAG: Failed to load MaaCore: ${it.message}")
            Ln.e(it.stackTraceToString())
        }.getOrNull()
    }

    val MaaContext: MaaCoreLibrary? by contextLazy

    // lazy 化：shutdown 清理触碰本对象不再连带触发加载链
    val maaService: MaaCoreServiceImpl by lazy { MaaCoreServiceImpl(MaaContext) }

    /**
     * setup() 记录的 userDir，用于 CreateInstance 后修复 debug 日志权限。
     */
    @Volatile
    var userDir: String? = null

    fun destroy() {
        // 未加载即跳过，退出路径不做 JNA 加载
        if (!contextLazy.isInitialized()) {
            Ln.i("$TAG: destroy() skipped, core never loaded")
            return
        }
        Ln.i("$TAG: destroy()")
        maaService.DestroyInstance()
    }
}
