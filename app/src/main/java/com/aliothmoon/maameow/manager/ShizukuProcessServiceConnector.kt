package com.aliothmoon.maameow.manager

import android.os.Build
import android.os.IBinder
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.VdmShellService
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.remote.RemoteServiceImpl
import com.aliothmoon.maameow.remote.VdmShellServiceImpl
import com.aliothmoon.maameow.root.RootUserService.RemoteProcessServiceEntry

/** Shizuku 唯一路径：newProcess 拉起自研 starter，见 [ShizukuSpawner] */
object ShizukuProcessServiceConnector : ProcessServiceConnectorBackend(ShizukuSpawner) {

    override val backend = RemoteBackend.SHIZUKU
    override val eventPrefix = "SHIZUKU"
    override val processNameSuffix = "shizuku_service"
    override val serviceClass: Class<out RemoteProcessServiceEntry> = RemoteServiceImpl::class.java
    override val logFileName = "shizuku_launch_debug.log"

    // 进程侧失败由 alive 轮询秒级暴露，超时只兜"进程活着但不回投"；实测全链 <1s，留 10 倍余量
    override val spawnTimeoutMs: Long = 10_000L

    // root 模式 Shizuku 与 root 后端同规则；adb 模式下 launcher 已是 shell，标志自动无效
    override val keepRoot: Boolean get() = keepRootForInputInjection

    override val sidecarServiceClass: Class<out RemoteProcessServiceEntry>?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            VdmShellServiceImpl::class.java
        } else {
            null
        }
    override val sidecarProcessNameSuffix = "shizuku_vdm_shell"
    override val sidecarLogFileName = "shizuku_vdm_shell_launch_debug.log"

    override fun attachSidecar(primaryBinder: IBinder, sidecarBinder: IBinder) {
        RemoteService.Stub.asInterface(primaryBinder)?.attachVdmShellService(sidecarBinder)
            ?: error("Shizuku RemoteService unavailable while attaching VDM sidecar")
    }

    override fun destroySidecar(sidecarBinder: IBinder) {
        VdmShellService.Stub.asInterface(sidecarBinder)?.destroy()
    }
}
