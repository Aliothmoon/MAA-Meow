package com.aliothmoon.maameow.notification

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import com.caverock.androidsvg.SVG
import java.io.FileInputStream

/**
 * 自定义进度条图标解码：先按像素格式（PNG/JPG/WebP/GIF/BMP）用 BitmapFactory 解码，
 * 失败时尝试用 AndroidSVG 渲染矢量图（SVG / 内嵌 SVG 的 XML），统一缩放后返回位图。
 * 设置页预览与 TaskExecutionService 共用，保证所见即所得。
 */
object TrackerIconDecoder {

    fun decode(path: String, targetSize: Int = 72): Bitmap? {
        if (path.isEmpty()) return null

        // 像素格式优先
        BitmapFactory.decodeFile(path)?.let { bm ->
            return scale(bm, targetSize)
        }

        // 矢量格式：AndroidSVG 解析 SVG/XML 内容
        return runCatching {
            val svg = SVG.getFromInputStream(FileInputStream(path))
            val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(
                Canvas(bitmap),
                android.graphics.RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat())
            )
            bitmap
        }.getOrNull()
    }

    private fun scale(source: Bitmap, targetSize: Int): Bitmap {
        if (source.width <= targetSize && source.height <= targetSize) return source
        val ratio = targetSize.toFloat() / maxOf(source.width, source.height)
        val w = (source.width * ratio).toInt().coerceAtLeast(1)
        val h = (source.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, w, h, true)
        if (scaled !== source) source.recycle()
        return scaled
    }
}