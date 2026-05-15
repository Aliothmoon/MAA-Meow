package com.aliothmoon.maameow.remote.internal

import android.os.Build
import com.aliothmoon.maameow.constant.AndroidVersions
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.wrappers.DisplayControl
import com.aliothmoon.maameow.third.wrappers.ServiceManager
import com.aliothmoon.maameow.third.wrappers.SurfaceControl
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object PowerController {
    private const val TAG = "PowerController"
    private const val USER_ACTIVITY_INTERVAL_MS = 4_000L
    private val file = File("/data/local/tmp/maa_power_off_flag")

    private val keepAliveDisplayId = AtomicInteger(DefaultDisplayConfig.DISPLAY_NONE)
    private val keepAliveRunning = AtomicBoolean(false)

    var flag: Boolean
        get() = runCatching { file.exists() }.getOrDefault(false)
        set(value) {
            runCatching {
                if (value) {
                    file.parentFile?.mkdirs()
                    file.createNewFile()
                } else {
                    file.delete()
                }
            }
        }

    fun setDisplayPower(on: Boolean): Boolean {
        flag = !on
        return setDisplayPowerInternal(on)
    }

    private fun setDisplayPowerInternal(on: Boolean): Boolean {
        var applyToMultiPhysicalDisplays =
            Build.VERSION.SDK_INT >= AndroidVersions.API_29_ANDROID_10

        if (applyToMultiPhysicalDisplays
            && Build.VERSION.SDK_INT >= AndroidVersions.API_34_ANDROID_14 && Build.BRAND.equals(
                "honor",
                ignoreCase = true
            )
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
        if (flag) {
            Ln.i("$TAG: Emergency recovering screen power...")
            runCatching {
                setDisplayPower(true)
            }.onFailure {
                Ln.e("$TAG: Failed to recover screen power: ${it.message}")
            }
        }
    }
}