package com.aliothmoon.maameow.remote.internal.display

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.view.Surface
import com.aliothmoon.maameow.bridge.NativeBridgeLib
import com.aliothmoon.maameow.constant.AndroidVersions
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.constant.DefaultDisplayConfig.VD_NAME
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.wrappers.ServiceManager
import com.aliothmoon.maameow.third.wrappers.WindowManager
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference


object VirtualDisplayManager {

    private const val STATE_IDLE = 0
    private const val STATE_CAPTURING = 1

    private const val VIRTUAL_DISPLAY_FLAG_PUBLIC: Int = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
    private const val VIRTUAL_DISPLAY_FLAG_PRESENTATION: Int =
        DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
    private const val VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY: Int =
        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
    private const val VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH: Int = 1 shl 6
    private const val VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT: Int = 1 shl 7
    private const val VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL: Int = 1 shl 8
    private const val VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS: Int = 1 shl 9
    private const val VIRTUAL_DISPLAY_FLAG_TRUSTED: Int = 1 shl 10
    private const val VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP: Int = 1 shl 11
    private const val VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED: Int = 1 shl 12
    private const val VIRTUAL_DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED: Int = 1 shl 13
    private const val VIRTUAL_DISPLAY_FLAG_OWN_FOCUS: Int = 1 shl 14
    private const val VIRTUAL_DISPLAY_FLAG_DEVICE_DISPLAY_GROUP: Int = 1 shl 15
    private const val VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED: Int = 1 shl 16

    private const val VD_SYSTEM_DECORATIONS = false
    private const val VD_DESTROY_CONTENT = true

    const val DISPLAY_NONE = -1

    data class DisplayConfig(
        val width: Int = DefaultDisplayConfig.WIDTH,
        val height: Int = DefaultDisplayConfig.HEIGHT,
        val dpi: Int = DefaultDisplayConfig.DPI
    )

    private val state = AtomicInteger(STATE_IDLE)
    private val config = AtomicReference(DisplayConfig())
    private val displayId = AtomicInteger(DISPLAY_NONE)
    private val virtualDisplay = AtomicReference<VirtualDisplay?>()
    private val vdmSession = AtomicReference<VdmDisplaySession?>()

    private val monitorSurface = AtomicReference<Surface?>()

    private data class DisplaySelection(
        val display: VirtualDisplay,
        val vdmSession: VdmDisplaySession? = null,
    )

    fun setMonitorSurface(surface: Surface?) {
        val old = monitorSurface.getAndSet(surface)
        if (old != null && old != surface) {
            old.release()
            Ln.i("Old monitor surface released")
        }
        Ln.i("setMonitorSurface: old=${old != null}, new=${surface != null}")
    }

    @Synchronized
    fun start(): Int {
        if (!state.compareAndSet(STATE_IDLE, STATE_CAPTURING)) {
            Ln.w("start: already capturing")
            return displayId.get()
        }
        return startInternal()
    }

    @Synchronized
    fun stop() {
        if (!state.compareAndSet(STATE_CAPTURING, STATE_IDLE)) {
            return
        }
        releaseResources(removeAssociation = true)
        monitorSurface.getAndSet(null)?.release()
        Ln.i("VirtualDisplayManager stopped")
    }

    @Synchronized
    fun restart() {
        if (state.get() != STATE_CAPTURING) {
            return
        }
        releaseResources(removeAssociation = false)
        if (state.get() == STATE_CAPTURING) {
            startInternal()
        }
    }

    @Synchronized
    fun setResolution(width: Int, height: Int, dpi: Int = config.get().dpi) {
        val newConfig = DisplayConfig(width, height, dpi)
        val oldConfig = config.getAndSet(newConfig)
        if (state.get() == STATE_CAPTURING && oldConfig != newConfig) {
            Ln.i("Resolution changed: ${oldConfig.width}x${oldConfig.height} -> ${width}x${height}, restart")
            restart()
        }
    }

    fun getDisplayId(): Int = displayId.get()

    /** 清理由上一个被强杀或崩溃的特权服务进程遗留的 VDM 关联。 */
    fun cleanupStaleState() {
        if (Build.VERSION.SDK_INT < AndroidVersions.API_34_ANDROID_14) return
        CompanionAssociation.cleanupStale()
    }

