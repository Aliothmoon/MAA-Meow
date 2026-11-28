package com.aliothmoon.maameow.data.resource

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.aliothmoon.maameow.data.config.MaaPathConfig
import timber.log.Timber
import java.io.File

/**
 * 仓库物品图标加载器（ItemListHelper.GetItemImage / ProcessBlackToTransparent）
 *
 * 只有私有缓存无可变组合状态，标记 [Stable] 允许格子跳过重组
 */
@Stable
class ItemIconLoader(
    pathConfig: MaaPathConfig
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

    /** 模板 item 图纯黑底抠成透明 */
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
