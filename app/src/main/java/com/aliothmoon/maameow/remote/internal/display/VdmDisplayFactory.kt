package com.aliothmoon.maameow.remote.internal.display

import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.Process
import android.view.Surface
import androidx.annotation.RequiresApi

/** 创建 VDM 独立显示，并协调关联、身份切换和失败清理。 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal object VdmDisplayFactory {
    fun create(
        name: String,
        width: Int,
        height: Int,
        dpi: Int,
        surface: Surface,
    ): VdmDisplaySession {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            "VDM requires Android 14"
        }
        check(Process.myUid() == Process.ROOT_UID || Process.myUid() == Process.SHELL_UID) {
            "VDM helper requires root or shell identity"
        }

        val address = CompanionAssociation.address()
        try {
            val association = VdmDiagnostics.stage("association") {
                CompanionAssociation.ensure(address)
            }
            return createForAssociation(
                address = address,
                associationId = association.id,
                name = name,
                width = width,
                height = height,
                dpi = dpi,
                surface = surface,
            )
        } catch (failure: IdentityRestoreError) {
            // 进程已请求终止，不能在有效身份未知时继续执行 Binder 或 shell 清理。
            throw failure
        } catch (failure: OwnerThreadAbandonedException) {
            // 清理责任已交给主线程迟到任务，不能在这里解除仍可能被使用的关联。
            throw failure
        } catch (failure: Throwable) {
            try {
                CompanionAssociation.remove(address)
            } catch (cleanupFailure: Throwable) {
                failure.addSuppressed(cleanupFailure)
            }
            throw failure
        }
    }

    private fun createForAssociation(
        address: String,
        associationId: Int,
        name: String,
        width: Int,
        height: Int,
        dpi: Int,
        surface: Surface,
    ): VdmDisplaySession {
        val resources = BinderIdentityRunner.onOwnerThread(
            action = {
                BinderIdentityRunner.withShellIdentity {
                    var createdDevice: VirtualDeviceHandle? = null
                    var createdDisplay: VirtualDisplay? = null
                    try {
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
                }
            },
            onAbandoned = { abandoned ->
                BinderIdentityRunner.withShellIdentity {
                    releaseCreatedDisplay(abandoned)
                }
                CompanionAssociation.remove(address)
            },
            onAbandonedFailure = {
                CompanionAssociation.remove(address)
            },
        )
        return VdmDisplaySession(
            address = address,
            virtualDevice = resources.virtualDevice,
            display = resources.display,
        )
    }

    private data class CreatedDisplay(
        val virtualDevice: VirtualDeviceHandle,
        val display: VirtualDisplay,
    )

    private fun releaseCreatedDisplay(resources: CreatedDisplay) {
        try {
            resources.display.setSurface(null)
            resources.display.release()
        } finally {
            resources.virtualDevice.close()
        }
    }
}
