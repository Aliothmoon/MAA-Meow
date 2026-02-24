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

    internal class DiskCache(
        val root: File
    ) {
        private fun calc(key: String): File {
            return File(root, key)
        }

        fun get(key: String): String? {
            return try {
                val file = calc(key)
                if (file.exists()) {
                    file.readText()
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.w(e, "$TAG: 读取缓存失败")
                null
            }
        }

        fun put(key: String, value: String) {
            try {
                val file = calc(key)
                file.parentFile?.mkdirs()
                file.writeText(value)
                Timber.d("$TAG: 缓存已保存: ${file.name}")
            } catch (e: Exception) {
                Timber.w(e, "$TAG: 保存缓存失败")
            }
        }

        fun invalidate() {
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
            Timber.e(e, "$TAG: 请求异常: $url")
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
                    Timber.d("$TAG: 请求成功: $url (${body.length} bytes)")
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
            Timber.d("$TAG: 缓存已清除")
        } catch (e: Exception) {
            Timber.w(e, "$TAG: 清除缓存失败")
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
     * 获取全球服任务配置数据
     * @param clientType 客户端类型（如 YoStarEN、YoStarJP、YoStarKR、txwy）
     */
    suspend fun getGlobalTasksInfo(clientType: String): String? {
        return requestWithCache(MaaApi.getGlobalTasksApi(clientType))
    }
}
