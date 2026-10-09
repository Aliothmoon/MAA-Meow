package com.aliothmoon.maameow.remote

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.Os
import android.view.Surface
import com.aliothmoon.maameow.ITouchEventCallback
import com.aliothmoon.maameow.MaaCoreService
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.bridge.NativeBridgeLib
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.constant.DisplayMode
import com.aliothmoon.maameow.constant.MaaFiles
import com.aliothmoon.maameow.maa.InputControlUtils
import com.aliothmoon.maameow.remote.internal.ActivityUtils
import com.aliothmoon.maameow.remote.internal.CoreDataStore
import com.aliothmoon.maameow.remote.internal.GameAudioMuteController
import com.aliothmoon.maameow.remote.internal.GameFpsMonitor
import com.aliothmoon.maameow.remote.internal.GestureRecorder
import com.aliothmoon.maameow.remote.internal.LogcatRetention
import com.aliothmoon.maameow.remote.internal.PermissionGrantHelper
import com.aliothmoon.maameow.remote.internal.PowerController
import com.aliothmoon.maameow.remote.internal.display.PrimaryDisplayManager
import com.aliothmoon.maameow.remote.internal.ProcessLiveness
import com.aliothmoon.maameow.remote.internal.RemoteUtils
import com.aliothmoon.maameow.remote.internal.ScreenManager
import com.aliothmoon.maameow.remote.internal.StaleFrameGuard
import com.aliothmoon.maameow.remote.internal.SystemLogDump
import com.aliothmoon.maameow.remote.internal.UserDirProbe
import com.aliothmoon.maameow.remote.internal.display.VirtualDisplayManager
import com.aliothmoon.maameow.remote.internal.WakeUnlockController
import com.aliothmoon.maameow.remote.internal.XmsfFirewall
import com.aliothmoon.maameow.third.Command
import com.aliothmoon.maameow.third.FakeContext
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.Workarounds
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

class RemoteServiceImpl : RemoteService.Stub() {

    companion object {
        private const val TAG = "RemoteService"
        private const val HEARTBEAT_INTERVAL_MS = 5_000L
        private const val JPEG_MAX_EDGE = 1280
        private const val PREVIEW_SHUTDOWN_TIMEOUT_MS = 1_000L

        @JvmStatic
        fun performEmergencyCleanup() {
            Ln.i("$TAG: performEmergencyCleanup triggered")
            runCatching {
                GameAudioMuteController.restoreAll()
                XmsfFirewall.restoreIfNeeded()
                VirtualDisplayManager.stop()
                PowerController.destroy()
                ScreenManager.destroy()
                MaaCoreManager.destroy()
            }.onFailure {
                Ln.e("$TAG: Emergency cleanup failed: ${it.message}")
            }
        }
    }

