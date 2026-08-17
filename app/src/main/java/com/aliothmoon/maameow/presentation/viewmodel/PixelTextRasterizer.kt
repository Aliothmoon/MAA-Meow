package com.aliothmoon.maameow.presentation.viewmodel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.aliothmoon.maameow.domain.models.pixelart.PreparedImage
import com.aliothmoon.maameow.domain.service.pixelart.PixelPaintHelper
import java.text.BreakIterator
import kotlin.math.ceil
import kotlin.math.min

/**
 * 把粘贴来的文字栅格化成 24×24 黑字白底点阵，直接当像素画原图
 *
 * 对齐 MaaWpfGui PixelPaintHelper.RenderTextToBitmap：
 * 含中文按自适应网格（1 字占满、2~4 字分 2 列）逐格绘制；
 * 纯英文数字整串单行横排，二分放大字号后裁掉墨迹空白，1:1 放入不降采样
 */
object PixelTextRasterizer {

    /** 只取前 4 个字，再多 24×24 也看不清 */
    private const val MAX_CHARS = 4

    private const val SIZE = PixelPaintHelper.GRID_SIZE

    /** 二分字号区间（px）与轮数，与 WpfGui 的 DrawInkFit 一致 */
    private const val MIN_EM = 8f
    private const val MAX_EM = 128f
    private const val PROBE_ROUNDS = 12

    /** 墨迹判定阈值：关掉抗锯齿后 alpha 非 0 即 255，取 16 只是兜底 */
    private const val INK_ALPHA = 16

    private const val WHITE = 0xFFFFFFFF.toInt()

    fun render(text: String): PreparedImage? {
        val chars = takeGraphemes(text)
        if (chars.isEmpty()) return null

        val pixels = if (chars.any(::hasNonAscii)) {
            rasterizeGrid(chars) ?: return null
        } else {
            IntArray(SIZE * SIZE) { WHITE }.also { blitInkFit(it, chars.joinToString("")) }
        }
        return PreparedImage(pixels, SIZE, SIZE)
    }

    /** 按字素切分，代理对和组合字符不会被拆开 */
    private fun takeGraphemes(text: String): List<String> {
        val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
        val result = ArrayList<String>(MAX_CHARS)
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE && result.size < MAX_CHARS) {
            // 空格也占一个名额，和 WpfGui 一致：｢AB CD｣ 取到的是 ｢AB C｣
            result.add(text.substring(start, end))
            start = end
            end = iterator.next()
        }
        return result
    }

    private fun hasNonAscii(cluster: String) = cluster.any { it.code >= 128 }

    private fun basePaint(sizePx: Float) = Paint().apply {
        isAntiAlias = false
        isSubpixelText = false
        isLinearText = false
        color = Color.BLACK
        typeface = Typeface.DEFAULT
        textSize = sizePx
    }

    /** 中文走自适应网格：1 字占满整张，2~4 字分 2 列 */
    private fun rasterizeGrid(chars: List<String>): IntArray? {
        val cols = min(chars.size, 2)
        val rows = (chars.size + cols - 1) / cols
        val cellW = SIZE / cols
        val cellH = SIZE / rows
        val paint = basePaint(min(cellW, cellH).toFloat()).apply { textAlign = Paint.Align.CENTER }
        val metrics = paint.fontMetrics

        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            chars.forEachIndexed { i, cluster ->
                val left = (i % cols) * cellW
                val top = (i / cols) * cellH
                val cx = left + cellW / 2f
                // 基线按字形中线摆正，而不是把格心当基线
                val baseline = top + cellH / 2f - (metrics.ascent + metrics.descent) / 2f
                canvas.drawText(cluster, cx, baseline, paint)
            }
            IntArray(SIZE * SIZE).also { bitmap.getPixels(it, 0, SIZE, 0, 0, SIZE, SIZE) }
        } catch (_: IllegalArgumentException) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    /** 二分找出墨迹恰能放进 24×24 的最大字号，再 1:1 居中贴上去，不做降采样 */
    private fun blitInkFit(target: IntArray, text: String) {
        var lo = MIN_EM
        var hi = MAX_EM
        repeat(PROBE_ROUNDS) {
            val mid = (lo + hi) / 2f
            val probe = renderInk(text, mid)
            if (probe != null && probe.width <= SIZE && probe.height <= SIZE) lo = mid else hi = mid
        }

        val ink = renderInk(text, lo) ?: return
        val dx = (SIZE - ink.width) / 2
        val dy = (SIZE - ink.height) / 2
        for (y in 0 until ink.height) {
            val ty = dy + y
            if (ty < 0 || ty >= SIZE) continue
            for (x in 0 until ink.width) {
                val tx = dx + x
                // 超出 24×24 的部分直接丢掉，等价于 WpfGui 那边 DrawImage 的裁剪
                if (tx < 0 || tx >= SIZE) continue
                val c = ink.pixels[y * ink.width + x]
                if ((c ushr 24) <= INK_ALPHA) continue
                target[ty * SIZE + tx] = c or 0xFF000000.toInt()
            }
        }
    }

    private class Ink(val pixels: IntArray, val width: Int, val height: Int)

    /** 透明底渲染一遍，扫 alpha 取墨迹外接框并裁掉四周空白 */
    private fun renderInk(text: String, sizePx: Float): Ink? {
        val paint = basePaint(sizePx).apply { textAlign = Paint.Align.LEFT }
        val metrics = paint.fontMetricsInt
        val pad = ceil(sizePx * 0.2).toInt()
        val w = ceil(paint.measureText(text)).toInt() + pad * 2
        val h = (metrics.bottom - metrics.top) + pad * 2
        if (w <= 0 || h <= 0) return null

        val tmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        try {
            Canvas(tmp).drawText(text, pad.toFloat(), (pad - metrics.top).toFloat(), paint)
            tmp.getPixels(pixels, 0, w, 0, 0, w, h)
        } finally {
            tmp.recycle()
        }

        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            val base = y * w
            for (x in 0 until w) {
                if ((pixels[base + x] ushr 24) <= INK_ALPHA) continue
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return null

        val iw = maxX - minX + 1
        val ih = maxY - minY + 1
        val ink = IntArray(iw * ih)
        for (y in 0 until ih) {
            System.arraycopy(pixels, (minY + y) * w + minX, ink, y * iw, iw)
        }
        return Ink(ink, iw, ih)
    }
}
