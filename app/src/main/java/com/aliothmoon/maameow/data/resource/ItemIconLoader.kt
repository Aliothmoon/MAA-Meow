package com.aliothmoon.maameow.data.resource

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.aliothmoon.maameow.data.config.MaaPathConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import androidx.core.graphics.createBitmap

/**
 * 仓库物品图标加载器
 *
 *  ItemListHelper.GetItemImage / ProcessBlackToTransparent
 *
 *  {resourceDir}/template/items/{itemId}.png
 */
class ItemIconLoader(
    private val pathConfig: MaaPathConfig
) {
    private val cache = LruCache<String, ImageBitmap>(512)

    private val missing = ConcurrentHashMap.newKeySet<String>()

    suspend fun load(itemId: String): ImageBitmap? {
        cache.get(itemId)?.let { return it }
        if (itemId in missing) return null

        return withContext(Dispatchers.IO) {
            // 双重检查：可能在切线程期间已被其他协程填充
            cache.get(itemId)?.let { return@withContext it }
            if (itemId in missing) return@withContext null

            val file = File(pathConfig.resourceDir, "template/items/$itemId.png")
            if (!file.exists()) {
                missing.add(itemId)
                return@withContext null
            }

            try {
                val processed = doProcess(file) ?: run {
                    missing.add(itemId)
                    return@withContext null
                }
                cache.put(itemId, processed)
                processed
            } catch (e: Exception) {
                Timber.w(e, "加载物品图标失败: $itemId")
                missing.add(itemId)
                null
            }
        }
    }

    private fun doProcess(file: File): ImageBitmap? {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }

        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

        return try {
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

            bitmap.asImageBitmap()
        } catch (e: Exception) {
            bitmap.recycle()
            Timber.e(e, "process item icon error")
            null
        }
    }
}
