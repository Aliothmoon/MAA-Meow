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
        API_URLS.forEach { base ->
            val result = withContext(Dispatchers.IO) {
                fetchWithETag(base, api)
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

    /**
     * 请求结果，携带数据和是否有变化的标记
     */
    data class FetchResult(val data: String?, val changed: Boolean)

    private suspend fun fetchWithETag(base: String, api: String): String? {
        return fetchWithETagDetailed(base, api).data
    }

    /**
     * 条件请求头与正文缓存共用 api 路径作键，在 200 分支一起写入
     * 因此 304 命中的一定是这份正文，与来自哪个源无关
     * @param conditional false 时不带条件头，用于正文丢失后的当场重取
     */
    private suspend fun fetchWithETagDetailed(
        base: String,
        api: String,
        conditional: Boolean = true,
    ): FetchResult {
        val url = "$base$api"
        return try {
            val header = if (conditional) eTagCache.getConditionalHeader(api) else emptyMap()
            val response = httpClient.get(
                url,
                headers = header
            )

            handleResponse(base, api, response, conditional)
        } catch (e: IOException) {
            Timber.e(e, "$TAG: request failed: $url")
            FetchResult(null, false)
        }
    }

    private suspend fun handleResponse(
        base: String,
        api: String,
        response: Response,
        conditional: Boolean,
    ): FetchResult {
        val url = "$base$api"
        // null 表示 304 但正文缺失，需在响应关闭后重取
        val settled: FetchResult? = response.use { resp ->
            when (resp.code) {
                200 -> {
                    eTagCache.updateConditionalHeaders(api, resp.headers)
                    val body = resp.body.string()
                    internalCache.put(api, body)
                    Timber.d("$TAG: request succeeded: $url (${body.length} bytes)")
                    FetchResult(body, true)
                }

                304 -> {
                    Timber.d("$TAG: 304 Not Modified: $url")
                    val cached = internalCache.get(api)
                    if (cached == null) {
                        Timber.w("$TAG: 304 but cache missing, refetching: $url")
                        eTagCache.invalidateKey(api)
                    }
                    cached?.let { FetchResult(it, false) }
                }

                else -> {
                    Timber.w("$TAG: HTTP ${resp.code}: $url")
                    FetchResult(null, false)
                }
            }
        }
        if (settled != null) return settled
        // 不再重试，避免打转
        return if (conditional) {
            fetchWithETagDetailed(base, api, conditional = false)
        } else {
            Timber.w("$TAG: unconditional request still returned 304: $url")
            FetchResult(null, false)
        }
    }

    /**
     * 检查指定 API 是否有更新（ETag 变化检测）
     * @return true 表示有新数据（200），false 表示无变化（304）或请求失败
     */
    private suspend fun checkChanged(api: String): Boolean {
        API_URLS.forEach { base ->
            val result = withContext(Dispatchers.IO) {
                fetchWithETagDetailed(base, api)
            }
            if (result.data != null) {
                return result.changed
            }
        }
        return false
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

    /** 检查活动关卡数据是否有更新 */
    suspend fun checkStageActivityChanged(): Boolean {
        return checkChanged(MaaApi.STAGE_ACTIVITY_API)
    }

    /** 检查任务配置数据是否有更新 */
    suspend fun checkTasksChanged(): Boolean {
        return checkChanged(MaaApi.TASKS_API)
    }
}
