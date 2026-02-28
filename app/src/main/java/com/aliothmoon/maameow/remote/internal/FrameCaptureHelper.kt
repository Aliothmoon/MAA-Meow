package com.aliothmoon.maameow.remote.internal

import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.aliothmoon.maameow.bridge.NativeBridgeLib
import com.aliothmoon.maameow.third.Ln

object FrameCaptureHelper {

    private val srcRect = Rect()
    private val dstRect = Rect()

    fun processImage(reader: ImageReader, onFrameUse: ((HardwareBuffer) -> Unit)? = null) {
        val image = reader.acquireLatestImage() ?: return
        try {
            val hb = image.hardwareBuffer ?: run {
                Ln.w("processImage: hardwareBuffer is null")
                return
            }
            try {
                NativeBridgeLib.copyFrameFromHardwareBuffer(hb)
                onFrameUse?.invoke(hb)
            } catch (e: Exception) {
                Ln.w("processImage onFrameUse failed: ${e.message}")
            } finally {
                hb.close()
            }

        } catch (e: IllegalStateException) {
            Ln.w("processImage failed: ${e.message}")
        } finally {
            image.close()
        }
    }

    fun renderToMonitor(hb: HardwareBuffer, surface: Surface) {
        if (!surface.isValid) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val canvas = surface.lockHardwareCanvas()
                try {
                    val bitmap = Bitmap.wrapHardwareBuffer(hb, null)
                    if (bitmap != null) {
                        srcRect.set(0, 0, bitmap.width, bitmap.height)
                        dstRect.set(0, 0, canvas.width, canvas.height)
                        canvas.drawBitmap(bitmap, srcRect, dstRect, null)
                    }
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }
            } catch (e: Exception) {
                Ln.w("renderToPreview failed: ${e.message}")
            }
        }
    }

    fun createCaptureHandler(name: String): Handler {
        return Handler(HandlerThread(name).apply { start() }.looper)
    }
}
