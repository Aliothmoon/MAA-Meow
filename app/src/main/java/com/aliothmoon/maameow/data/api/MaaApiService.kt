package com.aliothmoon.maameow.data.api

import android.content.Context
import com.aliothmoon.maameow.constant.MaaApi.API_URLS
import com.aliothmoon.maameow.data.config.MaaPathConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import timber.log.Timber
import java.io.File
import java.io.IOException

class MaaApiService(
    private val context: Context,
    private val httpClient: HttpClientHelper,
    private val eTagCache: ETagCacheManager,
    private val pathConfig: MaaPathConfig
) {
    companion object {
        private const val TAG = "MaaApiService"
    }

    private val diskCacheDir: File by lazy {
        File(pathConfig.cacheDir).also { it.mkdirs() }
    }

    private val internalCache by lazy {
        DiskCache(diskCacheDir)
    }

    /** tasks.json 有几 MB 且 App 不读内容，不常驻内存 */
    internal class DiskCache(
        val root: File
    ) {
        private fun calc(key: String): File {
            return File(root, key)
        }

        fun exists(key: String): Boolean = calc(key).isFile

        fun get(key: String): String? {
            return try {
                calc(key).takeIf { it.isFile }?.readText()
            } catch (e: Exception) {
                Timber.w(e, "$TAG: failed to read cache")
                null
            }
        }

        fun put(key: String, value: String): Boolean {
            return try {
                val file = calc(key)
                file.parentFile?.mkdirs()
                val tmp = File(file.path + ".tmp")
                tmp.writeText(value)
                check(tmp.renameTo(file)) { "rename failed: $tmp" }
                Timber.d("$TAG: cache saved: ${file.name}")
                true
            } catch (e: Exception) {
                Timber.w(e, "$TAG: failed to save cache")
                false
            }
        }

        fun invalidate() {
            root.deleteRecursively()
            root.mkdirs()
        }
    }

    sealed interface FetchResult {
        data object NotModified : FetchResult
        class Changed(val body: String, val headers: Headers) : FetchResult
    }

    /** 所有源都失败返回 null */
    suspend fun fetch(api: String): FetchResult? {
        API_URLS.forEach { base ->
            val result = withContext(Dispatchers.IO) { fetchFrom(base, api) }
            if (result != null) return result
        }
        return null
    }

    /** 正文先落盘再记 ETag，中途失败下次照常 200 重取 */
    fun commit(api: String, result: FetchResult.Changed): Boolean {
        if (!internalCache.put(api, result.body)) return false
        eTagCache.updateConditionalHeaders(api, result.headers)
        return true
    }

    fun cached(api: String): String? = internalCache.get(api)

    private suspend fun fetchFrom(base: String, api: String, conditional: Boolean = true): FetchResult? {
        val url = "$base$api"
        return try {
            val header = if (conditional) eTagCache.getConditionalHeader(api) else emptyMap()
            // 304 正文缺失要在响应关闭后重取
            httpClient.get(url, headers = header).use { resp ->
                when (resp.code) {
                    200 -> {
                        val body = resp.body.string()
                        // 换源后 ETag 对不上也会 200，正文没变不算更新
                        if (body != internalCache.get(api)) return FetchResult.Changed(body, resp.headers)
                        eTagCache.updateConditionalHeaders(api, resp.headers)
                        return FetchResult.NotModified
                    }

                    304 -> Timber.d("$TAG: 304 Not Modified: $url")
                    else -> {
                        Timber.w("$TAG: HTTP ${resp.code}: $url")
                        return null
                    }
                }
            }
            when {
                internalCache.exists(api) -> FetchResult.NotModified
                conditional -> {
                    Timber.w("$TAG: 304 but cache missing, refetching: $url")
                    eTagCache.invalidateKey(api)
                    fetchFrom(base, api, conditional = false)
                }

                else -> {
                    Timber.w("$TAG: unconditional request still returned 304: $url")
                    null
                }
            }
        } catch (e: IOException) {
            Timber.e(e, "$TAG: request failed: $url")
            null
        }
    }

    fun invalidateCache() {
        try {
            internalCache.invalidate()
            eTagCache.invalidate()
            Timber.d("$TAG: cache cleared")
        } catch (e: Exception) {
            Timber.w(e, "$TAG: failed to clear cache")
        }
    }
}
