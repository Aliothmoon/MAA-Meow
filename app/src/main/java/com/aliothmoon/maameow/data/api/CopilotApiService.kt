package com.aliothmoon.maameow.data.api

import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotResponse
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotSetResponse
import com.aliothmoon.maameow.data.model.copilot.PrtsRateResponse
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

    /**
     * 获取作业集
     */
    suspend fun getCopilotSet(id: Int): Result<PrtsCopilotSetResponse> = withContext(Dispatchers.IO) {
        runCatching {
            httpClient.getEntity<PrtsCopilotSetResponse>("${BASE_URL}set/get/$id")
        }.onFailure {
            Timber.e(it, "$TAG: 获取作业集失败: id=$id")
        }
    }

    /**
     * 评分作业
     * @param id 作业 ID
     * @param rating "Like" 或 "Dislike"
     */
    suspend fun rateCopilot(id: Int, rating: String): Result<PrtsRateResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                put("rating", rating)
                put("id", id)
            }.toString()
            val response = httpClient.post("${BASE_URL}copilot/rating", body)
            response.use {
                JsonUtils.common.decodeFromString<PrtsRateResponse>(it.body.string())
            }
        }.onFailure {
            Timber.e(it, "$TAG: 评分失败: id=$id, rating=$rating")
        }
    }
}
