package com.aliothmoon.maameow.remote

import android.os.Process
import android.view.Surface
import com.aliothmoon.maameow.ITouchEventCallback
import com.aliothmoon.maameow.MaaCoreService
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.bridge.NativeBridgeLib
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.constant.DisplayMode
import com.aliothmoon.maameow.maa.InputControlUtils
import android.content.Intent
import com.aliothmoon.maameow.remote.internal.ActivityUtils
import com.aliothmoon.maameow.remote.internal.AppOpsHelper
import com.aliothmoon.maameow.remote.internal.PermissionGrantHelper
import com.aliothmoon.maameow.remote.internal.PowerController
import com.aliothmoon.maameow.remote.internal.PrimaryDisplayManager
import com.aliothmoon.maameow.remote.internal.ScreenManager
import com.aliothmoon.maameow.remote.internal.VirtualDisplayManager
import com.aliothmoon.maameow.third.FakeContext
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.Workarounds
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

class RemoteServiceImpl : RemoteService.Stub() {

    companion object {
        private const val TAG = "RemoteService"
        private const val HEARTBEAT_INTERVAL_MS = 5_000L
        private val trackedAudioPackages = ConcurrentHashMap.newKeySet<String>()

        @JvmStatic
        fun performEmergencyCleanup() {
            Ln.i("$TAG: performEmergencyCleanup triggered")
            runCatching {
                restoreTrackedAudioPackages()
                PowerController.destroy()
                ScreenManager.destroy()
                MaaCoreManager.destroy()
            }.onFailure {
                Ln.e("$TAG: Emergency cleanup failed: ${it.message}")
            }
        }

        private fun restoreTrackedAudioPackages() {
            trackedAudioPackages.forEach { packageName ->
                AppOpsHelper.resetAppops(packageName)
            }
        }
    }

    private val virtualDisplayMode = AtomicInteger(DisplayMode.PRIMARY)
    private val appPid = AtomicInteger(0)
    private val destroyed = AtomicBoolean(false)
    private var setup = false

    init {
        startHeartbeatWatchdog()
        Ln.i("$TAG: RemoteServiceImpl init, version: ${MaaCoreManager.maaService.GetVersion()}")
    }

    override fun destroy() {
        if (!destroyed.compareAndSet(false, true)) {
            return
        }
        Ln.i("$TAG: destroy()")
        InputControlUtils.setTouchCallback(null)
        performEmergencyCleanup()
        exitProcess(0)
    }

    override fun exit() = destroy()

    override fun getMaaCoreService(): MaaCoreService {
        return MaaCoreManager.maaService
    }

    override fun version(): String {
        val maaVersion = MaaCoreManager.MaaContext?.AsstGetVersion() ?: "Not loaded"
        return """
            ==== Build Info ====
            BridgeInfo: ${NativeBridgeLib.ping()}
            MaaCore Version: $maaVersion
            =====================
        """.trimIndent()
    }

    override fun pid(): Int = Process.myPid()

    override fun setup(userDir: String?, isDebug: Boolean): Boolean {
        if (!setup) {
            val ctx = MaaCoreManager.MaaContext ?: run {
                Ln.e("$TAG: setup failed - MaaContext is null")
                return false
            }
            Ln.i("NativeBridgeLib ping ${NativeBridgeLib.ping()}")
            with(ctx) {
                if (!AsstSetUserDir(userDir)) {
                    Ln.e("$TAG: setup failed - AsstSetUserDir($userDir) returned false")
                    return false
                }
                Ln.i("MaaCore ${AsstGetVersion()}")
                // TODO 使用fw control unit需要删除
                AsstSetStaticOption(3, "libbridge.so")
            }
            Workarounds.apply()
            setup = true
        }
        return true
    }

    override fun test(map: MutableMap<String, String>) {
    }

    override fun screencap(width: Int, height: Int) {
    }

    override fun setForcedDisplaySize(width: Int, height: Int): Boolean {
        return ScreenManager.setForcedDisplaySize(width, height)
    }

    override fun clearForcedDisplaySize(): Boolean {
        return ScreenManager.clearForcedDisplaySize()
    }

    override fun grantPermissions(request: PermissionGrantRequest): PermissionStateInfo {
        val packageName = request.packageName
        val uid = if (request.uid > 0) request.uid else runCatching {
            FakeContext.get().packageManager.getApplicationInfo(packageName, 0).uid
        }.getOrElse {
            Ln.w("$TAG: Failed to resolve UID for $packageName")
            request.uid
        }
        val p = request.permissions

        with(PermissionGrantHelper) {
            return PermissionStateInfo(
                accessibilityPermission = if (p and PermissionGrantRequest.PERM_ACCESSIBILITY != 0)
                    grantAccessibilityService(request.accessibilityServiceId) else false,
                floatingWindowPermission = if (p and PermissionGrantRequest.PERM_FLOATING_WINDOW != 0)
                    grantFloatingWindowPermission(packageName, uid) else false,
                notificationPermission = if (p and PermissionGrantRequest.PERM_NOTIFICATION != 0)
                    grantNotificationPermission(packageName, uid) else false,
                batteryOptimizationExempt = if (p and PermissionGrantRequest.PERM_BATTERY != 0)
                    grantBatteryOptimizationExemption(packageName) else false,
                storagePermission = if (p and PermissionGrantRequest.PERM_STORAGE != 0)
                    grantStoragePermission(packageName, uid) else false,
                backgroundUnrestricted = if (p and PermissionGrantRequest.PERM_BACKGROUND != 0)
                    grantBackgroundUnrestricted(packageName, uid) else false,
            )
        }
    }

