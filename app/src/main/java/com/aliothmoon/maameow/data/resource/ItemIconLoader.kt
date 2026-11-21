package com.aliothmoon.maameow.data.resource

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.aliothmoon.maameow.data.config.MaaPathConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * 仓库物品图标加载器
 *
 *  ItemListHelper.GetItemImage / ProcessBlackToTransparent
 *
 *  {resourceDir}/template/items/{itemId}.png
 *
 * 取图（含内存 LRU 与缺失负缓存）走父类 [TemplateImageLoader.load]，解码在 IO 线程。
 *
 * [Stable]：只有私有缓存，没有会参与组合的可变状态，
 * 标上之后调用它的格子才可能被跳过重组。
 */
@Stable
class ItemIconLoader(
    private val pathConfig: MaaPathConfig
) : TemplateImageLoader(pathConfig, "template/items", cacheSize = 512) {

    override fun decode(file: File): ImageBitmap? {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }

        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

        return try {
            processBlackToTransparentInPlace(bitmap)
            // 提前上传纹理
            bitmap.prepareToDraw()

            bitmap.asImageBitmap()
        } catch (e: Exception) {
            bitmap.recycle()
            Timber.e(e, "process item icon error")
            null
        }
    }

    /**
     * 导出用：读取物品图标并把纯黑抠成透明。
     * 模板 item 图底是黑的，导出时要透出画布白底；再在绘制处铺不透明白底，避免发透发灰。
     * UI 仍走 [TemplateImageLoader.load]。
     */
    suspend fun loadRawAndroidBitmap(itemId: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = resolveItemFile(itemId) ?: return@withContext null
        runCatching {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return@runCatching null
            processBlackToTransparentInPlace(bitmap)
            bitmap
        }.getOrNull()
    }

    /**
     * 与父类取图同一套查找顺序：热更目录优先，再回落到资源目录，
     * 免得界面显示的是热更后的图、导出的却是旧图。
     */
    private fun resolveItemFile(itemId: String): File? =
        sequenceOf(pathConfig.cacheResourceDir, pathConfig.resourceDir)
            .map { File(it, "template/items/$itemId.png") }
            .firstOrNull { it.isFile }

    /**
     * 模板 item 图底是黑的，抠成透明；UI 与导出共用同一段像素处理。
     * see ItemListHelper.ProcessBlackToTransparent
     */
    private fun processBlackToTransparentInPlace(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            if (pixels[i] and 0x00FFFFFF == 0) {
                pixels[i] = 0
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }
}
