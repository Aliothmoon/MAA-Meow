package com.aliothmoon.maameow.domain.service.pixelart

import com.aliothmoon.maameow.domain.models.pixelart.NormalizedRect
import com.aliothmoon.maameow.domain.models.pixelart.PixelArtPlan
import com.aliothmoon.maameow.domain.models.pixelart.PixelColorGroup
import com.aliothmoon.maameow.domain.models.pixelart.PixelConvertOptions
import com.aliothmoon.maameow.domain.models.pixelart.PixelDitherMode
import com.aliothmoon.maameow.domain.models.pixelart.PixelFitMode
import com.aliothmoon.maameow.domain.models.pixelart.PreparedImage
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 巡展像素画：原图 → 24×24 × 官方 40 色
 *
 * 逐行对齐 MaaWpfGui/Helper/PixelPaintHelper.cs，两端出图必须一致
 * 色序与游戏右侧色板一致；最近色用 OKLab 感知距离
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

    /** 色板的 OKLab 预转换缓存，扁平存 [L,a,b, L,a,b, ...]，避免最近色比对时反复解码 */
    private val PALETTE_OKLAB = DoubleArray(COLOR_COUNT * 3).also { cache ->
        for (i in 0 until COLOR_COUNT) {
            srgb8ToOklab(
                PALETTE_R[i].toDouble(),
                PALETTE_G[i].toDouble(),
                PALETTE_B[i].toDouble(),
                cache,
                i * 3
            )
        }
    }

    // ==================== 入口 ====================

    fun convert(
        pixels: IntArray,
        width: Int,
        height: Int,
        options: PixelConvertOptions,
        skipWhite: Boolean = true,
    ): PixelArtPlan =
        convert(prepare(pixels, width, height, options.trimEmptyBorder), options, skipWhite)

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
        val matrix = if (options.dither == PixelDitherMode.ILLUSTRATION) {
            quantizeIllustration(sample)
        } else {
            quantize(sample, options.dither)
        }
        return PixelArtPlan(GRID_SIZE, matrix, buildGroups(matrix, skipWhite))
    }

    /** 解码后（可选）去边，结果可复用于多次转换 */
    fun prepare(
        pixels: IntArray,
        width: Int,
        height: Int,
        trimEmptyBorder: Boolean = true
    ): PreparedImage {
        val src = PreparedImage(pixels, width, height)
        // 恰好 24×24 视为外部已处理好的像素画，原样保留
        if (!trimEmptyBorder || (width == GRID_SIZE && height == GRID_SIZE)) return src
        return trimBorder(src) ?: src
    }

    // ==================== 色彩空间 ====================

    /** sRGB 0~255 → 线性光 0~1（IEC 61966-2-1 解伽马） */
    private fun srgb8ToLinear(v8: Double): Double {
        val x = v8 / 255.0
        return if (x >= 0.04045) ((x + 0.055) / 1.055).pow(2.4) else x / 12.92
    }

    /** 线性光 0~1 → sRGB 0~255 */
    private fun linearToSrgb8(lin: Double): Double {
        val v = if (lin <= 0.0031308) lin * 12.92 else 1.055 * lin.pow(1.0 / 2.4) - 0.055
        return v * 255.0
    }

    /** sRGB 0~255 → OKLab（Björn 2020 标准矩阵），写入 out[at..at+2] */
    private fun srgb8ToOklab(r8: Double, g8: Double, b8: Double, out: DoubleArray, at: Int) {
        val r = srgb8ToLinear(r8)
        val g = srgb8ToLinear(g8)
        val b = srgb8ToLinear(b8)

        // 线性 sRGB → LMS'
        val l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
        val m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
        val s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b

        val lc = cbrt(l)
        val mc = cbrt(m)
        val sc = cbrt(s)

        out[at] = 0.2104542553 * lc + 0.7936177850 * mc - 0.0040720468 * sc
        out[at + 1] = 1.9779984951 * lc - 2.4285922050 * mc + 0.4505937099 * sc
        out[at + 2] = 0.0259040371 * lc + 0.7827717662 * mc - 0.8086757660 * sc
    }

    /**
     * 量化前统一按 C# 的 (byte)Clamp(Round(v)) 收敛，保证两端取到同一个整数色
     * 必须用 rint 的银行家舍入：Kotlin 的 roundToInt 是逢半进一，.5 的采样点会落到别的色号
     */
    private fun roundToChannel(v: Double): Double = Math.rint(v).toInt().coerceIn(0, 255).toDouble()

    // ==================== 距离与最近色 ====================

    /** 两个 OKLab 坐标的欧氏距离平方，越小越接近 */
    private fun oklabDist2(p: DoubleArray, pi: Int, q: DoubleArray, qi: Int): Double {
        val dL = p[pi] - q[qi]
        val dA = p[pi + 1] - q[qi + 1]
        val dB = p[pi + 2] - q[qi + 2]
        return dL * dL + dA * dA + dB * dB
    }

    /** OKLab 感知色差（欧氏距离平方），热路径请走 nearestPaletteIndex 的缓存比对 */
    fun colorDistance(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Double {
        val a = DoubleArray(3)
        val b = DoubleArray(3)
        srgb8ToOklab(r1.toDouble(), g1.toDouble(), b1.toDouble(), a, 0)
        srgb8ToOklab(r2.toDouble(), g2.toDouble(), b2.toDouble(), b, 0)
        return oklabDist2(a, 0, b, 0)
    }

    fun nearestPaletteIndex(r: Int, g: Int, b: Int): Int {
        val lab = DoubleArray(3)
        srgb8ToOklab(r.toDouble(), g.toDouble(), b.toDouble(), lab, 0)
        return nearestPaletteIndex(lab, 0)
    }

    /** 给定 OKLab 坐标（lab[at..at+2]），在预转换缓存中找最近色板项 */
    private fun nearestPaletteIndex(lab: DoubleArray, at: Int): Int {
        var best = 0
        var bestD = Double.MAX_VALUE
        for (i in 0 until COLOR_COUNT) {
            val d = oklabDist2(lab, at, PALETTE_OKLAB, i * 3)
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

    /**
     * 24×24 采样结果
     *
     * [means] 每格代表色（线性光平均后回编码 sRGB），[subs] 每格 [subCount] 个子样原值
     * 两者都是 sRGB 0~255，扁平行优先；子样供插画优先模式选 medoid
     */
    private class SampleGrid(val means: DoubleArray, val subCount: Int, val subs: DoubleArray) {
        fun meanAt(x: Int, y: Int) = (y * GRID_SIZE + x) * 3
        fun subAt(x: Int, y: Int, i: Int) = ((y * GRID_SIZE + x) * subCount + i) * 3
    }

    /** 按 fit + 可选取景采样到 24×24；每格在线性光空间做面积平均，同时留下子样 */
    private fun sampleToGrid(src: PreparedImage, options: PixelConvertOptions): SampleGrid {
        // 源图恰好 24×24：逐像素直取，不插值不面积平均，只做最近色匹配
        if (src.width == GRID_SIZE && src.height == GRID_SIZE) {
            val means = DoubleArray(GRID_SIZE * GRID_SIZE * 3)
            for (y in 0 until GRID_SIZE) {
                for (x in 0 until GRID_SIZE) {
                    getRgb(src, x, y, means, (y * GRID_SIZE + x) * 3)
                }
            }
            return SampleGrid(means, 1, means.copyOf())
        }

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

        // 格内子样数按源图覆盖面积自适应，最多 4×4；格子占的源像素越多采样越密
        val cellW = mapW / GRID_SIZE
        val cellH = mapH / GRID_SIZE
        val sxN = ceil(cellW).toInt().coerceIn(1, 4)
        val syN = ceil(cellH).toInt().coerceIn(1, 4)
        val subCount = sxN * syN

        val means = DoubleArray(GRID_SIZE * GRID_SIZE * 3)
        val subs = DoubleArray(GRID_SIZE * GRID_SIZE * subCount * 3)
        val grid = SampleGrid(means, subCount, subs)

        for (gy in 0 until GRID_SIZE) {
            for (gx in 0 until GRID_SIZE) {
                val gx0 = mapX0 + (gx.toDouble() / GRID_SIZE) * mapW
                val gy0 = mapY0 + (gy.toDouble() / GRID_SIZE) * mapH

                // 线性光累加，避免 sRGB 直接平均导致交界偏暗
                var sumLR = 0.0
                var sumLG = 0.0
                var sumLB = 0.0
                var k = 0
                for (iy in 0 until syN) {
                    for (ix in 0 until sxN) {
                        val sx = gx0 + ((ix + 0.5) / sxN) * cellW
                        val sy = gy0 + ((iy + 0.5) / syN) * cellH
                        val at = grid.subAt(gx, gy, k)
                        if (sx < srcX0 || sy < srcY0 || sx >= viewX1 || sy >= viewY1) {
                            fillWhite(subs, at)
                        } else {
                            sampleBilinear(src, sx, sy, subs, at)
                        }
                        sumLR += srgb8ToLinear(subs[at])
                        sumLG += srgb8ToLinear(subs[at + 1])
                        sumLB += srgb8ToLinear(subs[at + 2])
                        k++
                    }
                }

                val mean = grid.meanAt(gx, gy)
                means[mean] = linearToSrgb8(sumLR / subCount)
                means[mean + 1] = linearToSrgb8(sumLG / subCount)
                means[mean + 2] = linearToSrgb8(sumLB / subCount)
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

    private fun sampleBilinear(
        src: PreparedImage,
        sx: Double,
        sy: Double,
        out: DoubleArray,
        at: Int
    ) {
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

        val c00 = DoubleArray(3).also { getRgb(src, x0, y0, it, 0) }
        val c10 = DoubleArray(3).also { getRgb(src, x1, y0, it, 0) }
        val c01 = DoubleArray(3).also { getRgb(src, x0, y1, it, 0) }
        val c11 = DoubleArray(3).also { getRgb(src, x1, y1, it, 0) }

        for (i in 0 until 3) {
            out[at + i] = lerp(lerp(c00[i], c10[i], tx), lerp(c01[i], c11[i], tx), ty)
        }
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    private fun getRgb(src: PreparedImage, x: Int, y: Int, out: DoubleArray, at: Int) {
        val c = src.pixels[y * src.width + x]
        val a = ((c ushr 24) and 0xFF) / 255.0
        val r = ((c shr 16) and 0xFF).toDouble()
        val g = ((c shr 8) and 0xFF).toDouble()
        val b = (c and 0xFF).toDouble()
        // 透明与白底合成
        out[at] = r * a + 255 * (1 - a)
        out[at + 1] = g * a + 255 * (1 - a)
        out[at + 2] = b * a + 255 * (1 - a)
    }

    // ==================== 滤镜 ====================

    /** 在 sRGB 0~255 上近似 CSS filter，形参顺序即应用顺序：亮度 → 对比度 → 饱和度 */
    private fun applyCssLikeFilters(
        grid: SampleGrid,
        brightness: Double,
        contrast: Double,
        saturation: Double,
    ) {
        if (kotlin.math.abs(contrast - 1) < 1e-6 &&
            kotlin.math.abs(brightness - 1) < 1e-6 &&
            kotlin.math.abs(saturation - 1) < 1e-6
        ) {
            return
        }

        filterInPlace(grid.means, brightness, contrast, saturation)
        // 子样同样要过滤，插画优先靠它选代表色
        filterInPlace(grid.subs, brightness, contrast, saturation)
    }

    private fun filterInPlace(
        buffer: DoubleArray,
        brightness: Double,
        contrast: Double,
        saturation: Double
    ) {
        var i = 0
        while (i < buffer.size) {
            var r = buffer[i] * brightness
            var g = buffer[i + 1] * brightness
            var b = buffer[i + 2] * brightness

            r = ((r / 255.0 - 0.5) * contrast + 0.5) * 255.0
            g = ((g / 255.0 - 0.5) * contrast + 0.5) * 255.0
            b = ((b / 255.0 - 0.5) * contrast + 0.5) * 255.0

            val luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
            r = luma + (r - luma) * saturation
            g = luma + (g - luma) * saturation
            b = luma + (b - luma) * saturation

            buffer[i] = clampByte(r)
            buffer[i + 1] = clampByte(g)
            buffer[i + 2] = clampByte(b)
            i += 3
        }
    }

    private fun clampByte(v: Double) = v.coerceIn(0.0, 255.0)

    // ==================== 量化 ====================

    /** 最近邻量化 + 可选误差扩散。FS 走蛇形扫描并只扩散部分误差，Atkinson 对称扩散 6/8 */
    private fun quantize(sample: SampleGrid, dither: PixelDitherMode): IntArray {
        val work = sample.means.copyOf()
        val result = IntArray(GRID_SIZE * GRID_SIZE)
        val lab = DoubleArray(3)

        fun addError(x: Int, y: Int, er: Double, eg: Double, eb: Double, factor: Double) {
            if (x < 0 || y < 0 || x >= GRID_SIZE || y >= GRID_SIZE) return
            val at = (y * GRID_SIZE + x) * 3
            work[at] = clampByte(work[at] + er * factor)
            work[at + 1] = clampByte(work[at + 1] + eg * factor)
            work[at + 2] = clampByte(work[at + 2] + eb * factor)
        }

        for (y in 0 until GRID_SIZE) {
            // 蛇形扫描：奇数行反向遍历，消除误差长期偏向一侧的条纹（仅对 FS 生效）
            val serpentine = dither == PixelDitherMode.FLOYD_STEINBERG && y % 2 == 1
            val fwd = if (serpentine) -1 else 1

            for (i in 0 until GRID_SIZE) {
                val x = if (serpentine) GRID_SIZE - 1 - i else i
                val at = (y * GRID_SIZE + x) * 3
                val oldR = work[at]
                val oldG = work[at + 1]
                val oldB = work[at + 2]
                srgb8ToOklab(
                    roundToChannel(oldR),
                    roundToChannel(oldG),
                    roundToChannel(oldB),
                    lab,
                    0
                )
                val idx = nearestPaletteIndex(lab, 0)
                result[y * GRID_SIZE + x] = idx

                if (dither == PixelDitherMode.NONE) continue

                val er = oldR - PALETTE_R[idx]
                val eg = oldG - PALETTE_G[idx]
                val eb = oldB - PALETTE_B[idx]

                if (dither == PixelDitherMode.FLOYD_STEINBERG) {
                    // 蛇形 FS：行内前向 + 下一行按行向镜像，权重不变
                    // 24×24 才 576 格，全强度扩散噪点过密观感脏，这里只扩散部分误差
                    addError(x + fwd, y, er, eg, eb, 7.0 / 16.0 * FS_STRENGTH)
                    addError(x - fwd, y + 1, er, eg, eb, 3.0 / 16.0 * FS_STRENGTH)
                    addError(x, y + 1, er, eg, eb, 5.0 / 16.0 * FS_STRENGTH)
                    addError(x + fwd, y + 1, er, eg, eb, 1.0 / 16.0 * FS_STRENGTH)
                } else {
                    // Atkinson 对称扩散，无需蛇形
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

    /** FS 误差扩散强度，<1 用来衰减高频噪点但仍压得住渐变色带 */
    private const val FS_STRENGTH = 0.6

    /** ICM 平滑项强度，对齐参考实现 */
    private const val MRF_STRENGTH = 0.0012

    /** ICM 平滑权重的指数衰减尺度（OKLab 距离平方） */
    private const val MRF_SIGMA2 = 0.0025

    /** ICM 迭代轮数 */
    private const val MRF_ITERATIONS = 3

    /**
     * 插画优先量化：medoid 代表色 + 边缘感知 MRF 平滑
     *
     * 每格在子样里选「最接近格内 OKLab 均值」的真实颜色当代表色，
     * 再用 ICM 迭代 + 指数衰减软约束让相邻且原图颜色接近的格子倾向同色
     */
    private fun quantizeIllustration(sample: SampleGrid): IntArray {
        val cells = GRID_SIZE * GRID_SIZE
        val subCount = sample.subCount

        // 1. 每格选代表色（medoid）：最接近格内 OKLab 均值的真实子样
        val repr = DoubleArray(cells * 3)
        val lab = DoubleArray(subCount * 3)
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                var sumL = 0.0
                var sumA = 0.0
                var sumB = 0.0
                for (i in 0 until subCount) {
                    val src = sample.subAt(x, y, i)
                    srgb8ToOklab(
                        roundToChannel(sample.subs[src]),
                        roundToChannel(sample.subs[src + 1]),
                        roundToChannel(sample.subs[src + 2]),
                        lab,
                        i * 3,
                    )
                    sumL += lab[i * 3]
                    sumA += lab[i * 3 + 1]
                    sumB += lab[i * 3 + 2]
                }
                val mean = doubleArrayOf(sumL / subCount, sumA / subCount, sumB / subCount)

                var best = 0
                var bestD = Double.MAX_VALUE
                for (i in 0 until subCount) {
                    val d = oklabDist2(lab, i * 3, mean, 0)
                    if (d < bestD) {
                        bestD = d
                        best = i
                    }
                }
                val at = (y * GRID_SIZE + x) * 3
                repr[at] = lab[best * 3]
                repr[at + 1] = lab[best * 3 + 1]
                repr[at + 2] = lab[best * 3 + 2]
            }
        }

        // 2. 预计算数据项：每格每色板的 OKLab 平方距离，量级 ~0.01，与下面的平滑项同尺度
        val dataCost = DoubleArray(cells * COLOR_COUNT)
        for (cell in 0 until cells) {
            for (c in 0 until COLOR_COUNT) {
                dataCost[cell * COLOR_COUNT + c] = oklabDist2(repr, cell * 3, PALETTE_OKLAB, c * 3)
            }
        }

        // 3. 初始标号：数据项最小的色板
        val labels = IntArray(cells)
        for (cell in 0 until cells) {
            var bestC = 0
            var bestE = Double.MAX_VALUE
            for (c in 0 until COLOR_COUNT) {
                val e = dataCost[cell * COLOR_COUNT + c]
                if (e < bestE) {
                    bestE = e
                    bestC = c
                }
            }
            labels[cell] = bestC
        }

        // 4. ICM 迭代：每格选使「数据项 + 平滑项」最小的标号
        //    平滑权重 w = MRF_STRENGTH * exp(-oklabDist² / MRF_SIGMA2)，邻居标号不同则加 w
        //    原图颜色相差大时 w→0，自然允许分裂，不用显式找边缘
        repeat(MRF_ITERATIONS) {
            var changed = false
            for (y in 0 until GRID_SIZE) {
                for (x in 0 until GRID_SIZE) {
                    val cell = y * GRID_SIZE + x
                    var bestLabel = labels[cell]
                    var bestEnergy = Double.MAX_VALUE

                    val hasL = x > 0
                    val hasR = x < GRID_SIZE - 1
                    val hasU = y > 0
                    val hasD = y < GRID_SIZE - 1
                    val wL = if (hasL) smoothWeight(repr, cell, cell - 1) else 0.0
                    val wR = if (hasR) smoothWeight(repr, cell, cell + 1) else 0.0
                    val wU = if (hasU) smoothWeight(repr, cell, cell - GRID_SIZE) else 0.0
                    val wD = if (hasD) smoothWeight(repr, cell, cell + GRID_SIZE) else 0.0

                    for (c in 0 until COLOR_COUNT) {
                        var energy = dataCost[cell * COLOR_COUNT + c]
                        if (hasL && labels[cell - 1] != c) energy += wL
                        if (hasR && labels[cell + 1] != c) energy += wR
                        if (hasU && labels[cell - GRID_SIZE] != c) energy += wU
                        if (hasD && labels[cell + GRID_SIZE] != c) energy += wD
                        if (energy < bestEnergy) {
                            bestEnergy = energy
                            bestLabel = c
                        }
                    }

                    if (bestLabel != labels[cell]) {
                        labels[cell] = bestLabel
                        changed = true
                    }
                }
            }
            if (!changed) return labels
        }
        return labels
    }

    /** 两格原图颜色越接近，倾向同色的惩罚越高 */
    private fun smoothWeight(repr: DoubleArray, cellA: Int, cellB: Int): Double =
        MRF_STRENGTH * exp(-oklabDist2(repr, cellA * 3, repr, cellB * 3) / MRF_SIGMA2)
}
