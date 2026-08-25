package com.aliothmoon.maameow.manager

import android.content.Context
import android.os.Build
import android.os.Process
import com.aliothmoon.maameow.constant.MaaFiles
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 远程服务绑定链路诊断日志（App 进程侧）。
 *
 * 写入 {externalFilesDir}/Maa/debug/service_bind_debug.log（与 root_launch_debug.log 同目录），
 * 按时间线记录 bind → CONNECTING → onServiceConnected → BINDER_CONNECTED 以及 onError / binderDied /
 * getInstance 超时。
 *
 * 当服务进程在启动阶段静默死亡、binder 永不回投时，文件会停在 CONNECTING 行而无任何终态行，并由看门狗
 * 补一条 STUCK 标记，直接定位"流程卡在 IPC 边界、服务进程未起来"——再配合服务进程侧的
 * service_boot_debug.log 即可判断是 native 加载崩溃还是连接后才死亡。
 *
 * 设计为 object：RemoteServiceManager / 各连接器均为单例 object，统一静态访问，
 * 不必把引用穿透各处。init() 前调用一律 no-op。路径直接由 Context 推导，不依赖 MaaPathConfig。
 */
object ServiceBootLogger {

    private const val FILE_NAME = "service_bind_debug.log"
    private const val MAX_BYTES = 512 * 1024L

    private val timeFmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")
    private val lock = Any()

    @Volatile
    private var debugDir: File? = null

    fun init(context: Context) {
        synchronized(lock) {
            val dir = File(context.getExternalFilesDir(null), "${MaaFiles.MAA}/${MaaFiles.DEBUG}")
            runCatching { dir.mkdirs() }
            debugDir = dir
        }
        event(
            "SESSION",
            "app start pid=${Process.myPid()} device=${Build.MANUFACTURER} ${Build.MODEL} api=${Build.VERSION.SDK_INT}"
        )
    }

    fun event(stage: String, msg: String = "") {
        val dir = debugDir ?: return
        synchronized(lock) {
            runCatching {
                val file = File(dir, FILE_NAME)
                rotateIfNeeded(file, dir)
                val time = Instant.now().atZone(ZoneId.systemDefault()).format(timeFmt)
                val line = if (msg.isEmpty()) "$time  $stage\n" else "$time  $stage  $msg\n"
                file.appendText(line)
            }.onFailure { Timber.w(it, "ServiceBootLogger write failed") }
        }
    }

    private fun rotateIfNeeded(file: File, dir: File) {
        if (file.exists() && file.length() > MAX_BYTES) {
            val bak = File(dir, "$FILE_NAME.1")
            if (bak.exists()) bak.delete()
            file.renameTo(bak)
        }
    }
}
