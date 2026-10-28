package com.aliothmoon.maameow.remote.internal

import android.os.Build
import androidx.annotation.VisibleForTesting
import com.aliothmoon.maameow.constant.AndroidVersions
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.wrappers.DisplayControl
import com.aliothmoon.maameow.third.wrappers.ServiceManager
import com.aliothmoon.maameow.third.wrappers.SurfaceControl
import com.aliothmoon.maameow.utils.device.Manufacturer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object PowerController {
    private const val TAG = "PowerController"
    private const val USER_ACTIVITY_INTERVAL_MS = 4_000L

    // 不落盘：进程被杀后由用户唤醒时系统恢复面板
    private val poweredOff = AtomicBoolean(false)

    private val keepAliveDisplayId = AtomicInteger(DefaultDisplayConfig.DISPLAY_NONE)
    private val keepAliveRunning = AtomicBoolean(false)

    // 判不出按醒着处理，宁可点亮
    @VisibleForTesting
    internal var systemAwake: () -> Boolean = {
        runCatching { ServiceManager.getPowerManager().isScreenOnOrNull(0) }.getOrNull() ?: true
    }

    fun setDisplayPower(on: Boolean): Boolean {
        poweredOff.set(!on)
        return setDisplayPowerInternal(on)
    }

    private fun setDisplayPowerInternal(on: Boolean): Boolean {
        var applyToMultiPhysicalDisplays =
            Build.VERSION.SDK_INT >= AndroidVersions.API_29_ANDROID_10

        if (applyToMultiPhysicalDisplays
            && Build.VERSION.SDK_INT >= AndroidVersions.API_34_ANDROID_14 && Manufacturer.isHonorBrand()
            && SurfaceControl.hasGetBuildInDisplayMethod()
        ) {
            applyToMultiPhysicalDisplays = false
        }

        val mode: Int =
            if (on) SurfaceControl.POWER_MODE_NORMAL else SurfaceControl.POWER_MODE_OFF
        if (applyToMultiPhysicalDisplays) {
            val useDisplayControl =
                Build.VERSION.SDK_INT >= AndroidVersions.API_34_ANDROID_14 && !SurfaceControl.hasGetPhysicalDisplayIdsMethod()

            val physicalDisplayIds =
                if (useDisplayControl) DisplayControl.getPhysicalDisplayIds() else SurfaceControl.getPhysicalDisplayIds()
            if (physicalDisplayIds == null) {
                Ln.e("Could not get physical display ids")
                return false
            }

            var allOk = true
            for (physicalDisplayId in physicalDisplayIds) {
                val binder = if (useDisplayControl) DisplayControl.getPhysicalDisplayToken(
                    physicalDisplayId
                ) else SurfaceControl.getPhysicalDisplayToken(physicalDisplayId)
                allOk = allOk and SurfaceControl.setDisplayPowerMode(binder, mode)
            }
            return allOk
        }

        val d = SurfaceControl.getBuiltInDisplay()
        if (d == null) {
            Ln.e("Could not get built-in display")
            return false
        }
        return SurfaceControl.setDisplayPowerMode(d, mode)
    }

    fun startUserActivityKeepAlive(displayId: Int) {
        keepAliveDisplayId.set(displayId)
        if (!keepAliveRunning.compareAndSet(false, true)) return
        Thread {
            Ln.i("$TAG: userActivity keep-alive started, displayId=$displayId")
            while (true) {
                val id = keepAliveDisplayId.get()
                if (id == DefaultDisplayConfig.DISPLAY_NONE) break
                try {
                    Thread.sleep(USER_ACTIVITY_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
                val currentId = keepAliveDisplayId.get()
                if (currentId == DefaultDisplayConfig.DISPLAY_NONE) break
                runCatching { ServiceManager.getPowerManager().userActivity(currentId) }
                    .onFailure { Ln.e("$TAG: userActivity failed", it) }
            }
            keepAliveRunning.set(false)
            Ln.i("$TAG: userActivity keep-alive stopped")
        }.apply {
            name = "power-user-activity-keepalive"
            isDaemon = true
        }.start()
    }

    fun stopUserActivityKeepAlive() {
        keepAliveDisplayId.set(DefaultDisplayConfig.DISPLAY_NONE)
    }

    fun destroy() {
        stopUserActivityKeepAlive()
        if (poweredOff.get()) {
            // 系统已休眠就别点亮，下次唤醒由系统恢复面板
            if (!systemAwake()) {
                Ln.i("$TAG: System asleep, leave display power to the system")
                poweredOff.set(false)
                return
            }
            Ln.i("$TAG: Emergency recovering screen power...")
            runCatching {
                setDisplayPower(true)
            }.onFailure {
                Ln.e("$TAG: Failed to recover screen power: ${it.message}")
            }
        }
    }
}
