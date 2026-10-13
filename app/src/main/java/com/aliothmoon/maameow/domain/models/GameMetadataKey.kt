package com.aliothmoon.maameow.domain.models

/** 界面元数据的一档：客户端档 + 显示语言 + App 侧资源版本 */
data class GameMetadataKey(val clientType: String, val language: String, val revision: Int)
