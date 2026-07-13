package com.aliothmoon.maameow.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object BitmapUtils {
    // Avoid loading multi-dozen-MB originals fully into RAM for picker fallbacks.
    private const val MAX_CONTENT_BYTES = 25L * 1024 * 1024

    fun loadDownsampledBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 2048,
    ): Bitmap? {
        return try {
            when (uri.scheme) {
                null, "file" -> {
                    val path = uri.path ?: return null
                    loadDownsampledBitmap(File(path), maxDimension)
                }
                else -> loadFromContentUri(context, uri, maxDimension)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun loadDownsampledBitmap(
        context: Context,
        uriString: String,
        maxDimension: Int = 2048,
    ): Bitmap? {
        val uri = parseUri(uriString)
        return loadDownsampledBitmap(context, uri, maxDimension)
    }

    fun loadDownsampledBitmap(
        file: File,
        maxDimension: Int = 2048,
    ): Bitmap? {
        if (!file.exists() || file.length() <= 0L) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
            applyExifOrientation(file, decoded)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Content providers may revoke temporary grants after the first open.
     * Read the stream once into memory, then decode bounds + pixels + EXIF from bytes.
     */
    private fun loadFromContentUri(
        context: Context,
        uri: Uri,
        maxDimension: Int,
    ): Bitmap? {
        val bytes = openInputStream(context, uri)?.use { input ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(chunk)
                if (n <= 0) break
                total += n
                if (total > MAX_CONTENT_BYTES) {
                    // Too large for in-memory content decode; fail over to File-based path if caller copied.
                    return null
                }
                buffer.write(chunk, 0, n)
            }
            buffer.toByteArray()
        } ?: return null
        if (bytes.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null

        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED,
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_UNDEFINED
        }
        return transformByOrientation(decoded, orientation)
    }

    private fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
        val maxDim = max(width, height)
        if (maxDim <= maxDimension) return 1
        var s = 1
        while (maxDim / s > maxDimension) s *= 2
        return s
    }

    private fun openInputStream(context: Context, uri: Uri): InputStream? {
        return try {
            when (uri.scheme) {
                null, "file" -> {
                    val path = uri.path ?: return null
                    val file = File(path)
                    if (!file.exists()) null else FileInputStream(file)
                }
                else -> context.contentResolver.openInputStream(uri)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun applyExifOrientation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED,
            )
        } catch (_: Exception) {
            return bitmap
        }
        return transformByOrientation(bitmap, orientation)
    }

    private fun transformByOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return try {
            val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (oriented !== bitmap && !bitmap.isRecycled) {
                bitmap.recycle()
            }
            oriented
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun parseUri(uriString: String): Uri {
        val uri = Uri.parse(uriString)
        return if (uri.scheme == null) Uri.fromFile(File(uriString)) else uri
    }
    /**
     * Approximate Gaussian blur for API < 31 where [androidx.compose.ui.draw.blur] is a no-op.
     * Operates on a downscaled **mutable** ARGB copy for speed; never mutates or recycles [source].
     *
     * Note: [Bitmap.createScaledBitmap] often returns an immutable bitmap when [source] is
     * immutable. [stackBlurInPlace] needs [Bitmap.setPixels], so we always force a mutable copy.
     *
     * @param radius blur radius in pixels on the downscaled bitmap (0 = return source).
     * @param maxDimension longest edge after downscale before blurring.
     */
    fun blurForWallpaper(
        source: Bitmap,
        radius: Int,
        maxDimension: Int = 720,
    ): Bitmap {
        if (source.isRecycled || source.width <= 0 || source.height <= 0) return source
        val r = radius.coerceAtLeast(0)
        if (r == 0) return source

        val maxDim = max(source.width, source.height)
        // Intermediate may be immutable (especially createScaledBitmap from immutable source).
        var intermediate: Bitmap? = null
        val working: Bitmap = try {
            if (maxDim > maxDimension) {
                val scale = maxDimension.toFloat() / maxDim.toFloat()
                val w = (source.width * scale).toInt().coerceAtLeast(1)
                val h = (source.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(source, w, h, true)
                intermediate = if (scaled !== source) scaled else null
                ensureMutableArgb8888(scaled, recycleIfCopied = scaled !== source)
            } else {
                // Always copy: never blur the caller's source in place.
                source.copy(Bitmap.Config.ARGB_8888, true)
                    ?: return source
            }
        } catch (_: Exception) {
            intermediate?.takeIf { !it.isRecycled }?.recycle()
            return source
        } ?: run {
            // copy failed after scale: drop intermediate if it is not the working bitmap.
            intermediate?.takeIf { !it.isRecycled }?.recycle()
            return source
        }

        return try {
            stackBlurInPlace(working, r.coerceAtMost(50))
            working
        } catch (_: Exception) {
            if (working !== source && !working.isRecycled) working.recycle()
            source
        }
    }

    /**
     * Returns a mutable ARGB_8888 bitmap. If [bitmap] is already mutable ARGB_8888, returns it as-is.
     * Otherwise copies; when [recycleIfCopied] is true and a new bitmap is created, recycles [bitmap]
     * (only safe when [bitmap] is not owned by the caller / is an intermediate).
     */
    private fun ensureMutableArgb8888(
        bitmap: Bitmap,
        recycleIfCopied: Boolean,
    ): Bitmap? {
        if (!bitmap.isRecycled &&
            bitmap.isMutable &&
            bitmap.config == Bitmap.Config.ARGB_8888
        ) {
            return bitmap
        }
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        if (recycleIfCopied && copy !== bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        return copy
    }

    /**
     * Stack Blur (Mario Klingemann) — good quality / cost tradeoff for software fallback.
     * Mutates [bitmap] in place; requires ARGB_8888 mutable bitmap.
     */
    private fun stackBlurInPlace(bitmap: Bitmap, radius: Int) {
        if (radius < 1) return
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(max(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        i = 0
        while (i < 256 * divsum) {
            dv[i] = i / divsum
            i++
        }

        yi = 0
        yw = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        y = 0
        while (y < h) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            i = -radius
            while (i <= radius) {
                p = pix[yi + min(wm, max(i, 0))]
                sir = stack[i + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                i++
            }
            stackpointer = radius
            x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                if (y == 0) {
                    vmin[x] = min(x + radius + 1, wm)
                }
                p = pix[yw + vmin[x]]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                yi++
                x++
            }
            yw += w
            y++
        }

        x = 0
        while (x < w) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            yp = -radius * w
            i = -radius
            while (i <= radius) {
                yi = max(0, yp) + x
                sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - abs(i)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (i < hm) {
                    yp += w
                }
                i++
            }
            yi = x
            stackpointer = radius
            y = 0
            while (y < h) {
                pix[yi] =
                    (0xff000000.toInt() and pix[yi]) or
                        (dv[rsum] shl 16) or
                        (dv[gsum] shl 8) or
                        dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                if (x == 0) {
                    vmin[y] = min(y + r1, hm) * w
                }
                p = x + vmin[y]
                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                yi += w
                y++
            }
            x++
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
    }
}
