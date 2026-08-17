package com.aliothmoon.maameow.presentation.viewmodel

import android.content.ClipboardManager
import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.models.pixelart.NormalizedRect
import com.aliothmoon.maameow.domain.models.pixelart.PixelArtPlan
import com.aliothmoon.maameow.domain.models.pixelart.PixelConvertOptions
import com.aliothmoon.maameow.domain.models.pixelart.PixelDitherMode
import com.aliothmoon.maameow.domain.models.pixelart.PixelFitMode
import com.aliothmoon.maameow.domain.models.pixelart.PreparedImage
import com.aliothmoon.maameow.domain.service.pixelart.PixelPaintHelper
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.maa.callback.ToolboxResultCollector
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.max

/** 解码上限，24×24 用不着原图 */
private const val MAX_SOURCE_SIDE = 1024

/** 取景框最小边长，对齐 WpfGui 的 0.05 下限 */
private const val MIN_VIEW_SIDE = 0.05

/** 每格额外等待上限（ms），对齐 WpfGui 的 NumericUpDown 上限 */
const val GRID_DELAY_MAX_MS = 500

data class PixelArtUiState(
    val plan: PixelArtPlan? = null,
    val sourceName: String = "",
    val fit: PixelFitMode = PixelFitMode.CROP,
    val dither: PixelDitherMode = PixelDitherMode.ILLUSTRATION,
    val contrastPercent: Float = 100f,
    val brightnessPercent: Float = 100f,
    val saturationPercent: Float = 100f,
    val trimEmptyBorder: Boolean = true,
    val skipWhite: Boolean = true,
    /** 拖动绘制，下发 params.pixel_paint.swipe */
    val swipeEnabled: Boolean = true,
    /** 每格额外等待，下发 params.pixel_paint.grid_delay；点击后 sleep，拖动时长按格累加 */
    val gridDelayMs: Int = 0,
    /** 取景框，相对去边后的内容图 */
    val view: NormalizedRect = NormalizedRect(),
    val statusMessage: UiText = UiText.Empty,
)

/**
 * 像素画：只负责图片转换
 * 任务下发由 MiniGameDelegate 统一处理，点格/选色/滚色板全在 Core 的 PixelPaintTaskPlugin
 */
