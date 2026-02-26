package com.aliothmoon.maameow.data.datasource

import android.content.Context
import com.aliothmoon.maameow.data.model.AssetManifest
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicInteger


class AssetExtractor(private val context: Context) {

    companion object {
        private const val MANIFEST_FILE_NAME = "MaaSync/asset_manifest.json"
        val PERMIT = Runtime.getRuntime().availableProcessors()
    }

    class ExtractFailedException(
        failedFile: String,
        attempts: Int,
        cause: Throwable
    ) : Exception("文件 $failedFile 在 $attempts 次尝试后仍失败", cause)

    private val json = JsonUtils.common


    data class ExtractProgress(
        val extractedCount: Int,
        val totalCount: Int,
        val currentFile: String
    )

    private object BufferPool {
        private const val BUFFER_SIZE = 128 * 1024
        private val POOL_SIZE = PERMIT
        private val pool = ArrayBlockingQueue<ByteArray>(POOL_SIZE)

        init {
            repeat(POOL_SIZE) {
                pool.offer(ByteArray(BUFFER_SIZE))
            }
        }

        fun acquire(): ByteArray = pool.poll() ?: ByteArray(BUFFER_SIZE)

        fun release(buffer: ByteArray) {
            if (buffer.size == BUFFER_SIZE) {
                pool.offer(buffer)
            }
        }

        inline fun <T> use(block: (ByteArray) -> T): T {
            val buffer = acquire()
            return try {
                block(buffer)
            } finally {
                release(buffer)
            }
        }
    }


    suspend fun extract(
        assetDir: String,
        destDir: File,
        onProgress: (ExtractProgress) -> Unit
    ): Result<Int> {
        return try {
            val startTime = System.currentTimeMillis()
            val extractedCount = AtomicInteger(0)
            val semaphore = Semaphore(PERMIT)

            val manifest = loadAssetManifest()
                ?: throw IllegalStateException("Assets 清单文件不存在，请重新构建项目")
            val allFiles = manifest.files.filter { it.startsWith("$assetDir/") }
            val totalFiles = allFiles.size

            Timber.d("待复制文件数: $totalFiles")
            onProgress(ExtractProgress(0, totalFiles, ""))

            withContext(Dispatchers.IO) {
                coroutineScope {
                    for (assetPath in allFiles) {
                        val relativePath = assetPath.removePrefix("$assetDir/")

                        launch {
                            semaphore.withPermit {
                                try {
                                    retryWithBackoff(maxRetries = 3, initialDelayMs = 120) {
                                        val targetFile = File(destDir, relativePath)
                                        targetFile.parentFile?.mkdirs()
                                        BufferPool.use { buffer ->
                                            context.assets.open(assetPath).use { input ->
                                                FileOutputStream(targetFile).use { output ->
                                                    input.copyToWithBuffer(output, buffer)
                                                }
                                            }
                                        }
                                    }
                                    val count = extractedCount.incrementAndGet()
                                    onProgress(ExtractProgress(count, totalFiles, relativePath))
                                } catch (e: Exception) {
                                    Timber.e(e, "文件复制最终失败: $assetPath")
                                    throw ExtractFailedException(assetPath, 3, e)
                                }
                            }
                        }
                    }
                }
            }

            val totalTime = System.currentTimeMillis() - startTime
            Timber.i("复制完成: ${extractedCount.get()} 个文件, 耗时: ${totalTime}ms")

            Result.success(extractedCount.get())
        } catch (e: Exception) {
            Timber.e(e, "复制失败")
            Result.failure(e)
        }
    }


    private fun loadAssetManifest(): AssetManifest? {
        return try {
            context.assets.open(MANIFEST_FILE_NAME).use { input ->
                val jsonText = input.bufferedReader().use { it.readText() }
                json.decodeFromString<AssetManifest>(jsonText)
            }
        } catch (e: Exception) {
            Timber.e(e, "读取 Assets 清单失败")
            null
        }
    }

    private suspend fun <T> retryWithBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 100,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    val delayMs = initialDelayMs * (1 shl attempt) // 100, 200, 400
                    Timber.w("重试 ${attempt + 1}/$maxRetries，等待 ${delayMs}ms: ${e.message}")
                    delay(delayMs)
                }
            }
        }
        throw lastException ?: IllegalStateException("Retry failed without exception")
    }

    private fun InputStream.copyToWithBuffer(out: OutputStream, buffer: ByteArray): Long {
        var bytesCopied: Long = 0
        var bytes = read(buffer)
        while (bytes >= 0) {
            out.write(buffer, 0, bytes)
            bytesCopied += bytes
            bytes = read(buffer)
        }
        return bytesCopied
    }
}
