package com.aliothmoon.maameow.domain.service.pixelart

import com.aliothmoon.maameow.domain.models.pixelart.NormalizedRect
import com.aliothmoon.maameow.domain.models.pixelart.PixelArtPlan
import com.aliothmoon.maameow.domain.models.pixelart.PixelColorGroup
import com.aliothmoon.maameow.domain.models.pixelart.PixelConvertOptions
import com.aliothmoon.maameow.domain.models.pixelart.PixelDitherMode
import com.aliothmoon.maameow.domain.models.pixelart.PixelFitMode
import com.aliothmoon.maameow.domain.models.pixelart.PreparedImage
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 巡展像素画：原图 → 24×24 × 官方 40 色
 *
 * 逐行对齐 MaaWpfGui/Helper/PixelPaintHelper.cs，两端出图必须一致
 * 色序与游戏右侧色板一致；最近色用 CompuPhase 加权距离，不要换成 Lab
 */
object PixelPaintHelper {

    const val GRID_SIZE = 24
    const val COLOR_COUNT = 40

    /** 纯白在 40 色板中的下标 */
    const val WHITE_COLOR_INDEX = 3

    /** 官方 40 色 RGB，与游戏色板顺序一致 */
    val PALETTE = intArrayOf(
        0x222222, 0xB4B4B4, 0xEAE7DF, 0xFFFFFF,
        0xD32F36, 0x9C0A00, 0xD60C4A, 0xE6968D,
        0xFE9875, 0xF7D0C0, 0xFCEFEA, 0xFBF6E8,
        0xDCD2C8, 0xE2CEAB, 0xD56322, 0xD48C42,
        0xF29900, 0xF9C933, 0xFCE499, 0xB3B47A,
        0xC2DA72, 0x6C6E00, 0xB19155, 0xA98F74,
        0xAA9228, 0x3F2B12, 0x74491F, 0x534658,
        0x2A2446, 0x394599, 0x5A459D, 0xBAA3D7,
        0xB6BCDF, 0xA9ACBE, 0x63ABB9, 0xB4D2DC,
        0x91D8E6, 0x47AEA0, 0xB6D3C8, 0x273864,
    )

    private val PALETTE_R = IntArray(COLOR_COUNT) { (PALETTE[it] shr 16) and 0xFF }
    private val PALETTE_G = IntArray(COLOR_COUNT) { (PALETTE[it] shr 8) and 0xFF }
    private val PALETTE_B = IntArray(COLOR_COUNT) { PALETTE[it] and 0xFF }

    // ==================== 入口 ====================

    fun convert(
        pixels: IntArray,
        width: Int,
        height: Int,
        options: PixelConvertOptions,
        skipWhite: Boolean = true,
    ): PixelArtPlan = convert(prepare(pixels, width, height, options.trimEmptyBorder), options, skipWhite)

    /** 用预处理好的图转换，适合反复调参实时预览 */
    fun convert(
        prepared: PreparedImage,
        options: PixelConvertOptions,
        skipWhite: Boolean = true,
    ): PixelArtPlan {
        val sample = sampleToGrid(prepared, options)
        applyCssLikeFilters(
            sample,
            options.brightnessPercent / 100.0,
            options.contrastPercent / 100.0,
            options.saturationPercent / 100.0,
        )
        val matrix = quantize(sample, options.dither)
        return PixelArtPlan(GRID_SIZE, matrix, buildGroups(matrix, skipWhite))
    }

    /** 解码后（可选）去边，结果可复用于多次转换 */
    fun prepare(pixels: IntArray, width: Int, height: Int, trimEmptyBorder: Boolean = true): PreparedImage {
        val src = PreparedImage(pixels, width, height)
        return if (trimEmptyBorder) trimBorder(src) ?: src else src
    }

    // ==================== 距离与最近色 ====================

