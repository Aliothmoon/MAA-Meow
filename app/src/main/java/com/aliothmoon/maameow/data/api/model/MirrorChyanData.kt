package com.aliothmoon.maameow.data.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class MirrorChyanData(
    @SerialName("version_name")
    val versionName: String,
    @SerialName("url")
    val url: String? = null,
    @SerialName("release_note")
    val releaseNote: String? = null,
    /** Unix 秒，仅带 cdk 请求时返回 */
    @SerialName("cdk_expired_time")
    val cdkExpiredTime: Long? = null
)
