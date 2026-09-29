package com.aliothmoon.maameow.data.notification.live

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.aliothmoon.maameow.utils.orientByExif

/** 自定义图标解码：降采样、EXIF 摆正后统一缩放到 [TARGET_SIZE] */
object TrackerIconDecoder {

    const val TARGET_SIZE = 72

    fun decode(path: String): Bitmap? {
        if (path.isEmpty()) return null
        val (width, height) = bounds(path) ?: return null
        // 解码到不超过目标两倍，压峰值内存
        val sample = maxOf(1, width / (TARGET_SIZE * 2), height / (TARGET_SIZE * 2))
        val bitmap = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        return scale(orientByExif(path, bitmap))
    }

    /** 只读文件头判断是不是图片 */
    fun isImage(path: String): Boolean = bounds(path) != null

    private fun bounds(path: String): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        return (opts.outWidth to opts.outHeight).takeIf { it.first > 0 && it.second > 0 }
    }

    // 等比缩放，小图也放大，保证各处尺寸一致
    private fun scale(source: Bitmap): Bitmap {
        if (source.width == TARGET_SIZE && source.height == TARGET_SIZE) return source
        val ratio = TARGET_SIZE.toFloat() / maxOf(source.width, source.height)
        val w = (source.width * ratio).toInt().coerceAtLeast(1)
        val h = (source.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, w, h, true)
        if (scaled !== source) source.recycle()
        return scaled
    }
}
