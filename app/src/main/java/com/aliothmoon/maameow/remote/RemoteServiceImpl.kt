package com.aliothmoon.maameow.remote

import android.os.Build
import android.os.Process
import android.view.Display
import android.view.Surface
import com.aliothmoon.maameow.MaaCoreService
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.bridge.NativeBridgeLib
import com.aliothmoon.maameow.constant.AndroidVersions
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.constant.DisplayMode
import com.aliothmoon.maameow.maa.InputControlUtils
import com.aliothmoon.maameow.maa.MaaCoreLibrary
import com.aliothmoon.maameow.remote.internal.PrimaryDisplayManager
import com.aliothmoon.maameow.remote.internal.VirtualDisplayManager
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.Workarounds
import com.aliothmoon.maameow.third.wrappers.DisplayControl
import com.aliothmoon.maameow.third.wrappers.ServiceManager
import com.aliothmoon.maameow.third.wrappers.SurfaceControl
import com.sun.jna.Native
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

class RemoteServiceImpl : RemoteService.Stub() {

    companion object {
        private const val TAG = "RemoteService"

        val MaaContext: MaaCoreLibrary? = run {
            runCatching {
                System.setProperty("jna.tmpdir", "/data/local/tmp")
                Ln.i("$TAG: Loading MaaCore...")
                Native.load("MaaCore", MaaCoreLibrary::class.java).also {
                    Ln.i("$TAG: MaaCore loaded successfully")
                }
            }.onFailure {
                Ln.e("$TAG: Failed to load MaaCore: ${it.message}")
                Ln.e(it.stackTraceToString())
            }.getOrNull()
        }

        private val maaService = MaaCoreServiceImpl(MaaContext)
    }


    private val _flag = File("/data/local/tmp/maa_screen_flag")

    private var flag: Boolean
        get() = runCatching { _flag.exists() }.onFailure {
            Ln.e("$TAG: Failed to check if alive flag file exists: ${it.message}")
            Ln.e(it.stackTraceToString())
        }.getOrDefault(false)
        set(value) {
            runCatching {
                if (value) {
                    _flag.parentFile?.mkdirs()
                    _flag.createNewFile()
                } else {
                    _flag.delete()
                }
            }.onFailure {
                Ln.e("$TAG: Failed to set alive flag file: ${it.message}")
                Ln.e(it.stackTraceToString())
            }
        }

    private val virtualDisplayMode = AtomicInteger(0)

    private var setup = false

    override fun destroy() {
        Ln.i("$TAG: destroy()")
        maaService.DestroyInstance()
        if (flag) {
            clearForcedDisplaySize()
        }
        exitProcess(0)
    }

    override fun exit() = destroy()

    override fun getMaaCoreService(): MaaCoreService {
        return maaService
    }

    override fun version(): String {
        val maaVersion = MaaContext?.AsstGetVersion() ?: "Not loaded"
        return """
            ==== Build Info ====
            BridgeInfo: ${NativeBridgeLib.ping()}
            MaaCore Version: $maaVersion
            =====================
        """.trimIndent()
    }

    override fun pid(): Int = Process.myPid()

    override fun setup(userDir: String?, isDebug: Boolean): Boolean {
        val result = NativeBridgeLib.ping()
        if (!setup) {
            val ctx = MaaContext ?: run {
                Ln.e("$TAG: setup failed - MaaContext is null")
                return false
            }
            Ln.i("NativeBridgeLib ping $result")
            with(ctx) {
                if (!AsstSetUserDir(userDir)) {
                    Ln.e("$TAG: setup failed - AsstSetUserDir($userDir) returned false")
                    return false
                }
                Ln.i("MaaCore ${AsstGetVersion()}")
                if (!AsstSetStaticOption(3, "libbridge.so")) {
                    Ln.e("$TAG: setup failed - AsstSetStaticOption(3, libbridge.so) returned false")
                    return false
                }
            }
            Workarounds.apply()
        }
        return true
    }

    override fun test(map: MutableMap<String, String>) {
    }

    override fun screencap(width: Int, height: Int) {

    }

    override fun setForcedDisplaySize(width: Int, height: Int): Boolean {
        flag = true
        return ServiceManager.getWindowManager()
            .setForcedDisplaySize(Display.DEFAULT_DISPLAY, width, height)
    }

    override fun clearForcedDisplaySize(): Boolean {
        flag = false
        return ServiceManager.getWindowManager().clearForcedDisplaySize(Display.DEFAULT_DISPLAY)
    }

    override fun grantPermissions(request: PermissionGrantRequest): PermissionStateInfo {
        val packageName = request.packageName
        val uid = request.uid
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
            )
        }
    }

    override fun setMonitorSurface(surface: Surface?) {
        Ln.i("$TAG: setMonitorSurface(${surface != null})")
        VirtualDisplayManager.setMonitorSurface(surface)
    }

    override fun touchDown(x: Int, y: Int) {
        if (virtualDisplayMode.get() == DisplayMode.PRIMARY) {
            return
        }
        val displayId = VirtualDisplayManager.getDisplayId()
        if (displayId != DefaultDisplayConfig.DISPLAY_NONE) {
            InputControlUtils.down(x, y, displayId)
        }
    }

    override fun touchMove(x: Int, y: Int) {
        if (virtualDisplayMode.get() == DisplayMode.PRIMARY) {
            return
        }
        val displayId = VirtualDisplayManager.getDisplayId()
        if (displayId != DefaultDisplayConfig.DISPLAY_NONE) {
            InputControlUtils.move(x, y, displayId)
        }
    }

    override fun touchUp(x: Int, y: Int) {
        if (virtualDisplayMode.get() == DisplayMode.PRIMARY) {
            return
        }
        val displayId = VirtualDisplayManager.getDisplayId()
        if (displayId != DefaultDisplayConfig.DISPLAY_NONE) {
            InputControlUtils.up(x, y, displayId)
        }
    }

    override fun setDisplayPower(on: Boolean) {
        setDisplayPowerInternal(on)
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
            // Workaround for Honor devices with Android 14:
            //  - <https://github.com/Genymobile/scrcpy/issues/4823>
            //  - <https://github.com/Genymobile/scrcpy/issues/4943>
            applyToMultiPhysicalDisplays = false
        }

        val mode: Int =
            if (on) SurfaceControl.POWER_MODE_NORMAL else SurfaceControl.POWER_MODE_OFF
        if (applyToMultiPhysicalDisplays) {
            // On Android 14, these internal methods have been moved to DisplayControl
            val useDisplayControl =
                Build.VERSION.SDK_INT >= AndroidVersions.API_34_ANDROID_14 && !SurfaceControl.hasGetPhysicalDisplayIdsMethod()

            // Change the power mode for all physical displays
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

        // Older Android versions, only 1 display
        val d = SurfaceControl.getBuiltInDisplay()
        if (d == null) {
            Ln.e("Could not get built-in display")
            return false
        }
        return SurfaceControl.setDisplayPowerMode(d, mode)
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
    }

    override fun setPlayAudioOpAllowed(packageName: String?, isAllowed: Boolean) {
        if (packageName.isNullOrBlank()) return
        val op = if (isAllowed) "allow" else "deny"
        try {
            val process = Runtime.getRuntime()
                .exec(arrayOf("sh", "-c", "appops set $packageName PLAY_AUDIO $op"))
            val exitCode = process.waitFor()
            Ln.i("$TAG: appops set $packageName PLAY_AUDIO $op -> exitCode=$exitCode")
        } catch (e: Exception) {
            Ln.e("$TAG: setPlayAudioOpAllowed failed: ${e.message}")
        }
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
}
