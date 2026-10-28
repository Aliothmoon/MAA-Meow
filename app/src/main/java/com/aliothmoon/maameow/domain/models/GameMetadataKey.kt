package com.aliothmoon.maameow.domain.models

/** 界面元数据的一档 */
data class GameMetadataKey(
    val clientType: String,
    val language: String,
    val revision: Int,
    val hotUpdateRevision: Int = 0,
)
