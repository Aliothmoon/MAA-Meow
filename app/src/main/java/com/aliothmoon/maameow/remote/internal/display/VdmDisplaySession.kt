package com.aliothmoon.maameow.remote.internal.display

import android.hardware.display.VirtualDisplay
import android.os.Build
import android.view.Surface
import androidx.annotation.RequiresApi
import java.util.concurrent.atomic.AtomicBoolean

/** 一个已经创建并校验过的 VDM VirtualDevice/VirtualDisplay 资源会话。 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class VdmDisplaySession internal constructor(
    private val address: String,
    private val virtualDevice: VirtualDeviceHandle,
    val display: VirtualDisplay,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    /** Surface 已在创建交易中挂载；此处只等待显示进入可用状态。 */
    fun awaitReady() {
        check(!closed.get()) { "Independent display is already closed" }
        VdmDiagnostics.awaitDisplayOn(display.display)
    }

    fun detachSurface() {
        if (!closed.get()) display.setSurface(null)
    }

    override fun close() = release(removeAssociation = true)

    /** 重建显示时保留关联及其角色授权，供替换后的 VDM 设备复用。 */
    fun closeForRestart() = release(removeAssociation = false)

    private fun release(removeAssociation: Boolean) {
        if (!closed.compareAndSet(false, true)) return

        var failure: Throwable? = null
        try {
            BinderIdentityRunner.onOwnerThread(
                action = {
                    BinderIdentityRunner.withShellIdentity {
                        try {
                            display.setSurface(null)
                            display.release()
                        } finally {
                            virtualDevice.close()
                        }
                    }
                },
                onAbandoned = {
                    if (removeAssociation) CompanionAssociation.remove(address)
                },
                onAbandonedFailure = {
                    if (removeAssociation) CompanionAssociation.remove(address)
                },
            )
        } catch (t: Throwable) {
            failure = t
        }

        // A timed-out owner task still needs the association until its delayed cleanup runs.
        if (removeAssociation && failure !is OwnerThreadAbandonedException) {
            try {
                CompanionAssociation.remove(address)
            } catch (cleanupFailure: Throwable) {
                if (failure == null) {
                    failure = cleanupFailure
                } else {
                    failure.addSuppressed(cleanupFailure)
                }
            }
        }

        failure?.let { throw it }
    }
}
