package com.aliothmoon.maameow.data.model.copilot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PRTS Plus 作业集响应
 *
 * API: GET https://prts.maa.plus/set/get/{id}
 */
@Serializable
data class PrtsCopilotSetResponse(
    @SerialName("status_code")
    val statusCode: Int = 0,
    @SerialName("message")
    val message: String = "",
    @SerialName("data")
    val data: PrtsCopilotSetData? = null
)

/**
 * 作业集数据
 */
@Serializable
data class PrtsCopilotSetData(
    @SerialName("id")
    val id: Int = 0,
    @SerialName("name")
    val name: String = "",
    @SerialName("description")
    val description: String = "",
    @SerialName("creator_id")
    val creatorId: Int = 0,
    @SerialName("create_time")
    val createTime: String = "",
    @SerialName("copilot_ids")
    val copilotIds: List<Int> = emptyList(),
    @SerialName("views")
    val views: Int = 0
)
