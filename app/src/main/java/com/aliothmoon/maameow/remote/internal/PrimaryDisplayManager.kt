package com.aliothmoon.maameow.remote.internal

import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Build
import android.os.IBinder
import android.view.Display
import android.view.Surface
import com.aliothmoon.maameow.bridge.NativeBridgeLib
import com.aliothmoon.maameow.constant.AndroidVersions
import com.aliothmoon.maameow.constant.DefaultDisplayConfig.VD_NAME
import com.aliothmoon.maameow.third.DisplayInfo
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.wrappers.ServiceManager
import com.aliothmoon.maameow.third.wrappers.SurfaceControl
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object PrimaryDisplayManager {

    private const val STATE_IDLE = 0
    private const val STATE_CAPTURING = 1

    private val state = AtomicInteger(STATE_IDLE)
    private val display = AtomicReference<IBinder?>()
    private val virtualDisplay = AtomicReference<VirtualDisplay?>()
    private val reader = AtomicReference<ImageReader?>()
    private val setup = AtomicBoolean(false)

    const val DISPLAY_ID = Display.DEFAULT_DISPLAY

    private val displayInfo = AtomicReference<DisplayInfo>()


    private val handler by lazy {
        FrameCaptureHelper.createCaptureHandler("PrimaryCapture")
    }

    private fun getDisplayInfo(): DisplayInfo {
        return ServiceManager.getDisplayManager().getDisplayInfo(DISPLAY_ID)
    }

    private val listenerHandler by lazy {
        FrameCaptureHelper.createCaptureHandler("DisplayListener")
    }


    private fun onDisplayChange(displayId: Int) {
        if (displayId != DISPLAY_ID) {
            return
        }
        val info = getDisplayInfo()
        val oldInfo = displayInfo.get()
        if (info.size != oldInfo?.size) {
            Ln.i("Display changed: ${oldInfo?.size} -> ${info.size}, triggering restart")
            displayInfo.set(info)
            restart()
        }
    }

    private fun setup() {
        ServiceManager.getDisplayManager().registerDisplayListener({
            onDisplayChange(it)
        }, listenerHandler)
        onDisplayChange(DISPLAY_ID)
    }

    fun start(): Int {
        if (!setup.get()) {
            setup()
            setup.set(true)
        }
        if (!state.compareAndSet(STATE_IDLE, STATE_CAPTURING)) {
            Ln.w("start: already capturing")
            return DISPLAY_ID
        }
        return startInternal()
    }

    fun stop() {
        if (!state.compareAndSet(STATE_CAPTURING, STATE_IDLE)) {
            return
        }
        releaseResources()
    }

    fun restart() {
        if (state.get() != STATE_CAPTURING) {
            return
        }
        releaseResources()
        startInternal()
    }

    private fun startInternal(): Int {
        val info = displayInfo.get()
        val width = info.size.width
        val height = info.size.height
        NativeBridgeLib.initFrameBuffers(width, height)
        val r = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 5)
        r.setOnImageAvailableListener({ onImageAvailable(it) }, handler)
        reader.set(r)
        createVirtualDisplay(r.surface, info)
        return DISPLAY_ID
    }

    private fun releaseResources() {
        virtualDisplay.getAndSet(null)?.release()
        display.getAndSet(null)?.let { SurfaceControl.destroyDisplay(it) }
        reader.getAndSet(null)?.close()
    }

    private fun onImageAvailable(reader: ImageReader) {
        FrameCaptureHelper.processImage(reader)
    }

    private fun createVirtualDisplay(surface: Surface, displayInfo: DisplayInfo) {
        val width = displayInfo.size.width
        val height = displayInfo.size.height
        try {
            val vd = ServiceManager.getDisplayManager()
                .createVirtualDisplay(
                    VD_NAME,
                    width,
                    height,
                    DISPLAY_ID,
                    surface
                )
            virtualDisplay.set(vd)
            Ln.d("Display: using DisplayManager API")
        } catch (displayManagerException: Exception) {
            try {
                val vd = createDisplay()
                display.set(vd)

                val deviceSize = displayInfo.size
                val layerStack = displayInfo.layerStack
                val rect = deviceSize.toRect()
                setDisplaySurface(vd, surface, rect, rect, layerStack)
                Ln.d("Display: using SurfaceControl API")
            } catch (surfaceControlException: Exception) {
                Ln.e("Could not create display using DisplayManager", displayManagerException)
                Ln.e("Could not create display using SurfaceControl", surfaceControlException)
                throw AssertionError("Could not create display")
            }
        }
    }

    private fun createDisplay(): IBinder {
        // Since Android 12 (preview), secure displays could not be created with shell permissions anymore.
        // On Android 12 preview, SDK_INT is still R (not S), but CODENAME is "S".
//        val secure =
//            Build.VERSION.SDK_INT < AndroidVersions.API_30_ANDROID_11 || (Build.VERSION.SDK_INT == AndroidVersions.API_30_ANDROID_11
//                    && "S" != Build.VERSION.CODENAME)
        return SurfaceControl.createDisplay(VD_NAME, false)
    }

    private fun setDisplaySurface(
        display: IBinder?,
        surface: Surface?,
        deviceRect: Rect?,
        displayRect: Rect?,
        layerStack: Int
    ) {
        SurfaceControl.openTransaction()
        try {
            SurfaceControl.setDisplaySurface(display, surface)
            SurfaceControl.setDisplayProjection(display, 0, deviceRect, displayRect)
            SurfaceControl.setDisplayLayerStack(display, layerStack)
        } finally {
            SurfaceControl.closeTransaction()
        }
    }
}