    override fun setMonitorSurface(surface: Surface?) {
        Ln.i("$TAG: setMonitorSurface(${surface != null})")
        VirtualDisplayManager.setMonitorSurface(surface)
        NativeBridgeLib.setPreviewSurface(surface)
    }

    override fun setTouchCallback(callback: ITouchEventCallback?) {
        Ln.i("$TAG: setTouchCallback(${callback != null})")
        InputControlUtils.setTouchCallback(callback)
    }

    override fun touchDown(x: Int, y: Int) {
        if (virtualDisplayMode.get() == DisplayMode.PRIMARY) return
        val displayId = VirtualDisplayManager.getDisplayId()
        if (displayId != DefaultDisplayConfig.DISPLAY_NONE) {
            InputControlUtils.down(x, y, displayId)
        }
    }

    override fun touchMove(x: Int, y: Int) {
        if (virtualDisplayMode.get() == DisplayMode.PRIMARY) return
        val displayId = VirtualDisplayManager.getDisplayId()
        if (displayId != DefaultDisplayConfig.DISPLAY_NONE) {
            InputControlUtils.move(x, y, displayId)
        }
    }

    override fun touchUp(x: Int, y: Int) {
        if (virtualDisplayMode.get() == DisplayMode.PRIMARY) return
        val displayId = VirtualDisplayManager.getDisplayId()
        if (displayId != DefaultDisplayConfig.DISPLAY_NONE) {
            InputControlUtils.up(x, y, displayId)
        }
    }

    override fun setDisplayPower(on: Boolean) {
        PowerController.setDisplayPower(on)
    }

    override fun startVirtualDisplay(): Int {
        Ln.i("$TAG: startVirtualDisplay() ${virtualDisplayMode.get()}")
        return when (virtualDisplayMode.get()) {
            DisplayMode.PRIMARY -> PrimaryDisplayManager.start()
            DisplayMode.BACKGROUND -> VirtualDisplayManager.start()
            else -> DefaultDisplayConfig.DISPLAY_NONE
        }
    }

    override fun stopVirtualDisplay() {
        Ln.i("$TAG: stopVirtualDisplay() ${virtualDisplayMode.get()}")
        when (virtualDisplayMode.get()) {
            DisplayMode.PRIMARY -> PrimaryDisplayManager.stop()
            DisplayMode.BACKGROUND -> VirtualDisplayManager.stop()
        }
        restoreTrackedAudioPackages()
    }

    override fun setPlayAudioOpAllowed(packageName: String?, isAllowed: Boolean) {
        if (packageName.isNullOrBlank()) return
        val updated = AppOpsHelper.setPlayAudioOpAllowed(packageName, isAllowed)
        if (!updated) {
            Ln.w("$TAG: setPlayAudioOpAllowed skipped tracking update for $packageName")
            return
        }
        if (isAllowed) {
            trackedAudioPackages.remove(packageName)
        } else {
            trackedAudioPackages.add(packageName)
        }
    }

    override fun isAppAlive(packageName: String): Int {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("pidof", packageName))
            val exitCode = process.waitFor()
            val output = process.inputStream.bufferedReader().readText().trim()
            val errorOutput = process.errorStream.bufferedReader().readText().trim()
            when (exitCode) {
                0 if output.isNotEmpty() -> AppAliveStatus.ALIVE
                1 if output.isEmpty() && errorOutput.isEmpty() -> AppAliveStatus.DEAD
                else -> {
                    Ln.w(
                        "$TAG: isAppAlive unexpected result for $packageName: exitCode=$exitCode, stdout=$output, stderr=$errorOutput"
                    )
                    AppAliveStatus.UNKNOWN
                }
            }
        } catch (e: Exception) {
            Ln.w("isAppAlive check failed for $packageName", e)
            AppAliveStatus.UNKNOWN
        }
    }

    override fun heartbeat(pid: Int) {
        appPid.set(pid)
        Ln.i("$TAG: heartbeat received, app pid=$pid")
    }

    override fun isPackageInstalled(packageName: String): Boolean {
        return try {
            FakeContext.get().packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            Ln.w("$TAG: isPackageInstalled: $packageName not found")
            false
        }
    }

    override fun startActivity(intent: Intent): Boolean {
        return ActivityUtils.startActivity(intent)
    }

    override fun setVirtualDisplayResolution(width: Int, height: Int, dpi: Int) {
        Ln.i("$TAG: setVirtualDisplayResolution(${width}x${height}, dpi=$dpi)")
        VirtualDisplayManager.setResolution(width, height, dpi)
    }

    override fun setVirtualDisplayMode(mode: Int): Boolean {
        when (mode) {
            DisplayMode.PRIMARY -> {
                VirtualDisplayManager.stop()
                virtualDisplayMode.set(mode)
                return true
            }

            DisplayMode.BACKGROUND -> {
                PrimaryDisplayManager.stop()
                virtualDisplayMode.set(mode)
                return true
            }
        }
        return false
    }

    private fun startHeartbeatWatchdog() {
        Thread {
            while (!destroyed.get()) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                val pid = appPid.get()
                if (pid <= 0) {
                    continue
                }
                if (!File("/proc/$pid").exists()) {
                    Ln.w("$TAG: app process (pid=$pid) no longer exists, destroying remote service")
                    destroy()
                    return@Thread
                }
            }
        }.apply {
            name = "remote-heartbeat-watchdog"
            isDaemon = true
        }.start()
    }
}