    init {
        RemoteBootTrace.mark("CTOR_START")
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { performEmergencyCleanup() }
        }.apply { name = "remote-shutdown-hook" })
    }

    private val virtualDisplayMode = AtomicInteger(DisplayMode.PRIMARY)
    private val appPid = AtomicInteger(0)
    private val destroyed = AtomicBoolean(false)
    /** 同一进程内 setup 幂等：成功后再调直接返回 OK，失败则下次重试 */
    private var setup = false
    private val coreData = CoreDataStore()

    init {
        // ctor 必须轻量：重活放 setup()，attach 前零阻塞
        // Root 下放开 umask，core 与本进程写的文件对 shell 可读写（切回 Shizuku 后还能追加）；父目录 0771 挡住其他 App
        if (Process.myUid() != Process.SHELL_UID) runCatching { Os.umask(0) }
        Workarounds.apply()
        startHeartbeatWatchdog()
        Ln.i("$TAG: RemoteServiceImpl created (lightweight ctor)")
        RemoteBootTrace.mark("CTOR_DONE")
    }

    override fun destroy() {
        if (!destroyed.compareAndSet(false, true)) {
            return
        }
        Ln.i("$TAG: destroy()")
        InputControlUtils.setTouchCallback(null)
        GameFpsMonitor.stop()
        StaleFrameGuard.stop()
        shutdownPreview()
        performEmergencyCleanup()
        exitProcess(0)
    }

    /**
     * 退出前断开预览 Surface：SurfaceView 的缓冲队列认不出 producer 进程死了，不断开的话
     * 这块 Surface 会一直算在本进程头上，下一个特权进程 `eglCreateWindowSurface` 报 already connected
     *
     * 排在 core 收尾之前：换资源档时新进程一秒多就会来接这块 Surface
     * 限时：渲染线程可能正卡在 swap 上，等不到就走，不能拖住进程退出
     */
    private fun shutdownPreview() {
        if (!NativeBridgeLib.LOADED) return
        val worker = Thread { NativeBridgeLib.shutdownPreview() }.apply {
            name = "preview-shutdown"
            isDaemon = true
            start()
        }
        runCatching { worker.join(PREVIEW_SHUTDOWN_TIMEOUT_MS) }
        if (worker.isAlive) Ln.w("$TAG: preview shutdown still running after ${PREVIEW_SHUTDOWN_TIMEOUT_MS}ms, leaving it")
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

    override fun setup(userDir: String?, isDebug: Boolean): Int {
        if (setup) return SetupResult.OK
        RemoteBootTrace.mark("SETUP_BEGIN")
        // 清上一实例可能残留的断网规则，同步执行先于业务 AIDL
        runCatching { XmsfFirewall.ensureRestored() }
            .onFailure { Ln.w("XmsFw boot restore failed: ${it.message}") }
        RemoteBootTrace.mark("SETUP_XMSF_RESTORED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { VirtualDisplayManager.cleanupStaleState() }
                .onFailure { Ln.w("VDM boot cleanup failed: ${it.message}") }
        }
        // 不可访问的路径进 AsstSetUserDir 会 abort 整个进程（#227），这里只报告，换到哪由用户在设置里决定
        val dir = File(userDir.orEmpty())
        val probeFailure = UserDirProbe.probe(dir)
        if (probeFailure != null) {
            Ln.e("$TAG: setup failed - userDir inaccessible: $probeFailure (uid=${Process.myUid()} sdk=${Build.VERSION.SDK_INT})")
            RemoteBootTrace.mark("SETUP_USER_DIR_INACCESSIBLE", probeFailure)
            return SetupResult.ERR_USER_DIR_INACCESSIBLE
        }
        RemoteBootTrace.bindUserDir(dir)
        val ctx = MaaCoreManager.MaaContext ?: run {
            Ln.e("$TAG: setup failed - MaaContext is null")
            return SetupResult.ERR_CORE_NOT_LOADED
        }
        Ln.i("NativeBridgeLib ping ${NativeBridgeLib.ping()}")
        with(ctx) {
            if (!AsstSetUserDir(dir.path)) {
                Ln.e("$TAG: setup failed - AsstSetUserDir($dir) returned false")
                return SetupResult.ERR_SET_USER_DIR
            }
            Ln.i("MaaCore ${AsstGetVersion()} userDir=$dir")
        }
        PermissionGrantHelper.disablePhantomProcessKiller()
        pruneLogcatCaptures(dir)
        setup = true
        RemoteBootTrace.mark("SETUP_DONE")
        return SetupResult.OK
    }

    /** 放在这里而非 logcat 进程：调试模式关掉后不再起 logcat 进程，旧文件也得有人收 */
    private fun pruneLogcatCaptures(userDir: File) {
        Thread {
            runCatching { LogcatRetention.prune(File(userDir, "${MaaFiles.DEBUG}/logcat")) }
                .onSuccess { if (it > 0) Ln.i("$TAG: pruned $it old logcat captures") }
                .onFailure { Ln.w("$TAG: prune logcat captures failed: ${it.message}") }
        }.apply { name = "logcat-prune"; isDaemon = true }.start()
    }

    override fun test(map: MutableMap<String, String>) {
    }

    // ---- 独立数据目录 ----

    override fun ensureCoreResources(apkPath: String?, stamp: String?): Boolean {
        if (apkPath.isNullOrBlank() || stamp.isNullOrBlank()) return false
        val start = System.currentTimeMillis()
        val ok = coreData.ensureResources(File(apkPath), stamp)
        Ln.i("$TAG: ensureCoreResources stamp=$stamp ok=$ok in ${System.currentTimeMillis() - start}ms")
        return ok
    }

    override fun applyCoreHotUpdate(zip: ParcelFileDescriptor?): Boolean {
        zip ?: return false
        val ok = ParcelFileDescriptor.AutoCloseInputStream(zip).use { coreData.applyHotUpdate(it) }
        Ln.i("$TAG: applyCoreHotUpdate ok=$ok version=${coreData.resourceVersion()}")
        return ok
    }

    override fun getCoreResourceVersion(): String = coreData.resourceVersion()

    override fun putCoreFile(relPath: String?, src: ParcelFileDescriptor?): Boolean {
        if (relPath == null || src == null) {
            src?.close()
            return false
        }
        return ParcelFileDescriptor.AutoCloseInputStream(src).use { coreData.putFile(relPath, it) }
            .also { if (!it) Ln.w("$TAG: putCoreFile rejected: $relPath") }
    }

    override fun listCoreDebugFiles(): MutableList<String> = coreData.listDebugFiles().toMutableList()

    override fun openCoreDebugFile(relPath: String?): ParcelFileDescriptor? {
        val file = relPath?.let(coreData::debugFile) ?: return null
        return runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
    }

    override fun clearCoreData(): Boolean = coreData.clear().also { Ln.i("$TAG: clearCoreData ok=$it") }

    override fun screencap(width: Int, height: Int) {
    }

    /** 取当前帧交给 [block]，用完即回收；无帧或出错返回 null */
    private inline fun <T> withFrameBitmap(tag: String, block: (Bitmap) -> T?): T? {
        val bitmap = NativeBridgeLib.getFrameBufferBitmap() ?: run {
            Ln.w("$TAG: $tag - no frame available")
            return null
        }
        return try {
            block(bitmap)
        } catch (e: Exception) {
            Ln.e("$TAG: $tag error: ${e.message}")
            null
        } finally {
            bitmap.recycle()
        }
    }

    override fun captureFrameJpeg(quality: Int): ByteArray? = withFrameBitmap("captureFrameJpeg") { frame ->
        // 前台模式是原生分辨率，缩到长边 1280 免得撑爆 binder 缓冲
        val scale = JPEG_MAX_EDGE.toFloat() / maxOf(frame.width, frame.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true)
        } else {
            frame
        }
        try {
            ByteArrayOutputStream().use { out ->
                if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)) {
                    out.toByteArray()
                } else {
                    Ln.e("$TAG: captureFrameJpeg - JPEG compress failed")
                    null
                }
            }
        } finally {
            if (bitmap !== frame) bitmap.recycle()
        }
    }

    override fun captureFramePng(dirPath: String?): String? {
        if (dirPath.isNullOrBlank()) {
            Ln.w("$TAG: captureFramePng - blank dirPath")
            return null
        }
        return withFrameBitmap("captureFramePng") { bitmap ->
            val dir = File(dirPath).apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val file = File(dir, "screenshot_$timestamp.png")
            val ok = FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (!ok) {
                Ln.e("$TAG: captureFramePng - PNG compress failed")
                file.delete()
                return@withFrameBitmap null
            }
            Ln.i("$TAG: captureFramePng saved ${file.absolutePath}")
            file.absolutePath
        }
    }

    override fun setForcedDisplaySize(width: Int, height: Int): Boolean {
        return ScreenManager.setForcedDisplaySize(width, height)
    }

    override fun clearForcedDisplaySize(): Boolean {
        return ScreenManager.clearForcedDisplaySize()
    }

    override fun grantPermissions(request: PermissionGrantRequest): PermissionStateInfo {
        val packageName = request.packageName
        val uid = if (request.uid > 0) request.uid
        else RemoteUtils.getAppUid(packageName).takeIf { it > 0 } ?: request.uid
        val p = request.permissions

        with(PermissionGrantHelper) {
            // 结果不进 PermissionStateInfo 免改 AIDL；放开失败由 App 侧预检兜底
            if (p and PermissionGrantRequest.PERM_FGS_SPECIAL_USE != 0) {
                grantForegroundServiceSpecialUse(packageName)
            }
            return PermissionStateInfo(
                accessibilityPermission = if (p and PermissionGrantRequest.PERM_ACCESSIBILITY != 0) grantAccessibilityService(
                    request.accessibilityServiceId
                ) else false,
                floatingWindowPermission = if (p and PermissionGrantRequest.PERM_FLOATING_WINDOW != 0) grantFloatingWindowPermission(
                    packageName,
                    uid
                ) else false,
                notificationPermission = if (p and PermissionGrantRequest.PERM_NOTIFICATION != 0) grantNotificationPermission(
                    packageName,
                    uid
                ) else false,
                batteryOptimizationExempt = if (p and PermissionGrantRequest.PERM_BATTERY != 0) grantBatteryOptimizationExemption(
                    packageName
                ) else false,
                storagePermission = if (p and PermissionGrantRequest.PERM_STORAGE != 0) grantStoragePermission(
                    packageName,
                    uid
                ) else false,
                backgroundUnrestricted = if (p and PermissionGrantRequest.PERM_BACKGROUND != 0) grantBackgroundUnrestricted(
                    packageName,
                    uid
                ) else false,
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

    override fun touchDown(x: Int, y: Int, contact: Int) {
        if (virtualDisplayMode.get() == DisplayMode.PRIMARY) return
        val displayId = VirtualDisplayManager.getDisplayId()
        if (displayId != DefaultDisplayConfig.DISPLAY_NONE) {
            InputControlUtils.down(x, y, contact, displayId)
        }
    }

    override fun touchMove(x: Int, y: Int, contact: Int) {
        if (virtualDisplayMode.get() == DisplayMode.PRIMARY) return
        val displayId = VirtualDisplayManager.getDisplayId()
        if (displayId != DefaultDisplayConfig.DISPLAY_NONE) {
            InputControlUtils.move(x, y, contact, displayId)
        }
    }

    override fun touchUp(x: Int, y: Int, contact: Int) {
        if (virtualDisplayMode.get() == DisplayMode.PRIMARY) return
        val displayId = VirtualDisplayManager.getDisplayId()
        if (displayId != DefaultDisplayConfig.DISPLAY_NONE) {
            InputControlUtils.up(x, y, contact, displayId)
        }
    }

    override fun touchCancel() {
        if (virtualDisplayMode.get() == DisplayMode.PRIMARY) return
        val displayId = VirtualDisplayManager.getDisplayId()
        if (displayId != DefaultDisplayConfig.DISPLAY_NONE) {
            InputControlUtils.cancel(displayId)
        }
    }

    override fun setDisplayPower(on: Boolean) {
        PowerController.setDisplayPower(on)
    }

    override fun startVirtualDisplay(): Int {
        Ln.i("$TAG: startVirtualDisplay() ${virtualDisplayMode.get()}")
        return when (virtualDisplayMode.get()) {
            DisplayMode.PRIMARY -> PrimaryDisplayManager.start()
            DisplayMode.BACKGROUND -> VirtualDisplayManager.start().also { displayId ->
                if (displayId != DefaultDisplayConfig.DISPLAY_NONE) {
                    PowerController.startUserActivityKeepAlive(displayId)
                }
            }

            else -> DefaultDisplayConfig.DISPLAY_NONE
        }
    }

    override fun stopVirtualDisplay() {
        Ln.i("$TAG: stopVirtualDisplay() ${virtualDisplayMode.get()}")
        when (virtualDisplayMode.get()) {
            DisplayMode.PRIMARY -> PrimaryDisplayManager.stop()
            DisplayMode.BACKGROUND -> {
                GameFpsMonitor.stop()
                StaleFrameGuard.stop()
                PowerController.stopUserActivityKeepAlive()
                VirtualDisplayManager.stop()
            }
        }
        GameAudioMuteController.restoreAll()
    }

    override fun getGameFps(): Float = GameFpsMonitor.currentFps()

    override fun setPlayAudioOpAllowed(packageName: String?, isAllowed: Boolean): Boolean {
        if (packageName.isNullOrBlank()) return false
        val ok = GameAudioMuteController.setMuted(packageName, muted = !isAllowed)
        if (!ok) {
            Ln.w("$TAG: setPlayAudioOpAllowed($packageName, allowed=$isAllowed) failed")
        }
        return ok
    }

    // 只认游戏主进程：probe 会把常驻的 包名:pushcore 这类子进程算成活着，游戏崩了也判不出
    override fun isAppAlive(packageName: String): Int = when (ProcessLiveness.pidof(packageName)) {
        ProcessLiveness.ALIVE -> AppAliveStatus.ALIVE
        ProcessLiveness.DEAD -> AppAliveStatus.DEAD
        ProcessLiveness.UNKNOWN -> AppAliveStatus.UNKNOWN
    }

    override fun heartbeat(pid: Int) {
        appPid.set(pid)
        Ln.i("$TAG: heartbeat received, app pid=$pid")
    }

    override fun isAppOnVirtualDisplay(packageName: String): Boolean {
        val targetDisplayId = VirtualDisplayManager.getDisplayId()
        if (targetDisplayId == DefaultDisplayConfig.DISPLAY_NONE) return true
        val onDisplay = ActivityUtils.isAppOnDisplay(packageName, targetDisplayId)
        // 游戏不是 MaaMeow 拉起的（未启用自动启动）时，这里是首次得知它在虚拟屏上
        if (onDisplay) GameFpsMonitor.ensureStarted(packageName)
        return onDisplay
    }

    override fun moveAppToVirtualDisplay(packageName: String): Boolean {
        val targetDisplayId = VirtualDisplayManager.getDisplayId()
        if (targetDisplayId == DefaultDisplayConfig.DISPLAY_NONE) {
            Ln.w("$TAG: moveAppToVirtualDisplay: no active virtual display")
            return false
        }
        Ln.i("$TAG: moveAppToVirtualDisplay($packageName) -> display $targetDisplayId")
        return ActivityUtils.repinAppToDisplay(packageName, targetDisplayId)
    }

    /** @return [com.aliothmoon.maameow.constant.WakeUnlockResult] */
    override fun unlock(credential: String?): Int =
        WakeUnlockController.unlock(credential.orEmpty())

    /** @return [com.aliothmoon.maameow.constant.WakeUnlockResult] */
    override fun lockAndSleep(): Int = WakeUnlockController.lockAndSleep()

    /** @return [com.aliothmoon.maameow.constant.WakeUnlockResult] */
    override fun testUnlock(credential: String?): Int =
        WakeUnlockController.testUnlock(credential.orEmpty())

    /** @return [com.aliothmoon.maameow.constant.WakeUnlockResult] */
    override fun unlockWithGesture(gestureJson: String?): Int =
        WakeUnlockController.unlockWithGesture(gestureJson.orEmpty())

    /** @return [com.aliothmoon.maameow.constant.WakeUnlockResult] */
    override fun testUnlockGesture(gestureJson: String?): Int =
        WakeUnlockController.testUnlockGesture(gestureJson.orEmpty())

    override fun startGestureRecord(timeoutMs: Int) = GestureRecorder.start(timeoutMs)

    /** @return [com.aliothmoon.maameow.domain.models.GestureRecordResult] 的 JSON */
    override fun pollGestureRecord(): String = GestureRecorder.poll()

    override fun cancelGestureRecord() = GestureRecorder.cancel()

    override fun isPackageInstalled(packageName: String): Boolean {
        return try {
            FakeContext.get().packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            Ln.w("$TAG: isPackageInstalled: $packageName not found", e)
            false
        }
    }

    override fun dumpSystemLog(sinceMs: Long, untilMs: Long, pid: Int, processPrefix: String?): String {
        // 空前缀行行命中，会把整份系统日志带出设备
        if (processPrefix.isNullOrEmpty()) return "# missing process prefix"
        return SystemLogDump.dump(sinceMs, untilMs, pid, processPrefix)
    }

    override fun isSmartResolutionEnabled(): Boolean {
        return try {
            Command.execReadLine("settings", "get", "global", "low_resolution_switch")?.trim() == "1"
        } catch (e: Exception) {
            Ln.w("$TAG: read low_resolution_switch failed", e)
            false
        }
    }

    override fun startActivity(intent: Intent): Boolean {
        return ActivityUtils.startActivity(intent)
    }

    override fun getTopPackage(displayId: Int): String? =
        (ActivityUtils.probeDisplay(displayId) as? ActivityUtils.DisplayOccupancy.Occupied)?.topPackage

    override fun setForceFullscreenOnVirtualDisplay(enabled: Boolean) {
        Ln.i("$TAG: setForceFullscreenOnVirtualDisplay($enabled)")
        ActivityUtils.forceFullscreenOnVirtualDisplay = enabled
    }

    override fun setPackageNetworkingEnabled(packageName: String?, enabled: Boolean): Boolean {
        if (packageName.isNullOrBlank()) return false
        return XmsfFirewall.setNetworkingEnabled(packageName, enabled)
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
