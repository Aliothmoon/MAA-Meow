package com.aliothmoon.maameow.schedule.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class TriggerLogEntry {

    @Serializable
    @SerialName("header")
    data class Header(
        val strategyId: String,
        val strategyName: String,
        val scheduledTimeMs: Long,
        val actualTimeMs: Long,
        /** [com.aliothmoon.maameow.domain.models.RunMode.name]，旧日志可能为空 */
        val runMode: String = "",
    ) : TriggerLogEntry()

    @Serializable
    @SerialName("log")
    data class Log(
        val time: Long,
        val message: String,
    ) : TriggerLogEntry()

    @Serializable
    @SerialName("footer")
    data class Footer(
        val time: Long,
        val result: ExecutionResult,
        val message: String? = null,
    ) : TriggerLogEntry()
}