    private fun startInternal(): Int {
        try {
            val cfg = config.get()
            val surface = NativeBridgeLib.setupNativeCapturer(cfg.width, cfg.height)
            createVirtualDisplay(surface, cfg)

            Ln.i("VirtualDisplayManager started, displayId=${displayId.get()}")
            return displayId.get()
        } catch (e: Exception) {
            Ln.e("VirtualDisplayManager start failed", e)
            releaseResources(removeAssociation = true)
            state.set(STATE_IDLE)
            return DISPLAY_NONE
        }
    }

    private fun releaseResources(removeAssociation: Boolean) {
        val vd = virtualDisplay.getAndSet(null)
        val session = vdmSession.getAndSet(null)
        if (
            Build.VERSION.SDK_INT >= AndroidVersions.API_34_ANDROID_14 &&
            session != null
        ) {
            try {
                if (removeAssociation) session.close() else session.closeForRestart()
            } catch (failure: Exception) {
                Ln.e("Failed to close VDM display session", failure)
            } catch (failure: LinkageError) {
                Ln.e("VDM display cleanup API unavailable", failure)
            }
        } else {
            vd?.release()
            if (removeAssociation && Build.VERSION.SDK_INT >= AndroidVersions.API_34_ANDROID_14) {
                runCatching { cleanupStaleState() }
                    .onFailure { Ln.w("Failed to clean up VDM association: ${it.message}") }
            }
        }
        NativeBridgeLib.releaseNativeCapturer()
        displayId.set(DISPLAY_NONE)
    }

    private fun createVirtualDisplay(surface: Surface, cfg: DisplayConfig) {
        val flags = buildDisplayFlags()
        val wm = ServiceManager.getWindowManager()
        val physicalRotation = runCatching { wm.rotation }.getOrDefault(-1)
        Ln.i("Physical display rotation: $physicalRotation")

        val selection = selectDisplay(surface, cfg, flags)
        val vd = selection.display
        vdmSession.set(selection.vdmSession)
        virtualDisplay.set(vd)
        val vdId = vd.display.displayId
        displayId.set(vdId)

        val d = vd.display
        val groupId = runCatching {
            ServiceManager.getDisplayManager().getDisplayGroupId(vdId)
        }.onFailure { Ln.w("Could not read VD display group: ${it.message}") }.getOrNull()
        Ln.i(
            "VD created: id=$vdId" +
                    ", configured=${cfg.width}x${cfg.height}" +
                    ", actual=${d.mode.physicalWidth}x${d.mode.physicalHeight}" +
                    ", rotation=${d.rotation}" +
                    ", groupId=${groupId ?: "unknown"}" +
                    ", requestedFlags=0x${flags.toString(16)}" +
                    ", actualFlags=0x${d.flags.toString(16)}"
        )
        if (groupId == 0) {
            Ln.w("VD remained in default display group; system sleep may stop its rendering")
        }

        if (d.rotation != Surface.ROTATION_0) {
            // 所有旋转非零的情况都先尝试 freezeRotation
            runCatching {
                wm.freezeRotation(vdId, Surface.ROTATION_0)
                Ln.i("freezeRotation done, post-freeze rotation=${vd.display.rotation}")
            }.onFailure { e -> Ln.w("freezeRotation failed: ${e.message}") }

            if (physicalRotation == Surface.ROTATION_0) {
                // 物理屏处于自然方向（rotation=0）而 VD 却有旋转角，
                // 这是横屏原生设备如AYN Odin2的典型特征：
                // 此类设备的定制 ROM 对二级显示调 freezeRotation 无效，
                // 额外调 setForcedDisplaySize 强制 VD 向内部 app 上报横屏尺寸。
                Ln.w(
                    "Landscape-native device detected (physRot=0, vdRot=${d.rotation}), " +
                            "applying setForcedDisplaySize"
                )
                runCatching {
                    wm.setForcedDisplaySize(vdId, cfg.width, cfg.height)
                    Ln.i("setForcedDisplaySize(${cfg.width}x${cfg.height}) applied")
                }.onFailure { e -> Ln.w("setForcedDisplaySize failed: ${e.message}") }
            }
        }
    }