    /** CompuPhase 加权 RGB 距离 */
    fun colorDistance(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Double {
        val rmean = (r1 + r2) / 2.0
        val dr = (r1 - r2).toDouble()
        val dg = (g1 - g2).toDouble()
        val db = (b1 - b2).toDouble()
        return (2.0 + rmean / 256.0) * dr * dr +
            4.0 * dg * dg +
            (2.0 + (255.0 - rmean) / 256.0) * db * db
    }

    fun nearestPaletteIndex(r: Int, g: Int, b: Int): Int {
        var best = 0
        var bestD = Double.MAX_VALUE
        for (i in 0 until COLOR_COUNT) {
            val d = colorDistance(r, g, b, PALETTE_R[i], PALETTE_G[i], PALETTE_B[i])
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    // ==================== 分组 ====================

    fun buildGroups(matrix: IntArray, skipWhite: Boolean): List<PixelColorGroup> {
        val buckets = Array(COLOR_COUNT) { ArrayList<IntArray>() }
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                val idx = matrix[y * GRID_SIZE + x]
                if (skipWhite && idx == WHITE_COLOR_INDEX) continue
                buckets[idx].add(intArrayOf(x, y))
            }
        }
        val groups = ArrayList<PixelColorGroup>()
        for (c in 0 until COLOR_COUNT) {
            if (buckets[c].isEmpty()) continue
            groups.add(PixelColorGroup(c, buckets[c]))
        }
        return groups
    }

    // ==================== 去边 ====================

    private fun isContent(argb: Int): Boolean {
        val a = (argb ushr 24) and 0xFF
        if (a < 16) return false
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        // 近白视为空白边
        return r < 250 || g < 250 || b < 250
    }

    private fun trimBorder(src: PreparedImage): PreparedImage? {
        val w = src.width
        val h = src.height
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1

        for (y in 0 until h) {
            val base = y * w
            for (x in 0 until w) {
                if (!isContent(src.pixels[base + x])) continue
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }
        if (maxX < minX || maxY < minY) return null

        val nw = maxX - minX + 1
        val nh = maxY - minY + 1
        if (nw == w && nh == h) return src

        val out = IntArray(nw * nh)
        for (y in 0 until nh) {
            System.arraycopy(src.pixels, (minY + y) * w + minX, out, y * nw, nw)
        }
        return PreparedImage(out, nw, nh)
    }

    // ==================== 采样 ====================

    /** 网格缓冲：每格 3 个 double，行优先 */
    private fun index(x: Int, y: Int) = (y * GRID_SIZE + x) * 3

    /** 按 fit + 可选取景采样到 24×24 浮点 RGB */
    private fun sampleToGrid(src: PreparedImage, options: PixelConvertOptions): DoubleArray {
        val view = normalizeViewRect(options.contentView ?: NormalizedRect())
        val srcX0 = view.x * src.width
        val srcY0 = view.y * src.height
        val srcW = max(1e-6, view.width * src.width)
        val srcH = max(1e-6, view.height * src.height)

        val mapX0: Double
        val mapY0: Double
        val mapW: Double
        val mapH: Double
        when (options.fit) {
            PixelFitMode.STRETCH -> {
                mapX0 = srcX0
                mapY0 = srcY0
                mapW = srcW
                mapH = srcH
            }

            PixelFitMode.CONTAIN -> {
                // 外接矩形包含整张源图，等比装入目标并留白
                val scale = max(srcW / GRID_SIZE, srcH / GRID_SIZE)
                mapW = GRID_SIZE * scale
                mapH = GRID_SIZE * scale
                mapX0 = srcX0 + (srcW - mapW) / 2.0
                mapY0 = srcY0 + (srcH - mapH) / 2.0
            }

            PixelFitMode.CROP -> {
                // 源图内最大的 1:1 采样矩形，裁掉多余边并铺满
                val scale = min(srcW / GRID_SIZE, srcH / GRID_SIZE)
                mapW = GRID_SIZE * scale
                mapH = GRID_SIZE * scale
                mapX0 = srcX0 + (srcW - mapW) / 2.0
                mapY0 = srcY0 + (srcH - mapH) / 2.0
            }
        }

        // 补白按取景矩形判：缩放取景后 CONTAIN 的留白区落在图内，按整张图判会采到框外内容
        val viewX1 = srcX0 + srcW
        val viewY1 = srcY0 + srcH

        val grid = DoubleArray(GRID_SIZE * GRID_SIZE * 3)
        for (gy in 0 until GRID_SIZE) {
            for (gx in 0 until GRID_SIZE) {
                // 格心采样
                val sx = mapX0 + ((gx + 0.5) / GRID_SIZE) * mapW
                val sy = mapY0 + ((gy + 0.5) / GRID_SIZE) * mapH
                val at = index(gx, gy)
                if (sx < srcX0 || sy < srcY0 || sx >= viewX1 || sy >= viewY1) {
                    fillWhite(grid, at)
                } else {
                    sampleBilinear(src, sx, sy, grid, at)
                }
            }
        }
        return grid
    }

    private fun fillWhite(out: DoubleArray, at: Int) {
        out[at] = 255.0
        out[at + 1] = 255.0
        out[at + 2] = 255.0
    }

    private fun normalizeViewRect(view: NormalizedRect): NormalizedRect {
        val x = view.x.coerceIn(0.0, 1.0)
        val y = view.y.coerceIn(0.0, 1.0)
        return NormalizedRect(
            x = x,
            y = y,
            width = view.width.coerceIn(1e-4, 1.0 - x),
            height = view.height.coerceIn(1e-4, 1.0 - y),
        )
    }

    private fun sampleBilinear(src: PreparedImage, sx: Double, sy: Double, out: DoubleArray, at: Int) {
        // 越界兜底，补白由调用方按取景矩形判定
        if (sx < 0 || sy < 0 || sx >= src.width || sy >= src.height) {
            fillWhite(out, at)
            return
        }

        val x0 = floor(sx).toInt()
        val y0 = floor(sy).toInt()
        val x1 = min(x0 + 1, src.width - 1)
        val y1 = min(y0 + 1, src.height - 1)
        val tx = sx - x0
        val ty = sy - y0

        val c00 = DoubleArray(3).also { getRgb(src, x0, y0, it) }
        val c10 = DoubleArray(3).also { getRgb(src, x1, y0, it) }
        val c01 = DoubleArray(3).also { getRgb(src, x0, y1, it) }
        val c11 = DoubleArray(3).also { getRgb(src, x1, y1, it) }

        for (i in 0 until 3) {
            out[at + i] = lerp(lerp(c00[i], c10[i], tx), lerp(c01[i], c11[i], tx), ty)
        }
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    private fun getRgb(src: PreparedImage, x: Int, y: Int, out: DoubleArray) {
        val c = src.pixels[y * src.width + x]
        val a = ((c ushr 24) and 0xFF) / 255.0
        val r = ((c shr 16) and 0xFF).toDouble()
        val g = ((c shr 8) and 0xFF).toDouble()
        val b = (c and 0xFF).toDouble()
        // 透明与白底合成
        out[0] = r * a + 255 * (1 - a)
        out[1] = g * a + 255 * (1 - a)
        out[2] = b * a + 255 * (1 - a)
    }

    // ==================== 滤镜 ====================

    /** 在 sRGB 0~255 上近似 CSS filter，形参顺序即应用顺序：亮度 → 对比度 → 饱和度 */
    private fun applyCssLikeFilters(grid: DoubleArray, brightness: Double, contrast: Double, saturation: Double) {
        if (kotlin.math.abs(contrast - 1) < 1e-6 &&
            kotlin.math.abs(brightness - 1) < 1e-6 &&
            kotlin.math.abs(saturation - 1) < 1e-6
        ) {
            return
        }

        var i = 0
        while (i < grid.size) {
            var r = grid[i]
            var g = grid[i + 1]
            var b = grid[i + 2]

            r *= brightness
            g *= brightness
            b *= brightness

            r = ((r / 255.0 - 0.5) * contrast + 0.5) * 255.0
            g = ((g / 255.0 - 0.5) * contrast + 0.5) * 255.0
            b = ((b / 255.0 - 0.5) * contrast + 0.5) * 255.0

            val luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
            r = luma + (r - luma) * saturation
            g = luma + (g - luma) * saturation
            b = luma + (b - luma) * saturation

            grid[i] = clampByte(r)
            grid[i + 1] = clampByte(g)
            grid[i + 2] = clampByte(b)
            i += 3
        }
    }

    private fun clampByte(v: Double) = v.coerceIn(0.0, 255.0)

    // ==================== 量化 ====================

    private fun quantize(sample: DoubleArray, dither: PixelDitherMode): IntArray {
        val work = sample.copyOf()
        val result = IntArray(GRID_SIZE * GRID_SIZE)

        fun addError(x: Int, y: Int, er: Double, eg: Double, eb: Double, factor: Double) {
            if (x < 0 || y < 0 || x >= GRID_SIZE || y >= GRID_SIZE) return
            val at = index(x, y)
            work[at] = clampByte(work[at] + er * factor)
            work[at + 1] = clampByte(work[at + 1] + eg * factor)
            work[at + 2] = clampByte(work[at + 2] + eb * factor)
        }

        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                val at = index(x, y)
                val oldR = work[at]
                val oldG = work[at + 1]
                val oldB = work[at + 2]
                val idx = nearestPaletteIndex(
                    oldR.roundToInt().coerceIn(0, 255),
                    oldG.roundToInt().coerceIn(0, 255),
                    oldB.roundToInt().coerceIn(0, 255),
                )
                result[y * GRID_SIZE + x] = idx

                if (dither == PixelDitherMode.NONE) continue

                val er = oldR - PALETTE_R[idx]
                val eg = oldG - PALETTE_G[idx]
                val eb = oldB - PALETTE_B[idx]

                if (dither == PixelDitherMode.FLOYD_STEINBERG) {
                    addError(x + 1, y, er, eg, eb, 7.0 / 16.0)
                    addError(x - 1, y + 1, er, eg, eb, 3.0 / 16.0)
                    addError(x, y + 1, er, eg, eb, 5.0 / 16.0)
                    addError(x + 1, y + 1, er, eg, eb, 1.0 / 16.0)
                } else {
                    val f = 1.0 / 8.0
                    addError(x + 1, y, er, eg, eb, f)
                    addError(x + 2, y, er, eg, eb, f)
                    addError(x - 1, y + 1, er, eg, eb, f)
                    addError(x, y + 1, er, eg, eb, f)
                    addError(x + 1, y + 1, er, eg, eb, f)
                    addError(x, y + 2, er, eg, eb, f)
                }
            }
        }
        return result
    }
}
