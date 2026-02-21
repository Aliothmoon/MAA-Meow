package com.aliothmoon.maameow.data.model.activity


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 活动基本信息
 */
@Serializable
data class ActivityInfo(
    @SerialName("Tip")
    val tip: String? = null,

    @SerialName("StageName")
    val stageName: String? = null,

    @SerialName("UtcStartTime")
    val utcStartTime: String? = null,

    @SerialName("UtcExpireTime")
    val utcExpireTime: String? = null,

    @SerialName("TimeZone")
    val timeZone: Int = 8
)