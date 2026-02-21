package com.aliothmoon.maameow.data.model.activity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 小游戏入口（API JSON 模型）
 * Display / DisplayKey 二选一；Tip / TipKey 二选一
 */
@Serializable
data class MiniGameEntry(
    @SerialName("MinimumRequired")
    val minimumRequired: String? = null,

    @SerialName("Display")
    val display: String? = null,

    @SerialName("DisplayKey")
    val displayKey: String? = null,

    @SerialName("Value")
    val value: String? = null,

    @SerialName("Tip")
    val tip: String? = null,

    @SerialName("TipKey")
    val tipKey: String? = null,

    @SerialName("UtcStartTime")
    val utcStartTime: String? = null,

    @SerialName("UtcExpireTime")
    val utcExpireTime: String? = null,

    @SerialName("TimeZone")
    val timeZone: Int = 8
)