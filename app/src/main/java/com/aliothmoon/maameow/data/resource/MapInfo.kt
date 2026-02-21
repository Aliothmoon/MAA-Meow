package com.aliothmoon.maameow.data.resource

import kotlinx.serialization.Serializable

/**
 * 地图信息
 * see Arknights-Tile-Pos/overview.json
 */
@Serializable
data class MapInfo(
    val code: String? = null,
    val filename: String? = null,
    val levelId: String? = null,
    val name: String? = null,
    val stageId: String? = null,
    val height: Int = 0,
    val width: Int = 0
)
