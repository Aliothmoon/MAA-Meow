package com.aliothmoon.maameow.data.resource

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.utils.Misc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * 自定义主界面背景图的单一数据源。
 *
 * 职责：
 * - [prepareSource] / [decodeSource]：把用户选中的图片复制到缓存并按 EXIF 方向解码，供裁剪页使用；
 * - [saveCropped]：把裁剪结果写入 filesDir/backgrounds/bg.jpg，并更新令牌触发重载；
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

    // 串行化背景文件的写入/删除：NonCancellable 的保存可能在转屏后仍在后台执行，
    // 重建后的页面再次保存会与之共写同一临时文件，必须互斥。
    private val writeMutex = Mutex()

    private val backgroundsDir: File
        get() = File(context.filesDir, DIR_NAME)

    private val backgroundFile: File
        get() = File(backgroundsDir, BG_FILE_NAME)

    /** 当前生效的背景位图；未启用或无文件时为 null。scope 即 IO 调度器，解码在 IO 线程执行。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val imageBitmap: StateFlow<ImageBitmap?> =
        combine(
            appSettingsManager.customBackgroundEnabled,
            appSettingsManager.customBackgroundToken,
        ) { enabled, token -> enabled to token }
            .mapLatest { (enabled, _) -> if (enabled) loadBitmap() else null }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * 把选中的图片复制到缓存目录，返回文件路径。
     * 裁剪页从该文件解码，进程被杀重建后仍可恢复（此时图片选择器的 Uri 授权通常已失效）。
     */
    suspend fun prepareSource(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val sourceFile = File(context.cacheDir, SOURCE_TMP_NAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                sourceFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            sourceFile.absolutePath
        }.onFailure { Timber.e(it, "prepareSource failed") }.getOrNull()
    }

    /**
     * 解码裁剪源图片：按 EXIF 方向旋转/镜像，并降采样限制内存占用。
     * EXIF 解析失败时按正常方向继续解码。
     */
    suspend fun decodeSource(path: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val orientation = runCatching {
                ExifInterface(path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            var sample = 1
            while (bounds.outWidth / sample > MAX_SOURCE_SIDE || bounds.outHeight / sample > MAX_SOURCE_SIDE) {
                sample *= 2
            }
            val decoded = BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return@runCatching null

            if (orientation == ExifInterface.ORIENTATION_NORMAL ||
                orientation == ExifInterface.ORIENTATION_UNDEFINED
            ) {
                return@runCatching decoded
            }
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        postRotate(90f)
                        postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        postRotate(-90f)
                        postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                }
            }
            try {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            } finally {
                decoded.recycle()
            }
        }.onFailure { Timber.e(it, "decodeSource failed") }.getOrNull()
    }

    /** 删除裁剪源缓存文件（取消裁剪或保存完成后调用）。 */
    fun clearSourceCache() {
        scope.launch { runCatching { File(context.cacheDir, SOURCE_TMP_NAME).delete() } }
    }

    /**
     * 把裁剪结果写入背景文件：成功后更新令牌并启用背景。
     */
    suspend fun saveCropped(bitmap: Bitmap): Boolean = withContext(NonCancellable + Dispatchers.IO) {
        writeMutex.withLock {
            runCatching {
                backgroundsDir.mkdirs()
                // 先写临时文件再同目录原子重命名：压缩失败或进程被杀不会破坏已有背景。
                val temporaryFile = File(backgroundsDir, "$BG_FILE_NAME.tmp")
                try {
                    temporaryFile.outputStream().use { out ->
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out))
                    }
                    check(temporaryFile.renameTo(backgroundFile)) { "重命名背景文件失败" }
                } finally {
                    temporaryFile.delete()
                }
                appSettingsManager.setCustomBackgroundState(
                    enabled = true,
                    token = System.currentTimeMillis().toString(),
                )
                true
            }.onFailure { Timber.e(it, "saveCropped failed") }.getOrDefault(false)
        }
    }

    /** 关闭并清除自定义背景。 */
    suspend fun clear() = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            appSettingsManager.setCustomBackgroundState(enabled = false, token = "")
            runCatching { backgroundFile.delete() }
            // 顺带清理进程被杀可能残留的临时文件
            runCatching { File(backgroundsDir, "$BG_FILE_NAME.tmp").delete() }
        }
    }

    private fun loadBitmap(): ImageBitmap? {
        val (screenWidth, screenHeight) = Misc.getScreenSize(context)
        return decodeScaled(backgroundFile, screenWidth, screenHeight)?.asImageBitmap()
    }

    private fun decodeScaled(file: File, requestedWidth: Int, requestedHeight: Int): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                requestedWidth = requestedWidth,
                requestedHeight = requestedHeight,
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        requestedWidth: Int,
        requestedHeight: Int,
    ): Int {
        if (requestedWidth <= 0 || requestedHeight <= 0) return 1
        var sample = 1
        val halfWidth = width / 2
        val halfHeight = height / 2
        while (halfWidth / sample >= requestedWidth && halfHeight / sample >= requestedHeight) {
            sample *= 2
        }
        return sample
    }

    companion object {
        private const val DIR_NAME = "backgrounds"
        private const val BG_FILE_NAME = "bg.jpg"
        private const val SOURCE_TMP_NAME = "bg_source_tmp"

        /** 裁剪源图片解码的最长边限制 */
        private const val MAX_SOURCE_SIDE = 2400
    }
}
