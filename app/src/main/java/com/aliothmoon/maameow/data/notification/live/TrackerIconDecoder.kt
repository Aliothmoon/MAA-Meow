package com.aliothmoon.maameow.data.notification.live

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface

/**
 * 自定义图标解码：按像素格式（PNG / JPG / WebP / GIF 等）解码，按 EXIF 方向摆正后统一缩放到目标尺寸。
 * 设置页预览与通知链路共用，保证所见即所得。
 */
object TrackerIconDecoder {

    fun decode(path: String, targetSize: Int = 72): Bitmap? {
        if (path.isEmpty()) return null
        // 先读尺寸计算采样率，再解码以降低峰值内存
        val bitmap = decodeWithSample(path, targetSize) ?: return null
        return scale(applyExifOrientation(path, bitmap), targetSize)
    }

    /**
     * 相机拍的照片文件里存的是原始像素 + EXIF 方向，不摆正会显示成转倒/镜像。
     * 解析失败按正常方向处理（与 BackgroundImageStore.decodeSource 同一套映射）。
     */
    private fun applyExifOrientation(path: String, source: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) {
            return source
        }
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    postRotate(90f)
                    postScale(-1f, 1f)
                }

                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    postRotate(-90f)
                    postScale(-1f, 1f)
                }

                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            }
        }
        return try {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } finally {
            source.recycle()
        }
    }

    /**
     * 先用 inJustDecodeBounds 读取原始尺寸，计算 inSampleSize 使解码后
     * 的宽高不超过 targetSize×2，再用采样率解码以减少峰值内存。
     */
    private fun decodeWithSample(path: String, targetSize: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        val (origW, origH) = opts.outWidth to opts.outHeight
        if (origW <= 0 || origH <= 0) return null

        val sample = maxOf(1, origW / (targetSize * 2), origH / (targetSize * 2))
        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    /** 统一缩放到 targetSize，保持宽高比。小图也放大，确保各处显示尺寸一致。 */
    private fun scale(source: Bitmap, targetSize: Int): Bitmap {
        if (source.width == targetSize && source.height == targetSize) return source
        val ratio = targetSize.toFloat() / maxOf(source.width, source.height)
        val w = (source.width * ratio).toInt().coerceAtLeast(1)
        val h = (source.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, w, h, true)
        if (scaled !== source) source.recycle()
        return scaled
    }
}
