package com.aliothmoon.maameow.data.model.copilot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PRTS Plus 作业评分响应
 *
 * API: POST https://prts.maa.plus/copilot/rating
 */
@Serializable
data class PrtsRateResponse(
    @SerialName("status_code")
    val statusCode: Int = 0,
    @SerialName("message")
    val message: String = "",
    @SerialName("data")
    val data: PrtsRateData? = null
)

/**
 * 评分响应数据
 */
@Serializable
data class PrtsRateData(
    @SerialName("rating")
    val rating: String = ""  // "Like" or "Dislike"
)
