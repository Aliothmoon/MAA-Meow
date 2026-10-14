package com.aliothmoon.maameow.remote.internal.display

import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.Process
import android.system.Os
import android.view.Surface
import androidx.annotation.RequiresApi

/** 在永久保持 shell 身份的辅助进程中创建 VDM 独立显示，并协调关联和失败清理。 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal object VdmDisplayFactory {
    fun create(
        userId: Int,
        name: String,
        width: Int,
        height: Int,
        dpi: Int,
        surface: Surface,
    ): VdmDisplaySession {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            "VDM requires Android 14"
        }
        check(Process.myUid() == Process.SHELL_UID && Os.geteuid() == Process.SHELL_UID) {
            "VDM owner must be permanently shell"
        }

        val address = CompanionAssociation.address(userId)
        try {
            val association = VdmDiagnostics.stage("association") {
                CompanionAssociation.ensure(address, userId)
            }
            return createForAssociation(
                address = address,
                userId = userId,
                associationId = association.id,
                name = name,
                width = width,
                height = height,
                dpi = dpi,
                surface = surface,
            )
        } catch (failure: Throwable) {
            try {
                CompanionAssociation.remove(address, userId)
            } catch (cleanupFailure: Throwable) {
                failure.addSuppressed(cleanupFailure)
            }
            throw failure
        }
    }

    private fun createForAssociation(
        address: String,
        userId: Int,
        associationId: Int,
        name: String,
        width: Int,
        height: Int,
        dpi: Int,
        surface: Surface,
    ): VdmDisplaySession {
        var createdDevice: VirtualDeviceHandle? = null
        var createdDisplay: VirtualDisplay? = null
        val resources = try {
            val device = VdmDiagnostics.stage("create_device") {
                AndroidVirtualDeviceApi.createVirtualDevice(associationId)
            }
            createdDevice = device
            val display = VdmDiagnostics.stage("create_display") {
                AndroidVirtualDeviceApi.createVirtualDisplay(
                    device = device,
                    name = name,
                    width = width,
                    height = height,
                    dpi = dpi,
                    surface = surface,
                )
            }
            createdDisplay = display
            VdmDiagnostics.awaitIndependentGroup(display.display)
            CreatedDisplay(device, display)
        } catch (failure: Throwable) {
            try {
                createdDisplay?.let { display ->
                    display.setSurface(null)
                    display.release()
                }
            } catch (cleanupFailure: Throwable) {
                failure.addSuppressed(cleanupFailure)
            }
            createdDevice?.let { device ->
                try {
                    device.close()
                } catch (cleanupFailure: Throwable) {
                    failure.addSuppressed(cleanupFailure)
                }
            }
            throw failure
        }
        return VdmDisplaySession(
            address = address,
            userId = userId,
            virtualDevice = resources.virtualDevice,
            display = resources.display,
        )
    }

    private data class CreatedDisplay(
        val virtualDevice: VirtualDeviceHandle,
        val display: VirtualDisplay,
    )
}
