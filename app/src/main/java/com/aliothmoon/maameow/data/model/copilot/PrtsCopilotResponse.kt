package com.aliothmoon.maameow.data.model.copilot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PRTS Plus 单个作业响应
 *
 * API: GET https://prts.maa.plus/copilot/get/{id}
 */
@Serializable
data class PrtsCopilotResponse(
    @SerialName("status_code")
    val statusCode: Int = 0,
    @SerialName("message")
    val message: String = "",
    @SerialName("data")
    val data: PrtsCopilotData? = null
)

/**
 * 作业数据
 */
@Serializable
data class PrtsCopilotData(
    @SerialName("id")
    val id: Int = 0,
    @SerialName("upload_time")
    val uploadTime: String = "",
    @SerialName("uploader_id")
    val uploaderId: Int = 0,
    @SerialName("views")
    val views: Int = 0,
    @SerialName("hot_score")
    val hotScore: Double = 0.0,
    @SerialName("available")
    val available: Boolean = true,
    @SerialName("rating_level")
    val ratingLevel: Int = 0,
    @SerialName("rating_ratio")
    val ratingRatio: Double = 0.0,
    @SerialName("rating_type")
    val ratingType: Int = 0,
    @SerialName("content")
    val content: String = "",  // JSON string of CopilotTaskData
    @SerialName("like_count")
    val likeCount: Int = 0,
    @SerialName("dislike_count")
    val dislikeCount: Int = 0
)
