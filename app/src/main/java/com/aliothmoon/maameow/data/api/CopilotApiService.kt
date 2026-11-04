package com.aliothmoon.maameow.data.api

import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotQueryResponse
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotResponse
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotSetQueryResponse
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotSetResponse
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

class CopilotApiService(
    private val httpClient: HttpClientHelper
) {
    companion object {
        private const val TAG = "CopilotApiService"
        private const val BASE_URL = "https://prts.maa.plus/"
    }

    /**
     * 获取单个作业
     */
    suspend fun getCopilot(id: Int): Result<PrtsCopilotResponse> = withContext(Dispatchers.IO) {
        runCatching {
            httpClient.getEntity<PrtsCopilotResponse>("${BASE_URL}copilot/get/$id")
        }.onFailure {
            Timber.e(it, "$TAG: 获取作业失败: id=$id")
        }
    }

    suspend fun queryCopilots(query: Map<String, String>): Result<PrtsCopilotQueryResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                httpClient.getEntity<PrtsCopilotQueryResponse>("${BASE_URL}copilot/query", query = query)
            }.onFailure {
                Timber.e(it, "$TAG: 搜索作业失败: $query")
            }
        }

    suspend fun querySets(page: Int, limit: Int, keyword: String): Result<PrtsCopilotSetQueryResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = buildJsonObject {
                    put("page", page)
                    put("limit", limit)
                    if (keyword.isNotEmpty()) put("keyword", keyword)
                }.toString()
                httpClient.post("${BASE_URL}set/query", body).use {
                    JsonUtils.common.decodeFromString<PrtsCopilotSetQueryResponse>(it.body.string())
                }
            }.onFailure {
                Timber.e(it, "$TAG: 搜索作业集失败: keyword=$keyword, page=$page")
            }
        }

    /**
     * 获取作业集
     */
    suspend fun getCopilotSet(id: Int): Result<PrtsCopilotSetResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                httpClient.getEntity<PrtsCopilotSetResponse>(
                    "${BASE_URL}set/get", query = mapOf(
                        "id" to id.toString()
                    )
                )
            }.onFailure {
                Timber.e(it, "$TAG: 获取作业集失败: id=$id")
            }
        }

    /**
     * 评分作业
     * @param id 作业 ID
     * @param rating "Like" 或 "Dislike"
     */
    suspend fun rateCopilot(id: Int, rating: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                put("rating", rating)
                put("id", id)
            }.toString()
            httpClient.post("${BASE_URL}copilot/rating", body).use { }
        }.onFailure {
            Timber.e(it, "$TAG: 评分失败: id=$id, rating=$rating")
        }
    }
}

