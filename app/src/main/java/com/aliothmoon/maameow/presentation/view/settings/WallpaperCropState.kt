package com.aliothmoon.maameow.presentation.view.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Stable
class CropState {
    var scale by mutableFloatStateOf(1f)
        internal set
    var panX by mutableFloatStateOf(0f)
        internal set
    var panY by mutableFloatStateOf(0f)
        internal set
    var rotationDegrees by mutableFloatStateOf(0f)
        internal set
    var screenW by mutableFloatStateOf(0f)
        internal set
    var screenH by mutableFloatStateOf(0f)
        internal set
    var cropW by mutableFloatStateOf(0f)
        internal set
    var cropH by mutableFloatStateOf(0f)
        internal set
    var cropLeft by mutableFloatStateOf(0f)
        internal set
    var cropTop by mutableFloatStateOf(0f)
        internal set

    // Source image pixel size, used for pan clamping / cover scale.
    var imageW by mutableFloatStateOf(0f)
        internal set
    var imageH by mutableFloatStateOf(0f)
        internal set

    fun reset(initScale: Float = 1f) {
        scale = initScale
        panX = 0f
        panY = 0f
        rotationDegrees = 0f
        ensureCoverScale()
    }

    fun restore(
        restoredScale: Float,
        restoredPanX: Float,
        restoredPanY: Float,
        restoredRotation: Float,
        fallbackScale: Float,
    ) {
        scale = restoredScale.coerceIn(MIN_SCALE, MAX_SCALE).takeIf { it.isFinite() } ?: fallbackScale
        panX = restoredPanX.takeIf { it.isFinite() } ?: 0f
        panY = restoredPanY.takeIf { it.isFinite() } ?: 0f
        rotationDegrees = normalizeDegrees(restoredRotation.takeIf { it.isFinite() } ?: 0f)
        ensureCoverScale()
    }

    fun rotateBy(degrees: Float) {
        // Snap 90° steps to exact multiples to avoid float drift on the button path.
        val next = normalizeDegrees(rotationDegrees + degrees)
        rotationDegrees = if (abs(degrees) % 90f < 0.01f || abs(abs(degrees) % 90f - 90f) < 0.01f) {
            (kotlin.math.round(next / 90f) * 90f).let { normalizeDegrees(it) }
        } else {
            next
        }
        ensureCoverScale()
    }

