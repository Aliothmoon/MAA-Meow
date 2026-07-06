package com.aliothmoon.maameow.data.resource

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * 自定义主界面背景图的单一数据源。
 *
 * 职责：
 * - [importFromUri]：把用户选中的图片降采样后复制到 filesDir/backgrounds/bg.jpg，并更新令牌触发重载；
 * - [clear]：删除文件并关闭背景；
 * - [imageBitmap]：监听「启用状态 + 令牌」，在 IO 线程解码并缓存为 [ImageBitmap]，供主界面绘制。
 *
 * 只负责数据与解码，不含任何 UI；玻璃主题与遮罩绘制在 presentation/theme 层完成。
 */
class BackgroundImageStore(
    private val context: Context,
    private val appSettingsManager: AppSettingsManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val backgroundsDir: File
        get() = File(context.filesDir, DIR_NAME)

    private val backgroundFile: File
        get() = File(backgroundsDir, BG_FILE_NAME)

    /** 当前生效的背景位图；未启用或无文件时为 null。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val imageBitmap: StateFlow<ImageBitmap?> =
        combine(
            appSettingsManager.customBackgroundEnabled,
            appSettingsManager.customBackgroundToken,
        ) { enabled, token -> enabled to token }
            .distinctUntilChanged()
            .mapLatest { (enabled, _) -> if (enabled) loadBitmap() else null }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, null)

    /** 背景图不透明度 0f~1f。 */
    val imageAlpha: StateFlow<Float> = appSettingsManager.customBackgroundImageAlpha
        .map { it / 100f }
        .stateIn(scope, SharingStarted.Eagerly, appSettingsManager.customBackgroundImageAlpha.value / 100f)

    /** 遮罩强度 0f~1f。 */
    val scrimAlpha: StateFlow<Float> = appSettingsManager.customBackgroundScrim
        .map { it / 100f }
        .stateIn(scope, SharingStarted.Eagerly, appSettingsManager.customBackgroundScrim.value / 100f)

    /** 模糊强度 0f~1f（由 UI 层换算为 dp，仅 API 31+ 生效）。 */
    val blurFraction: StateFlow<Float> = appSettingsManager.customBackgroundBlur
        .map { it / 100f }
        .stateIn(scope, SharingStarted.Eagerly, appSettingsManager.customBackgroundBlur.value / 100f)

    /**
     * 从相册选中的 [uri] 导入背景图：降采样到屏幕分辨率后另存为 JPEG，成功后更新令牌并启用。
     */
    suspend fun importFromUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = backgroundsDir.apply { mkdirs() }
            val tmp = File(dir, "bg_tmp")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("无法读取所选图片")

            val dm = context.resources.displayMetrics
            val scaled = decodeScaled(tmp, dm.widthPixels, dm.heightPixels)
                ?: run { tmp.delete(); error("图片解码失败") }

            backgroundFile.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            scaled.recycle()
            tmp.delete()

            appSettingsManager.setCustomBackgroundToken(System.currentTimeMillis().toString())
            appSettingsManager.setCustomBackgroundEnabled(true)
        }.onFailure { Timber.e(it, "importFromUri failed") }
    }

    /** 关闭并清除自定义背景。 */
    suspend fun clear() = withContext(Dispatchers.IO) {
        appSettingsManager.setCustomBackgroundEnabled(false)
        runCatching { backgroundFile.delete() }
        appSettingsManager.setCustomBackgroundToken("")
    }

    private fun loadBitmap(): ImageBitmap? {
        val dm = context.resources.displayMetrics
        val bmp = decodeScaled(backgroundFile, dm.widthPixels, dm.heightPixels) ?: return null
        return bmp.asImageBitmap()
    }

    private fun decodeScaled(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = calcInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun calcInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        if (reqWidth <= 0 || reqHeight <= 0) return 1
        var sample = 1
        var halfW = width / 2
        var halfH = height / 2
        while (halfW / sample >= reqWidth && halfH / sample >= reqHeight) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    companion object {
        private const val DIR_NAME = "backgrounds"
        private const val BG_FILE_NAME = "bg.jpg"
    }
}
