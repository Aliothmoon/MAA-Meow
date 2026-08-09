package com.aliothmoon.maameow.domain.pixelart

import com.aliothmoon.maameow.domain.models.pixelart.PixelConvertOptions
import com.aliothmoon.maameow.domain.models.pixelart.PixelDitherMode
import com.aliothmoon.maameow.domain.models.pixelart.PixelFitMode
import com.aliothmoon.maameow.domain.service.pixelart.PixelPaintHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对齐 MaaWpfGui/Helper/PixelPaintHelper.cs
 * 色板、距离函数、分组格式三项必须和 Core 侧完全一致，否则填出来的画是错的
 */
class PixelPaintHelperTest {

    private fun solid(width: Int, height: Int, rgb: Int) =
        IntArray(width * height) { 0xFF000000.toInt() or rgb }

    private fun options(
        fit: PixelFitMode = PixelFitMode.STRETCH,
        dither: PixelDitherMode = PixelDitherMode.NONE,
        trim: Boolean = false,
    ) = PixelConvertOptions(fit = fit, dither = dither, trimEmptyBorder = trim)

    @Test
    fun `色板规格与 Core 一致`() {
        assertEquals(24, PixelPaintHelper.GRID_SIZE)
        assertEquals(40, PixelPaintHelper.COLOR_COUNT)
        assertEquals(40, PixelPaintHelper.PALETTE.size)
        // 纯白下标，Core 侧 WhiteColorIndex = 3
        assertEquals(3, PixelPaintHelper.WHITE_COLOR_INDEX)
        assertEquals(0xFFFFFF, PixelPaintHelper.PALETTE[PixelPaintHelper.WHITE_COLOR_INDEX])
        // 首尾各抽一个对齐 C# 表
        assertEquals(0x222222, PixelPaintHelper.PALETTE[0])
        assertEquals(0x273864, PixelPaintHelper.PALETTE[39])
    }

    @Test
    fun `调色盘内的颜色必须精确命中自己`() {
        for (index in 0 until PixelPaintHelper.COLOR_COUNT) {
            val rgb = PixelPaintHelper.PALETTE[index]
            val matched = PixelPaintHelper.nearestPaletteIndex(
                (rgb shr 16) and 0xFF,
                (rgb shr 8) and 0xFF,
                rgb and 0xFF,
            )
            assertEquals("palette index $index", index, matched)
        }
    }

    @Test
    fun `CompuPhase 距离对称且同色为零`() {
        assertEquals(0.0, PixelPaintHelper.colorDistance(10, 20, 30, 10, 20, 30), 1e-9)
        assertEquals(
            PixelPaintHelper.colorDistance(10, 20, 30, 200, 100, 50),
            PixelPaintHelper.colorDistance(200, 100, 50, 10, 20, 30),
            1e-9,
        )
    }

    @Test
    fun `纯色图每格同一色号`() {
        val plan = PixelPaintHelper.convert(solid(96, 96, 0xD32F36), 96, 96, options())
        assertEquals(24 * 24, plan.indices.size)
        assertTrue(plan.indices.all { it == 4 })
        assertEquals(1, plan.groups.size)
        assertEquals(4, plan.groups[0].color)
        assertEquals(576, plan.groups[0].points.size)
    }

    @Test
    fun `跳过白色时白格不进分组`() {
        val white = PixelPaintHelper.convert(solid(48, 48, 0xFFFFFF), 48, 48, options(), skipWhite = true)
        assertTrue(white.groups.isEmpty())
        assertEquals(0, white.paintedCellCount)

        val kept = PixelPaintHelper.convert(solid(48, 48, 0xFFFFFF), 48, 48, options(), skipWhite = false)
        assertEquals(1, kept.groups.size)
        assertEquals(PixelPaintHelper.WHITE_COLOR_INDEX, kept.groups[0].color)
    }

    @Test
    fun `分组点坐标是 x y 且落在 0 到 23`() {
        val src = IntArray(48 * 48) { i ->
            val x = i % 48
            0xFF000000.toInt() or if (x < 24) 0xD32F36 else 0xFFFFFF
        }
        val plan = PixelPaintHelper.convert(src, 48, 48, options(), skipWhite = true)

        val points = plan.groups.flatMap { it.points }
        assertTrue(points.isNotEmpty())
        assertTrue(points.all { it.size == 2 })
        assertTrue(points.all { it[0] in 0..23 && it[1] in 0..23 })
        // 左半红右半白，跳过白后只剩左边 12 列
        assertTrue(points.all { it[0] < 12 })
        assertEquals(12 * 24, plan.paintedCellCount)
    }

