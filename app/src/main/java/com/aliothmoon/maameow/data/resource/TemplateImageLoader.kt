package com.aliothmoon.maameow.data.resource

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

/**
 * 按 id 取 template 子目录下的图：内存 LRU + 缺失记忆，解码在 IO 线程
 *
 * {subDir}/{id}.png，热更目录优先：新干员、新材料的图先随热更到
 */
abstract class TemplateImageLoader(
    private val pathConfig: MaaPathConfig,
    private val subDir: String,
    cacheSize: Int,
) {
    private val cache = LruCache<String, ImageBitmap>(cacheSize)

    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun peek(id: String): ImageBitmap? = cache.get(id)

    suspend fun load(id: String): ImageBitmap? {
        if (id.isEmpty()) return null
        cache.get(id)?.let { return it }
        if (id in missing) return null

        return withContext(Dispatchers.IO) {
            // 双重检查：可能在切线程期间已被其他协程填充
            cache.get(id)?.let { return@withContext it }
            if (id in missing) return@withContext null

            val file = listOfNotNull(id, fallbackOf(id)).asSequence()
                .flatMap { name ->
                    sequenceOf(pathConfig.cacheResourceDir, pathConfig.resourceDir)
                        .map { File(it, "$subDir/$name.png") }
                }
                .firstOrNull { it.isFile }
            val bitmap = try {
                file?.let(::decode)
            } catch (e: Exception) {
                Timber.w(e, "加载图片失败: $subDir/$id")
                null
            }
            if (bitmap == null) {
                missing.add(id)
                return@withContext null
            }
            cache.put(id, bitmap)
            bitmap
        }
    }

    protected open fun fallbackOf(id: String): String? = null

    protected open fun decode(file: File): ImageBitmap? {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        // 提前上传纹理
        bitmap.prepareToDraw()
        return bitmap.asImageBitmap()
    }
}
