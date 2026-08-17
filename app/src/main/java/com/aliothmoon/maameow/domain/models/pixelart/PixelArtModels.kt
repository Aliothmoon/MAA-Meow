package com.aliothmoon.maameow.domain.models.pixelart

/** 图片适配方式，对齐 MaaWpfGui PixelPaintHelper.FitMode */
enum class PixelFitMode {
    CROP,
    CONTAIN,
    STRETCH,
}

/**
 * 抖动方式，对齐 MaaWpfGui PixelPaintHelper.DitherMode
 * 顺序即下拉框展示顺序，与 WpfGui 的 PixelPaintDitherModeList 一致
 */
enum class PixelDitherMode {
    /** 插画优先：medoid 代表色 + 边缘感知 MRF，保留干净色块、压交界噪点 */
    ILLUSTRATION,

    /** 不抖动：纯最近邻量化 */
    NONE,

    /** Floyd-Steinberg，蛇形扫描 + 部分误差扩散，适合照片渐变 */
    FLOYD_STEINBERG,

    /** Atkinson：仅扩散 6/8 误差，噪点比 FS 柔和 */
    ATKINSON,
}

/** 取景区，相对去边后内容图的归一化矩形 */
data class NormalizedRect(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val width: Double = 1.0,
    val height: Double = 1.0,
)

/** 转换参数，对齐 MaaWpfGui PixelPaintHelper.ConvertOptions */
data class PixelConvertOptions(
    val fit: PixelFitMode = PixelFitMode.CROP,
    val dither: PixelDitherMode = PixelDitherMode.ILLUSTRATION,
    /** 对比度百分比，100 为原图 */
    val contrastPercent: Double = 100.0,
    /** 亮度百分比，100 为原图 */
    val brightnessPercent: Double = 100.0,
    /** 饱和度百分比，100 为原图 */
    val saturationPercent: Double = 100.0,
    /** null 表示用完整去边结果再按 fit 适配 */
    val contentView: NormalizedRect? = null,
    /** 导入时是否裁掉透明/近白边 */
    val trimEmptyBorder: Boolean = true,
)

/** 预处理后的源图，多次调参复用，避免每次重新解码 */
class PreparedImage(val pixels: IntArray, val width: Int, val height: Int)

/** 按色分组的点列，直接对应 params.pixel_paint.groups */
class PixelColorGroup(val color: Int, val points: List<IntArray>)

/** 转换结果：24×24 色号矩阵（行优先）+ 分组点列 */
class PixelArtPlan(
    val size: Int,
    val indices: IntArray,
    val groups: List<PixelColorGroup>,
) {
    init {
        require(indices.size == size * size) { "indices size ${indices.size} != $size^2" }
    }

    val paintedCellCount: Int = groups.sumOf { it.points.size }

    fun indexAt(x: Int, y: Int): Int = indices[y * size + x]
}
