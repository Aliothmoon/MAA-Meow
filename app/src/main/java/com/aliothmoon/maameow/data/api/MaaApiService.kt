package com.aliothmoon.maameow.data.api

import android.content.Context
import com.aliothmoon.maameow.constant.MaaApi
import com.aliothmoon.maameow.constant.MaaApi.API_URLS
import com.aliothmoon.maameow.data.config.MaaPathConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Response
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

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
        LayeredCache(diskCacheDir)
    }

    internal class LayeredCache(
        val root: File
    ) {
        private val cache = ConcurrentHashMap<String, String>()

        private fun calc(key: String): File {
            return File(root, key)
        }

        fun get(key: String): String? {
            cache[key]?.let {
                Timber.d("$TAG: in-memory cache hit: $key")
                return it
            }
            return try {
                val file = calc(key)
                if (file.exists()) {
                    file.readText().also { cache[key] = it }
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.w(e, "$TAG: failed to read cache")
                null
            }
        }

        fun put(key: String, value: String) {
            cache[key] = value
            try {
                val file = calc(key)
                file.parentFile?.mkdirs()
                file.writeText(value)
                Timber.d("$TAG: cache saved: ${file.name}")
            } catch (e: Exception) {
                Timber.w(e, "$TAG: failed to save cache")
            }
        }

        fun invalidate() {
            cache.clear()
            root.deleteRecursively()
            root.mkdirs()
        }
    }

    suspend fun requestWithCache(api: String, allowFallback: Boolean = true): String? {
        API_URLS.forEach {
            val result = withContext(Dispatchers.IO) {
                fetchWithETag("$it${api}")
            }
            if (result != null) {
                return result
            }
        }
        if (allowFallback) {
            return internalCache.get(api)
        }
        Timber.w("requestWithCache error no available api")
        return null
    }

    private suspend fun fetchWithETag(url: String): String? {
        return try {
            val header = eTagCache.getConditionalHeader(url)
            val response = httpClient.get(
                url,
                headers = header
            )

            handleResponse(url, response)
        } catch (e: IOException) {
            Timber.e(e, "$TAG: request failed: $url")
            null
        }
    }

    private fun handleResponse(url: String, response: Response): String? {
        return response.use { resp ->
            val api = getRealKey(url)
            when (resp.code) {
                200 -> {
                    eTagCache.updateConditionalHeaders(url, resp.headers)
                    val body = resp.body.string()
                    internalCache.put(api, body)
                    Timber.d("$TAG: request succeeded: $url (${body.length} bytes)")
                    body
                }

                304 -> {
                    Timber.d("$TAG: 304 Not Modified: $url")
                    internalCache.get(api)
                }

                else -> {
                    Timber.w("$TAG: HTTP ${resp.code}: $url")
                    null
                }
            }
        }
    }

    private fun getRealKey(url: String): String {
        return when {
            url.contains(MaaApi.MAA_API) -> url.removePrefix(MaaApi.MAA_API)
            url.contains(MaaApi.MAA_API_BACKUP) -> url.removePrefix(MaaApi.MAA_API_BACKUP)
            else -> url.substringAfterLast("/")
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

    /**
     * 获取活动关卡数据
     */
    suspend fun getStageActivity(): String? {
        return requestWithCache(MaaApi.STAGE_ACTIVITY_API)
    }

    /**
     * 获取任务配置数据
     */
    suspend fun getTasksInfo(): String? {
        return requestWithCache(MaaApi.TASKS_API)
    }

    /**
     * 获取外服任务配置数据
     * @param clientType 客户端类型（如 YoStarEN、YoStarJP、YoStarKR、txwy）
     */
    suspend fun getGlobalTasksInfo(clientType: String): String? {
        return requestWithCache(MaaApi.getGlobalTasksApi(clientType))
    }
}
