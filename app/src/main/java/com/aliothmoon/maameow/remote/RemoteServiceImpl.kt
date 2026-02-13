package com.aliothmoon.maameow.remote

import android.os.Process
import android.view.Display
import android.view.Surface
import com.aliothmoon.maameow.MaaCoreService
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.bridge.NativeBridgeLib
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.constant.DisplayMode
import com.aliothmoon.maameow.maa.MaaCoreLibrary
import com.aliothmoon.maameow.remote.internal.PrimaryDisplayManager
import com.aliothmoon.maameow.remote.internal.VirtualDisplayManager
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.wrappers.ServiceManager
import com.sun.jna.Native
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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


    private val aliveFlagFile = File("/data/local/tmp/Maa/screen_flag")

    private val virtualDisplayMode = AtomicInteger(0)

    private var setup = false

    override fun destroy() {
        Ln.i("$TAG: destroy()")
        maaService.DestroyInstance()
        if (aliveFlagFile.exists()) {
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

    override fun setup(userDir: String?, isDebug: Boolean): Boolean {
        val result = NativeBridgeLib.ping()
        if (!setup) {
            if (isDebug) {
                startLogcatCapture(userDir)
            }
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
        }
        return true
    }

    override fun test(map: MutableMap<String, String>) {

    }

    override fun screencap(width: Int, height: Int) {

    }

    override fun setForcedDisplaySize(width: Int, height: Int): Boolean {
        aliveFlagFile.parentFile?.mkdirs()
        aliveFlagFile.createNewFile()
        return ServiceManager.getWindowManager()
            .setForcedDisplaySize(Display.DEFAULT_DISPLAY, width, height)
    }

    override fun clearForcedDisplaySize(): Boolean {
        aliveFlagFile.delete()
        return ServiceManager.getWindowManager().clearForcedDisplaySize(Display.DEFAULT_DISPLAY)
    }

    override fun grantPermissions(request: PermissionGrantRequest): PermissionStateInfo {
        val packageName = request.packageName
        val uid = request.uid

        with(PermissionGrantHelper) {
            val accessibilityPermission = grantAccessibilityService(request.accessibilityServiceId)
            val floatingWindowPermission = grantFloatingWindowPermission(packageName, uid)
            val notificationPermission = grantNotificationPermission(packageName, uid)
            val batteryOptimizationExempt = grantBatteryOptimizationExemption(packageName)
            val storagePermission = grantStoragePermission(packageName, uid)

            return PermissionStateInfo(
                floatingWindowPermission = floatingWindowPermission,
                storagePermission = storagePermission,
                batteryOptimizationExempt = batteryOptimizationExempt,
                accessibilityPermission = accessibilityPermission,
                notificationPermission = notificationPermission
            )
        }
    }

    override fun setMonitorSurface(surface: Surface?) {
        Ln.i("$TAG: setMonitorSurface(${surface != null})")
        VirtualDisplayManager.setMonitorSurface(surface)
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

    override fun getVirtualDisplayId(): Int {
        return when (virtualDisplayMode.get()) {
            DisplayMode.PRIMARY -> PrimaryDisplayManager.DISPLAY_ID
            DisplayMode.BACKGROUND -> VirtualDisplayManager.getDisplayId()
            else -> DefaultDisplayConfig.DISPLAY_NONE
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

    private fun startLogcatCapture(userDir: String?) {
        if (userDir == null) return
        try {
            val debugDir = File(userDir, "debug").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val logcatDir = File(debugDir, "logcat_logs").apply { mkdirs() }
            val logFile = File(logcatDir, "logcat_$timestamp.log")
            val pid = Process.myPid().toString()
            Ln.i("$TAG: Saving logcat (pid=$pid) to ${logFile.absolutePath}")
            Runtime.getRuntime()
                .exec(arrayOf("sh", "-c", "logcat --pid=$pid > ${logFile.absolutePath}"))
        } catch (e: Exception) {
            Ln.e("$TAG: Failed to start logcat capture: ${e.message}")
        }
    }
}
