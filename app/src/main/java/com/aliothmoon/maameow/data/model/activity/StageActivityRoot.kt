package com.aliothmoon.maameow.data.model.activity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * StageActivityV2.json 根结构
 * JSON 顶层为 { "Official": {...}, "YoStarEN": {...}, ... } 的动态 key 结构
 */
object StageActivityRoot {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 从 JSON 字符串解析，按 clientType 获取对应的活动数据
     * @param jsonContent 完整的 StageActivityV2.json 内容
     * @param clientType 客户端类型 (Official, YoStarEN, YoStarJP, YoStarKR, txwy)
     * @return 对应客户端的活动数据，不存在则返回 null
     */
    fun parse(jsonContent: String, clientType: String): ClientStageActivity? {
        return try {
            val root = json.parseToJsonElement(jsonContent)
            if (root !is JsonObject) return null
            val clientData = root[clientType] ?: return null
            json.decodeFromJsonElement<ClientStageActivity>(clientData)
        } catch (e: Exception) {
            null
        }
    }
}