    /**
     * Apply pan/zoom/rotate with [pivotX]/[pivotY] as the rotation/scale center
     * (one finger stays put; the other orbits for 1:1 angle tracking).
     *
     * [rotationDegreesDelta] is already in degrees and applied 1:1 (no gain).
     * graphicsLayer still uses layer-center + pan; pan is compensated so the
     * pivot stays fixed under pure rotate/scale.
     */
    fun applyTransform(
        zoom: Float,
        panDeltaX: Float,
        panDeltaY: Float,
        rotationDegreesDelta: Float,
        pivotX: Float,
        pivotY: Float,
    ) {
        val oldScale = scale
        val oldRot = rotationDegrees
        val oldPanX = panX
        val oldPanY = panY
        val newScale = (oldScale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val newRot = if (rotationDegreesDelta != 0f) {
            normalizeDegrees(oldRot + rotationDegreesDelta)
        } else {
            oldRot
        }

        // graphicsLayer (transformOrigin=Center):
        //   screen = R(scale * (local - center)) + center + pan
        val centerX = if (screenW > 0f) screenW / 2f else pivotX
        val centerY = if (screenH > 0f) screenH / 2f else pivotY
        // pivotX/Y are the CURRENT pivot-finger position. Recover the pre-pan
        // pivot so rotate/scale compensation is 1:1 around the finger and pan
        // is not double-counted when the pivot finger also moves.
        val oldPivotX = pivotX - panDeltaX
        val oldPivotY = pivotY - panDeltaY
        val mappedOldX = oldPivotX - centerX - oldPanX
        val mappedOldY = oldPivotY - centerY - oldPanY
        val (localX, localY) = unmapAroundOrigin(mappedOldX, mappedOldY, oldScale, oldRot)
        val (mappedNewX, mappedNewY) = mapAroundOrigin(localX, localY, newScale, newRot)

        scale = newScale
        rotationDegrees = newRot
        // Move with the pivot finger, then keep that contact point fixed under
        // pure rotate/scale (Gallery / Matrix.postRotate(pivot) style).
        panX = oldPanX + panDeltaX + (mappedOldX - mappedNewX)
        panY = oldPanY + panDeltaY + (mappedOldY - mappedNewY)
    }

    fun applyPan(panDeltaX: Float, panDeltaY: Float) {
        panX += panDeltaX
        panY += panDeltaY
    }

    /**
     * Map a vector from layer-center coords through uniform scale then
     * clockwise rotation (matches Compose rotationZ / Android Matrix).
     */
    private fun mapAroundOrigin(
        x: Float,
        y: Float,
        s: Float,
        degrees: Float,
    ): Pair<Float, Float> {
        val sx = x * s
        val sy = y * s
        val rad = Math.toRadians(degrees.toDouble())
        val c = cos(rad).toFloat()
        val sn = sin(rad).toFloat()
        // Clockwise: x' = x*cos + y*sin, y' = -x*sin + y*cos
        return (sx * c + sy * sn) to (-sx * sn + sy * c)
    }

    /** Inverse of [mapAroundOrigin]: counter-clockwise unrotate then unscale. */
    private fun unmapAroundOrigin(
        x: Float,
        y: Float,
        s: Float,
        degrees: Float,
    ): Pair<Float, Float> {
        val safe = if (s == 0f) 1f else s
        val rad = Math.toRadians(degrees.toDouble())
        val c = cos(rad).toFloat()
        val sn = sin(rad).toFloat()
        // Inverse of clockwise: x = x'cos - y'sin, y = x'sin + y'cos, then /s
        val ux = x * c - y * sn
        val uy = x * sn + y * c
        return (ux / safe) to (uy / safe)
    }

    /**
     * After rotation the axis-aligned bounds change; boost scale if needed so the
     * image still fully covers the crop window (no black bars), then clamp pan.
     */
    fun ensureCoverScale() {
        if (screenW <= 0f || screenH <= 0f || cropW <= 0f || cropH <= 0f) return
        if (imageW <= 0f || imageH <= 0f) return

        val baseScale = min(screenW / imageW, screenH / imageH)
        if (baseScale <= 0f) return
        val dispW = imageW * baseScale
        val dispH = imageH * baseScale
        val rad = Math.toRadians(rotationDegrees.toDouble())
        val c = abs(cos(rad)).toFloat()
        val s = abs(sin(rad)).toFloat()
        val unitW = dispW * c + dispH * s
        val unitH = dispW * s + dispH * c
        if (unitW <= 0f || unitH <= 0f) return

        val minCover = max(cropW / unitW, cropH / unitH)
        if (scale < minCover) {
            scale = minCover.coerceIn(MIN_SCALE, MAX_SCALE)
        }
        clampPan()
    }

    /**
     * Keep the scaled/rotated image covering the crop window so saves do not include
     * empty black borders from over-panning.
     */
    fun clampPan() {
        if (screenW <= 0f || screenH <= 0f || cropW <= 0f || cropH <= 0f) return
        if (imageW <= 0f || imageH <= 0f) return

        val baseScale = min(screenW / imageW, screenH / imageH)
        val dispW = imageW * baseScale * scale
        val dispH = imageH * baseScale * scale
        val rad = Math.toRadians(rotationDegrees.toDouble())
        val c = abs(cos(rad)).toFloat()
        val s = abs(sin(rad)).toFloat()
        val boundW = dispW * c + dispH * s
        val boundH = dispW * s + dispH * c

        // Max pan so the image AABB still fully covers the crop rect.
        val maxPanX = max(0f, (boundW - cropW) / 2f)
        val maxPanY = max(0f, (boundH - cropH) / 2f)
        panX = panX.coerceIn(-maxPanX, maxPanX)
        panY = panY.coerceIn(-maxPanY, maxPanY)
    }

    fun getCroppedBitmap(source: Bitmap, maxOutputDimension: Int = 2048): Bitmap? {
        val sw = screenW
        val sh = screenH
        // Geometry is written in SideEffect; refuse save until it is ready.
        if (sw <= 0f || sh <= 0f || cropW <= 0f || cropH <= 0f) return null
        val bw = source.width.toFloat()
        val bh = source.height.toFloat()
        if (bw <= 0f || bh <= 0f) return null
        val baseScale = min(sw / bw, sh / bh)
        if (baseScale <= 0f || scale <= 0f) return null

        // Output matches the crop window in screen space mapped back through user scale.
        // Rotation is baked by the draw matrix; size stays crop-aspect.
        var outW = (cropW / baseScale / scale).toInt().coerceAtLeast(1)
        var outH = (cropH / baseScale / scale).toInt().coerceAtLeast(1)
        val maxOut = maxOf(outW, outH)
        if (maxOut > maxOutputDimension) {
            val ratio = maxOutputDimension.toFloat() / maxOut.toFloat()
            outW = (outW * ratio).toInt().coerceAtLeast(1)
            outH = (outH * ratio).toInt().coerceAtLeast(1)
        }

        val m = Matrix()
        // Source pixels -> fit-centered screen pixels (matches ContentScale.Fit).
        m.postScale(baseScale, baseScale)
        m.postTranslate((sw - bw * baseScale) / 2f, (sh - bh * baseScale) / 2f)
        // User transforms: scale then rotate around screen center, then pan.
        // Matches Compose graphicsLayer order (scale -> rotate -> translate) with center pivot.
        m.postTranslate(-sw / 2f, -sh / 2f)
        m.postScale(scale, scale)
        m.postRotate(rotationDegrees)
        m.postTranslate(sw / 2f, sh / 2f)
        m.postTranslate(panX, panY)
        // Crop window -> output bitmap origin
        m.postTranslate(-cropLeft, -cropTop)
        val scaleFactor = cropW / outW.toFloat()
        m.postScale(1f / scaleFactor, 1f / scaleFactor)

        return try {
            val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawBitmap(source, m, null)
            result
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val MIN_SCALE = 0.3f
        const val MAX_SCALE = 5f

        fun normalizeDegrees(degrees: Float): Float {
            if (!degrees.isFinite()) return 0f
            var d = degrees % 360f
            if (d < 0f) d += 360f
            return d
        }
    }
}
