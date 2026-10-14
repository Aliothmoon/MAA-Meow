package com.aliothmoon.maameow.manager

import android.os.Build
import android.os.IBinder
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.VdmShellService
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.remote.RemoteServiceImpl
import com.aliothmoon.maameow.remote.VdmShellServiceImpl
import com.aliothmoon.maameow.root.RootUserService.RemoteProcessServiceEntry

object RootRemoteServiceConnector : ProcessServiceConnectorBackend(SuSpawner) {

    override val backend = RemoteBackend.ROOT
    override val eventPrefix = "ROOT"
    override val processNameSuffix = "root_service"
    override val serviceClass: Class<out RemoteProcessServiceEntry> = RemoteServiceImpl::class.java
    override val logFileName = "root_launch_debug.log"
    override val keepRoot: Boolean get() = keepRootForInputInjection

    override val sidecarServiceClass: Class<out RemoteProcessServiceEntry>?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            VdmShellServiceImpl::class.java
        } else {
            null
        }
    override val sidecarProcessNameSuffix = "root_vdm_shell"
    override val sidecarLogFileName = "root_vdm_shell_launch_debug.log"

    override fun attachSidecar(primaryBinder: IBinder, sidecarBinder: IBinder) {
        RemoteService.Stub.asInterface(primaryBinder)?.attachVdmShellService(sidecarBinder)
            ?: error("Root RemoteService unavailable while attaching VDM sidecar")
    }

    override fun destroySidecar(sidecarBinder: IBinder) {
        VdmShellService.Stub.asInterface(sidecarBinder)?.destroy()
    }
}
