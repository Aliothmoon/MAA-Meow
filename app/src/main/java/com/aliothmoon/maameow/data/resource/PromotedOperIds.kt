package com.aliothmoon.maameow.data.resource

/** 升变形态不归一，基础形态 ID 仅用于头像回退 */
object PromotedOperIds {

    private val BASE = mapOf(
        "char_1001_amiya2" to "char_002_amiya", // 阿米娅-WARRIOR
        "char_1037_amiya3" to "char_002_amiya", // 阿米娅-MEDIC
    )

    val ids: Set<String> = BASE.keys

    fun baseOf(id: String): String? = BASE[id]
}
