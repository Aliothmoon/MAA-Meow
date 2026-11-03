package com.aliothmoon.maameow.data.model.copilot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET https://prts.maa.plus/copilot/query */
@Serializable
data class PrtsCopilotQueryResponse(
    @SerialName("status_code")
    val statusCode: Int = 0,
    @SerialName("message")
    val message: String = "",
    @SerialName("data")
    val data: PrtsCopilotPage? = null,
)

@Serializable
data class PrtsCopilotPage(
    @SerialName("has_next")
    val hasNext: Boolean = false,
    @SerialName("page")
    val page: Int = 1,
    @SerialName("total")
    val total: Int = 0,
    @SerialName("data")
    val data: List<PrtsCopilotSummary> = emptyList(),
)

/** [content] 不含 actions，执行前仍要按 id 拉完整作业 */
@Serializable
data class PrtsCopilotSummary(
    @SerialName("id")
    val id: Int = 0,
    @SerialName("uploader")
    val uploader: String = "",
    @SerialName("views")
    val views: Int = 0,
    @SerialName("like")
    val like: Int = 0,
    @SerialName("content")
    val content: String = "",
)
