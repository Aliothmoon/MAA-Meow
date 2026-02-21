package com.aliothmoon.maameow.data.model.activity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 资源收集活动信息
 */
@Serializable
data class ResourceCollectionInfo(
    @SerialName("Tip")
    val tip: String? = null,

    @SerialName("UtcStartTime")
    val utcStartTime: String? = null,

    @SerialName("UtcExpireTime")
    val utcExpireTime: String? = null,

    @SerialName("TimeZone")
    val timeZone: Int = 8,

    @SerialName("IsResourceCollection")
    val isResourceCollection: Boolean = false
)