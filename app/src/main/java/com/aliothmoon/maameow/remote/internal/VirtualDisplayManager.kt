package com.aliothmoon.maameow.remote.internal

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.view.Surface
import com.aliothmoon.maameow.bridge.NativeBridgeLib
import com.aliothmoon.maameow.constant.AndroidVersions
import com.aliothmoon.maameow.constant.DefaultDisplayConfig.VD_NAME
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.wrappers.ServiceManager
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
    private val reader = AtomicReference<ImageReader?>()

    private var lastPreviewTime = 0L
    private val monitorSurface = AtomicReference<Surface?>()

    private val handler: Handler by lazy {
        FrameCaptureHelper.createCaptureHandler("VDCapture")
    }


    fun setMonitorSurface(surface: Surface?) {
        val old = monitorSurface.getAndSet(surface)
        if (old != null && old !== surface) {
            runCatching { old.release() }
                .onFailure { Ln.w("setMonitorSurface: release old surface failed: ${it.message}") }
        }
        Ln.i("setMonitorSurface: old=${old != null}, new=${surface != null}")
    }

    fun start(): Int {
        if (!state.compareAndSet(STATE_IDLE, STATE_CAPTURING)) {
            Ln.w("start: already capturing")
            return displayId.get()
        }
        return startInternal()
    }

    fun stop() {
        if (!state.compareAndSet(STATE_CAPTURING, STATE_IDLE)) {
            return
        }
        releaseResources()
        NativeBridgeLib.releaseFrameBuffers()
        Ln.i("VirtualDisplayManager stopped")
    }

    fun restart() {
        if (state.get() != STATE_CAPTURING) {
            return
        }
        releaseResources()
        startInternal()
    }

    fun setResolution(width: Int, height: Int, dpi: Int = config.get().dpi) {
        val newConfig = DisplayConfig(width, height, dpi)
        val oldConfig = config.getAndSet(newConfig)
        if (state.get() == STATE_CAPTURING && oldConfig != newConfig) {
            Ln.i("Resolution changed: ${oldConfig.width}x${oldConfig.height} -> ${width}x${height}, restart")
            restart()
        }
    }

    fun getDisplayId(): Int = displayId.get()

    private fun startInternal(): Int {
        try {
            val cfg = config.get()
            NativeBridgeLib.initFrameBuffers(cfg.width, cfg.height)

            val r = DisplayHelper.newInstanceImagerReader(cfg.width, cfg.height)
            r.setOnImageAvailableListener({ onImageAvailable(it) }, handler)
            reader.set(r)

            createVirtualDisplay(r.surface, cfg)

            Ln.i("VirtualDisplayManager started, displayId=${displayId.get()}")
            return displayId.get()
        } catch (e: Exception) {
            Ln.e("VirtualDisplayManager start failed", e)
            state.set(STATE_IDLE)
            return DISPLAY_NONE
        }
    }

    private fun releaseResources() {
        virtualDisplay.getAndSet(null)?.release()
        reader.getAndSet(null)?.close()
        monitorSurface.getAndSet(null)?.let { surface ->
            runCatching { surface.release() }
                .onFailure { Ln.w("releaseResources: release monitor surface failed: ${it.message}") }
        }
        displayId.set(DISPLAY_NONE)
    }

    private fun onImageAvailable(reader: ImageReader) {
        FrameCaptureHelper.processImage(reader) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastPreviewTime >= DefaultDisplayConfig.FRAME_INTERVAL_MS) {
                monitorSurface.get()?.let { surface ->
                    FrameCaptureHelper.renderToMonitor(it, surface)
                }
                lastPreviewTime = now
            }
        }

    }

    private fun createVirtualDisplay(surface: Surface, cfg: DisplayConfig) {
        val flags = buildDisplayFlags()
        val vd = ServiceManager.getDisplayManager()
            .createNewVirtualDisplay(
                VD_NAME,
                cfg.width,
                cfg.height,
                cfg.dpi,
                surface,
                flags
            )
        virtualDisplay.set(vd)
        val vdId = vd.display.displayId
        displayId.set(vdId)
        Ln.i("Virtual display created: id=$vdId, size=${cfg.width}x${cfg.height}, dpi=${cfg.dpi}")
    }

    private fun buildDisplayFlags(): Int {
        var flags = (VIRTUAL_DISPLAY_FLAG_PUBLIC
                or VIRTUAL_DISPLAY_FLAG_PRESENTATION
                or VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                or VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH
                or VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT)

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
                        or VIRTUAL_DISPLAY_FLAG_DEVICE_DISPLAY_GROUP)
            }
        }
        return flags
    }
}