    /**
     * Core 的 PixelPaintTaskPlugin::draw_group 按点序做单趟线段合并，不排序，
     * 只在「上一个点是同行左邻」时延长线段。点序一旦乱掉，拖动绘制会静默退化成逐格点
     */
    @Test
    fun `分组点必须行优先且行内 x 递增`() {
        val src = IntArray(96 * 96) { i ->
            val x = (i % 96) / 4
            val y = (i / 96) / 4
            0xFF000000.toInt() or if ((x + y) % 3 == 0) 0xD32F36 else 0x273864
        }
        val plan = PixelPaintHelper.convert(src, 96, 96, options(), skipWhite = false)

        for (group in plan.groups) {
            var previous: IntArray? = null
            for (point in group.points) {
                val last = previous
                if (last != null) {
                    val ordered = point[1] > last[1] || (point[1] == last[1] && point[0] > last[0])
                    assertTrue(
                        "color ${group.color}: ${last.toList()} -> ${point.toList()} 不是行优先递增",
                        ordered,
                    )
                }
                previous = point
            }
        }
    }

    /** 复刻 Core 的合并逻辑，确认整行同色确实能合成一条线段而不是 24 段 */
    @Test
    fun `整行同色会被 Core 合成一条线段`() {
        val src = IntArray(96 * 96) { i ->
            val y = (i / 96) / 4
            0xFF000000.toInt() or if (y < 12) 0xD32F36 else 0x273864
        }
        val plan = PixelPaintHelper.convert(src, 96, 96, options(), skipWhite = false)
        val group = plan.groups.first { it.color == 4 }

        // 与 PixelPaintTaskPlugin.cpp 的合并条件一致
        var segments = 0
        var end: IntArray? = null
        for (point in group.points) {
            val last = end
            if (last != null && last[1] == point[1] && last[0] + 1 == point[0]) {
                end = point
            } else {
                segments++
                end = point
            }
        }
        // 上半 12 行整行同色 → 恰好 12 条线段，每条 24 格
        assertEquals(12, segments)
        assertEquals(12 * 24, group.points.size)
    }

    @Test
    fun `分组按色号升序且不含空组`() {
        val src = IntArray(48 * 48) { i ->
            val x = i % 48
            0xFF000000.toInt() or when {
                x < 16 -> 0x273864
                x < 32 -> 0xD32F36
                else -> 0x222222
            }
        }
        val plan = PixelPaintHelper.convert(src, 48, 48, options(), skipWhite = true)
        val colors = plan.groups.map { it.color }
        assertEquals(colors.sorted(), colors)
        assertTrue(plan.groups.all { it.points.isNotEmpty() })
    }

    @Test
    fun `contain 模式在长条图上下补白`() {
        val plan = PixelPaintHelper.convert(
            solid(48, 24, 0xD32F36), 48, 24, options(fit = PixelFitMode.CONTAIN), skipWhite = false,
        )
        assertEquals(PixelPaintHelper.WHITE_COLOR_INDEX, plan.indexAt(12, 0))
        assertEquals(PixelPaintHelper.WHITE_COLOR_INDEX, plan.indexAt(12, 23))
        assertEquals(4, plan.indexAt(12, 12))
    }

    @Test
    fun `crop 模式取中心正方形`() {
        val src = IntArray(96 * 48) { i ->
            val x = i % 96
            0xFF000000.toInt() or if (x < 48) 0xD32F36 else 0x222222
        }
        val plan = PixelPaintHelper.convert(src, 96, 48, options(fit = PixelFitMode.CROP), skipWhite = false)
        assertEquals(4, plan.indexAt(0, 12))
        assertEquals(0, plan.indexAt(23, 12))
    }

    @Test
    fun `去边裁掉近白边框`() {
        // 48×48 白底，中间 24×24 红块，去边后应铺满整格
        val src = IntArray(48 * 48) { 0xFFFFFFFF.toInt() }
        for (y in 12 until 36) {
            for (x in 12 until 36) {
                src[y * 48 + x] = 0xFFD32F36.toInt()
            }
        }
        val trimmed = PixelPaintHelper.convert(src, 48, 48, options(trim = true), skipWhite = false)
        assertTrue(trimmed.indices.all { it == 4 })

        val untrimmed = PixelPaintHelper.convert(src, 48, 48, options(trim = false), skipWhite = false)
        assertEquals(PixelPaintHelper.WHITE_COLOR_INDEX, untrimmed.indexAt(0, 0))
    }

    @Test
    fun `抖动会把色板外的灰拆成多色`() {
        val flat = PixelPaintHelper.convert(solid(96, 96, 0x808080), 96, 96, options())
        val dithered = PixelPaintHelper.convert(
            solid(96, 96, 0x808080), 96, 96, options(dither = PixelDitherMode.FLOYD_STEINBERG),
        )
        assertEquals(1, flat.groups.size)
        assertTrue("dither should mix colors", dithered.groups.size > 1)
    }

    @Test
    fun `透明像素合成到白底`() {
        val src = IntArray(48 * 48) { 0x00000000 }
        val plan = PixelPaintHelper.convert(src, 48, 48, options(trim = false), skipWhite = false)
        assertTrue(plan.indices.all { it == PixelPaintHelper.WHITE_COLOR_INDEX })
    }
}
