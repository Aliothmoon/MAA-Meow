package com.aliothmoon.maameow.remote

import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.view.Surface
import androidx.annotation.RequiresApi
import com.aliothmoon.maameow.VdmShellService
import com.aliothmoon.maameow.remote.internal.display.CompanionAssociation
import com.aliothmoon.maameow.remote.internal.display.VdmDisplayFactory
import com.aliothmoon.maameow.remote.internal.display.VdmDisplaySession
import com.aliothmoon.maameow.root.RootUserService.RemoteProcessServiceEntry
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.Workarounds
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

/**
 * 在 app_process 启动前永久降权的 VDM 辅助进程。
 *
 * CDM/VDM 资源均由此进程持有，使承担多种职责的 root 主服务无需在其他 Binder 或
 * native 任务运行期间切换 euid。
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class VdmShellServiceImpl : VdmShellService.Stub(), RemoteProcessServiceEntry {
    private val session = AtomicReference<VdmDisplaySession?>()
    private val owner = AtomicReference<IBinder?>()
    private val ownerDeath = IBinder.DeathRecipient {
        Ln.w("VDM shell owner died; releasing display and exiting")
        runCatching { releaseSession(removeAssociation = true) }
        exitProcess(0)
    }

    init {
        // shell 辅助进程拥有独立的 ActivityThread；三星系统创建 Context 时依赖完整配置。
        Workarounds.apply()
        check(Process.myUid() == Process.SHELL_UID) {
            "VDM sidecar must start permanently as shell (uid=${Process.myUid()})"
        }
        Ln.i("VDM shell sidecar started: uid=${Process.myUid()}")
    }

    override fun attachOwner(ownerBinder: IBinder) {
        requirePrivilegedOwner()
        val previous = owner.getAndSet(ownerBinder)
        if (previous === ownerBinder) return
        previous?.let { runCatching { it.unlinkToDeath(ownerDeath, 0) } }
        ownerBinder.linkToDeath(ownerDeath, 0)
    }

    override fun cleanupStale(userId: Int) {
        requirePrivilegedOwner()
        CompanionAssociation.cleanupStale(userId)
    }

    @Synchronized
    override fun createDisplay(
        name: String,
        width: Int,
        height: Int,
        dpi: Int,
        surface: Surface,
        userId: Int,
    ): Int {
        requirePrivilegedOwner()
        var created: VdmDisplaySession? = null
        return try {
            session.getAndSet(null)?.closeForRestart()
            val candidate = VdmDisplayFactory.create(
                userId = userId,
                name = name,
                width = width,
                height = height,
                dpi = dpi,
                surface = surface,
            )
            created = candidate
            candidate.awaitReady()
            session.set(candidate)
            candidate.display.display.displayId
        } catch (failure: Throwable) {
            runCatching { created?.close() }
                .onFailure { failure.addSuppressed(it) }
            Ln.e("VDM shell display creation failed", failure)
            throw IllegalStateException(failure.message ?: "VDM display creation failed", failure)
        }
    }

    @Synchronized
    override fun closeDisplay(removeAssociation: Boolean) {
        requirePrivilegedOwner()
        releaseSession(removeAssociation)
    }

    override fun destroy() {
        runCatching { releaseSession(removeAssociation = true) }
            .onFailure { Ln.w("VDM shell shutdown cleanup failed: ${it.message}") }
        exitProcess(0)
    }

    @Synchronized
    private fun releaseSession(removeAssociation: Boolean) {
        session.getAndSet(null)?.let { active ->
            if (removeAssociation) active.close() else active.closeForRestart()
        }
    }

    private fun requirePrivilegedOwner() {
        val caller = Binder.getCallingUid()
        check(caller == Process.ROOT_UID || caller == Process.SHELL_UID) {
            "VDM sidecar rejected caller uid=$caller"
        }
    }
}
