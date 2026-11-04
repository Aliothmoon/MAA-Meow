package com.aliothmoon.maameow.web.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 与 webui/src/api/types.ts 一一对应，改字段两边一起改

@Serializable
data class WebAppInfo(
    val version: String,
    val device: String,
)

@Serializable
data class WebStatus(
    /** MaaExecutionState 名 */
    val execution: String,
    /** Disconnected / Connecting / Connected / Died / Error */
    val backend: String,
    /** FOREGROUND / BACKGROUND */
    val runMode: String,
    val launch: WebLaunch? = null,
    /** 运行时长上限的截止时间戳 */
    val runDeadline: Long? = null,
)

@Serializable
data class WebLaunch(
    val name: String,
    /** DevicePrep / Counting / Preparing / Starting */
    val phase: String,
    val remainingSeconds: Int? = null,
)

@Serializable
data class WebChain(
    val profileId: String,
    val profileName: String,
    val nodes: List<WebChainNode>,
)

@Serializable
data class WebChainNode(
    val id: String,
    val name: String,
    val enabled: Boolean,
    /** TaskTypeInfo 名，认不出为空 */
    val type: String? = null,
    /** 本轮运行状态，TaskRunStatus 名；不在本轮为空 */
    val run: String? = null,
)

@Serializable
data class WebLogItem(
    val id: Long,
    val time: Long,
    val content: String,
    val level: String,
    /** LogColorRole 名，前端按它配色 */
    val color: String,
    val tooltip: String? = null,
)

@Serializable
sealed interface WebEvent {
    @Serializable
    @SerialName("snapshot")
    data class Snapshot(
        val app: WebAppInfo,
        val status: WebStatus,
        val chain: WebChain,
        val logs: List<WebLogItem>,
    ) : WebEvent

    @Serializable
    @SerialName("status")
    data class Status(val status: WebStatus) : WebEvent

    @Serializable
    @SerialName("chain")
    data class Chain(val chain: WebChain) : WebEvent

    /** [reset] 为真时整表替换 */
    @Serializable
    @SerialName("logs")
    data class Logs(val items: List<WebLogItem>, val reset: Boolean) : WebEvent
}

@Serializable
data class PairRequest(val code: String, val name: String = "")

@Serializable
data class ErrorBody(val error: String)
