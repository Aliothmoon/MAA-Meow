package com.aliothmoon.maameow.data.model.activity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * StageActivityV2.json 根结构
 */
@Serializable
data class StageActivityRoot(
    @SerialName("Official")
    val official: OfficialStageActivity? = null
)