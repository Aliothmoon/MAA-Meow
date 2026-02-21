package com.aliothmoon.maameow.data.model.activity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 活动关卡原始数据
 * 每个关卡可携带独立的 MinimumRequired 和 Activity，覆盖所属分组的值
 */
@Serializable
data class ActivityStageRaw(
    @SerialName("Display")
    val display: String,

    @SerialName("Value")
    val value: String,

    @SerialName("Drop")
    val drop: String? = null,

    /** 单关卡最低版本要求，优先于分组级别 */
    @SerialName("MinimumRequired")
    val minimumRequired: String? = null,

    /** 单关卡活动信息，优先于分组级别 */
    @SerialName("Activity")
    val activity: ActivityInfo? = null
)
