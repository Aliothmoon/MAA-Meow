package com.aliothmoon.maameow.data.notification.live

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 自定义图标解码：按像素格式（PNG / JPG / WebP / GIF 等）解码，并统一缩放到目标尺寸。
 * 设置页预览与通知链路共用，保证所见即所得。
 */
object TrackerIconDecoder {

    fun decode(path: String, targetSize: Int = 72): Bitmap? {
        if (path.isEmpty()) return null
        // 先读尺寸计算采样率，再解码以降低峰值内存
        val bitmap = decodeWithSample(path, targetSize) ?: return null
        return scale(bitmap, targetSize)
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
