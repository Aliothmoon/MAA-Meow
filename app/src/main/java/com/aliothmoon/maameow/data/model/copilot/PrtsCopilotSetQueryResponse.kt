package com.aliothmoon.maameow.data.model.copilot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** POST https://prts.maa.plus/set/query，后端固定按 id 倒序 */
@Serializable
data class PrtsCopilotSetQueryResponse(
    @SerialName("status_code")
    val statusCode: Int = 0,
    @SerialName("message")
    val message: String = "",
    @SerialName("data")
    val data: PrtsCopilotSetPage? = null,
)

@Serializable
data class PrtsCopilotSetPage(
    @SerialName("has_next")
    val hasNext: Boolean = false,
    @SerialName("data")
    val data: List<PrtsCopilotSetSummary> = emptyList(),
)

@Serializable
data class PrtsCopilotSetSummary(
    @SerialName("id")
    val id: Int = 0,
    @SerialName("name")
    val name: String = "",
    @SerialName("description")
    val description: String = "",
    @SerialName("creator")
    val creator: String = "",
    @SerialName("copilot_ids")
    val copilotIds: List<Int> = emptyList(),
)
