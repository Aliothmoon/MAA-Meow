package com.aliothmoon.maameow.data.model.activity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Official 服务器活动数据
 */
@Serializable
data class OfficialStageActivity(
    @SerialName("sideStoryStage")
    val sideStoryStage: Map<String, SideStoryStageEntry>? = null,

    @SerialName("resourceCollection")
    val resourceCollection: ResourceCollectionInfo? = null,

    @SerialName("miniGame")
    val miniGame: List<MiniGameEntry>? = null
)