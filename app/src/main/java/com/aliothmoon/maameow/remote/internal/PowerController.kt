package com.aliothmoon.maameow.remote.internal

import android.os.Build
import com.aliothmoon.maameow.constant.AndroidVersions
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.wrappers.DisplayControl
import com.aliothmoon.maameow.third.wrappers.ServiceManager
import com.aliothmoon.maameow.third.wrappers.SurfaceControl
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object PowerController {
    private const val TAG = "PowerController"
    private const val USER_ACTIVITY_INTERVAL_MS = 4_000L
    private const val RESTORE_ATTEMPTS = 3
    private const val RESTORE_RETRY_MS = 150L

    // 不落盘：进程被杀后由用户唤醒时系统恢复面板
    private val poweredOff = AtomicBoolean(false)

    private val keepAliveDisplayId = AtomicInteger(DefaultDisplayConfig.DISPLAY_NONE)
    private val keepAliveRunning = AtomicBoolean(false)

    @Synchronized
    fun setDisplayPower(on: Boolean): Boolean {
        if (!on) {
            // 多屏设备可能只关掉部分面板，失败时也须保留恢复标记。
            poweredOff.set(true)
            return setDisplayPowerInternal(false)
        }
        // AIDL 是 oneway；底层返回值在提权进程内处理，不依赖客户端推断成功。
        repeat(RESTORE_ATTEMPTS) { attempt ->
            val restored = runCatching { setDisplayPowerInternal(true) }
                .onFailure { Ln.e("$TAG: Failed to restore screen power", it) }
                .getOrDefault(false)
            if (restored) {
                poweredOff.set(false)
                return true
            }
            if (attempt < RESTORE_ATTEMPTS - 1) {
                try {
                    Thread.sleep(RESTORE_RETRY_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
        }
        Ln.e("$TAG: Screen power restore failed; keeping emergency recovery pending")
        return false
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
        if (poweredOff.get()) {
            Ln.i("$TAG: Emergency recovering screen power...")
            runCatching {
                setDisplayPower(true)
            }.onFailure {
                Ln.e("$TAG: Failed to recover screen power: ${it.message}")
            }
        }
    }
}