    /**
     * 在 Android 14+ 的 VDM 候选显示通过结构及电源状态检查前，保留未挂载 Surface 的
     * 旧虚拟显示。VDM 保持拆分前的创建顺序，在创建交易中直接绑定
     * Surface；若检查失败，再把 Surface 交给保留的旧显示。
     */
    private fun selectDisplay(
        surface: Surface,
        cfg: DisplayConfig,
        flags: Int,
    ): DisplaySelection {
        val displayManager = ServiceManager.getDisplayManager()
        val legacy = displayManager.createNewVirtualDisplay(
            VD_NAME,
            cfg.width,
            cfg.height,
            cfg.dpi,
            null,
            flags,
        )
        val legacyGroup = runCatching {
            displayManager.getDisplayGroupId(legacy.display.displayId)
        }.onFailure {
            Ln.w("Could not read legacy VD display group: ${it.message}")
        }.getOrNull()

        if (Build.VERSION.SDK_INT < AndroidVersions.API_34_ANDROID_14 || legacyGroup != 0) {
            if (Build.VERSION.SDK_INT >= AndroidVersions.API_34_ANDROID_14) {
                runCatching { cleanupStaleState() }
                    .onFailure { Ln.w("Failed to remove unused VDM association: ${it.message}") }
            }
            return attachLegacySurface(legacy, surface)
        }

        var independent: VdmDisplaySession? = null
        val legacyId = legacy.display.displayId
        try {
            independent = VdmDisplayFactory.create(
                VD_NAME,
                cfg.width,
                cfg.height,
                cfg.dpi,
                surface,
            )
            independent.awaitReady()

            // VDM 默认让可信显示使用本地输入法，这与旧 VD 不同；正式采用候选显示前，
            // 恢复为旧路径使用的回退显示输入法策略。
            val independentId = independent.display.display.displayId
            val windowManager = ServiceManager.getWindowManager()
            val imePolicy = runCatching {
                windowManager.setDisplayImePolicy(
                    independentId,
                    WindowManager.DISPLAY_IME_POLICY_FALLBACK_DISPLAY,
                )
                check(
                    windowManager.getDisplayImePolicy(independentId) ==
                        WindowManager.DISPLAY_IME_POLICY_FALLBACK_DISPLAY
                )
                "fallback"
            }.getOrElse { failure ->
                "warning-${failure.javaClass.simpleName}"
            }

            // 仅在 VDM 显示进入独立显示组且状态为 STATE_ON 后，才正式切换。
            legacy.release()
            VdmDiagnostics.reportSuccess(independent.display.display, legacyId, imePolicy)
            return DisplaySelection(independent.display, independent)
        } catch (failure: Exception) {
            VdmDiagnostics.reportFallback(failure, independent?.display?.display, legacyId)
        } catch (failure: LinkageError) {
            VdmDiagnostics.reportFallback(failure, independent?.display?.display, legacyId)
        }

        independent?.let { candidate ->
            try {
                candidate.detachSurface()
                candidate.close()
            } catch (cleanupFailure: Exception) {
                Ln.e("Failed to clean up rejected independent display", cleanupFailure)
            } catch (cleanupFailure: LinkageError) {
                Ln.e("Rejected independent display cleanup API unavailable", cleanupFailure)
            }
        }
        return attachLegacySurface(legacy, surface)
    }

    private fun attachLegacySurface(
        legacy: VirtualDisplay,
        surface: Surface,
    ): DisplaySelection = try {
        legacy.setSurface(surface)
        DisplaySelection(legacy)
    } catch (failure: Throwable) {
        legacy.release()
        throw failure
    }

    private fun buildDisplayFlags(): Int {
        var flags = (VIRTUAL_DISPLAY_FLAG_PUBLIC
                or VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                or VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH)

        if (VD_DESTROY_CONTENT) {
            flags = flags or VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL
        }
        if (VD_SYSTEM_DECORATIONS) {
            flags = flags or VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS
        }
        if (Build.VERSION.SDK_INT >= AndroidVersions.API_33_ANDROID_13) {
            flags = flags or (VIRTUAL_DISPLAY_FLAG_TRUSTED
                    or VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP
                    or VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED
                    or VIRTUAL_DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED)
            if (Build.VERSION.SDK_INT >= AndroidVersions.API_34_ANDROID_14) {
                flags = flags or (VIRTUAL_DISPLAY_FLAG_OWN_FOCUS
                        or VIRTUAL_DISPLAY_FLAG_DEVICE_DISPLAY_GROUP
                        or VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED)
            }
        }
        return flags
    }
}