class PixelArtDelegate(
    private val appContext: Context,
    collector: ToolboxResultCollector,
    executionState: StateFlow<MaaExecutionState>,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(PixelArtUiState())
    val state: StateFlow<PixelArtUiState> = _state.asStateFlow()

    val progress = collector.pixelPaintProgress

    /**
     * 跑起来之后锁住参数
     * 预览一旦和已下发的 groups 不一致，进度条和日志就对不上了
     */
    val parametersLocked: StateFlow<Boolean> = executionState
        .map { it == MaaExecutionState.STARTING || it == MaaExecutionState.RUNNING || it == MaaExecutionState.STOPPING }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** 原始解码结果，去边前 */
    private var rawSource: PreparedImage? = null

    /**
     * 去边后的源图，切换参数时复用，不必重新解码
     * trim 值当键：被取消的旧协程仍会回写，键不匹配就自动作废
     */
    @Volatile
    private var preparedCache: Pair<Boolean, PreparedImage>? = null

    fun onImagePicked(uri: Uri, displayName: String) {
        if (parametersLocked.value) return
        scope.launch {
            _state.update { it.copy(statusMessage = uiTextOf(R.string.pixel_art_status_decoding)) }
            val decoded = withContext(Dispatchers.IO) { decodeScaled(uri) }
            if (decoded == null) {
                _state.update { it.copy(statusMessage = uiTextOf(R.string.pixel_art_status_decode_failed)) }
                return@launch
            }
            applySource(decoded, displayName)
        }
    }

    /**
     * 粘贴剪贴板文字，栅格化成 24×24 点阵当原图
     * 只收纯文本：图片走选图，不去猜剪贴板 URI 的 MIME
     */
    fun onPasteText() {
        if (parametersLocked.value) return
        scope.launch {
            val text = runCatching {
                appContext.getSystemService(ClipboardManager::class.java)
                    ?.primaryClip
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.text
                    ?.toString()
            }.onFailure { Timber.w(it, "read clipboard failed") }.getOrNull()?.trim().orEmpty()

            if (text.isEmpty()) {
                _state.update { it.copy(statusMessage = uiTextOf(R.string.pixel_art_paste_empty)) }
                return@launch
            }

            val rendered = withContext(Dispatchers.Default) { PixelTextRasterizer.render(text) }
            if (rendered == null) {
                _state.update { it.copy(statusMessage = uiTextOf(R.string.pixel_art_paste_failed)) }
                return@launch
            }
            applySource(rendered, text)
        }
    }

    private suspend fun applySource(image: PreparedImage, displayName: String) {
        preparedCache = null
        rawSource = image
        // 换图后旧取景没有意义
        _state.update {
            it.copy(sourceName = displayName, view = NormalizedRect(), statusMessage = UiText.Empty)
        }
        reconvert()
    }

    fun onFitChange(fit: PixelFitMode) = updateOptions { it.copy(fit = fit) }

    fun onDitherChange(dither: PixelDitherMode) = updateOptions { it.copy(dither = dither) }

    fun onContrastChange(percent: Float) = updateOptions { it.copy(contrastPercent = percent) }

    fun onBrightnessChange(percent: Float) = updateOptions { it.copy(brightnessPercent = percent) }

    fun onSaturationChange(percent: Float) = updateOptions { it.copy(saturationPercent = percent) }

    fun onSkipWhiteChange(enabled: Boolean) = updateOptions { it.copy(skipWhite = enabled) }

    /** 只影响下发参数，不用重算预览 */
    fun onSwipeChange(enabled: Boolean) {
        if (parametersLocked.value) return
        _state.update { it.copy(swipeEnabled = enabled) }
    }

    /** 同上，只影响下发参数 */
    fun onGridDelayChange(delayMs: Int) {
        if (parametersLocked.value) return
        _state.update { it.copy(gridDelayMs = delayMs.coerceIn(0, GRID_DELAY_MAX_MS)) }
    }

    fun onTrimChange(enabled: Boolean) = updateOptions { it.copy(trimEmptyBorder = enabled) }

    /**
     * 预览区手势：pan 用容器占比表达，zoom 为放大倍数（>1 放大）
     * 与 WpfGui 一致按取景中心缩放，不跟随手势中心
     */
    fun onPreviewTransform(panXFraction: Float, panYFraction: Float, zoom: Float) {
        if (parametersLocked.value || rawSource == null) return
        val current = _state.value.view
        val factor = if (zoom > 0f) 1.0 / zoom else 1.0
        val width = (current.width * factor).coerceIn(MIN_VIEW_SIDE, 1.0)
        val height = (current.height * factor).coerceIn(MIN_VIEW_SIDE, 1.0)
        val centerX = current.x + current.width / 2.0
        val centerY = current.y + current.height / 2.0
        // 手势右移则内容右移，取景左移
        val x = (centerX - width / 2.0 - panXFraction * width).coerceIn(0.0, max(0.0, 1.0 - width))
        val y = (centerY - height / 2.0 - panYFraction * height).coerceIn(0.0, max(0.0, 1.0 - height))
        val next = NormalizedRect(x, y, width, height)
        if (next == current) return
        updateOptions { it.copy(view = next) }
    }

    fun onResetView() = updateOptions { it.copy(view = NormalizedRect()) }

    /** 恢复全部转换参数与取景，图片保留 */
    fun onResetParameters() {
        if (parametersLocked.value) return
        val defaults = PixelArtUiState()
        updateOptions {
            it.copy(
                fit = defaults.fit,
                dither = defaults.dither,
                contrastPercent = defaults.contrastPercent,
                brightnessPercent = defaults.brightnessPercent,
                saturationPercent = defaults.saturationPercent,
                trimEmptyBorder = defaults.trimEmptyBorder,
                skipWhite = defaults.skipWhite,
                swipeEnabled = defaults.swipeEnabled,
                gridDelayMs = defaults.gridDelayMs,
                view = defaults.view,
            )
        }
    }

    /** 拖动取景会连发，旧的转换结果必须作废，否则可能盖掉最新预览 */
    private var reconvertJob: Job? = null

    private fun updateOptions(transform: (PixelArtUiState) -> PixelArtUiState) {
        if (parametersLocked.value) return
        _state.update(transform)
        reconvertJob?.cancel()
        reconvertJob = scope.launch { reconvert() }
    }

    private suspend fun reconvert() {
        val raw = rawSource ?: return
        val snapshot = _state.value
        val trim = snapshot.trimEmptyBorder
        val plan = withContext(Dispatchers.Default) {
            val source = preparedCache?.takeIf { it.first == trim }?.second
                ?: PixelPaintHelper
                    .prepare(raw.pixels, raw.width, raw.height, trim)
                    .also { preparedCache = trim to it }
            PixelPaintHelper.convert(source, snapshot.toOptions(), snapshot.skipWhite)
        }
        _state.update { it.copy(plan = plan) }
    }

    private fun PixelArtUiState.toOptions() = PixelConvertOptions(
        fit = fit,
        dither = dither,
        contrastPercent = contrastPercent.toDouble(),
        brightnessPercent = brightnessPercent.toDouble(),
        saturationPercent = saturationPercent.toDouble(),
        contentView = view,
        trimEmptyBorder = trimEmptyBorder,
    )

    // ==================== 解码 ====================

    /** ImageDecoder 会按 EXIF 摆正，BitmapFactory 不会，横拍的照片得靠它 */
    private fun decodeScaled(uri: Uri): PreparedImage? = runCatching {
        val source = ImageDecoder.createSource(appContext.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            var sample = 1
            while (info.size.width / sample > MAX_SOURCE_SIDE || info.size.height / sample > MAX_SOURCE_SIDE) {
                sample *= 2
            }
            decoder.setTargetSampleSize(sample)
            // getPixels 读不了硬件位图
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            PreparedImage(pixels, bitmap.width, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }.onFailure { Timber.w(it, "decode image failed") }.getOrNull()
}
