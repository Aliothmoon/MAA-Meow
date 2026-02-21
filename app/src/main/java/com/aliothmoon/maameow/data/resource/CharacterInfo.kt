package com.aliothmoon.maameow.data.resource

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class CharacterInfo(
    val id: String = "",
    val name: String,              // 简中名
    @SerialName("name_en")
    val nameEn: String? = null,    // 英文名
    @SerialName("name_jp")
    val nameJp: String? = null,    // 日文名
    @SerialName("name_kr")
    val nameKr: String? = null,    // 韩文名
    @SerialName("name_tw")
    val nameTw: String? = null,    // 繁中名
    val position: String? = null,  // MELEE/RANGED
    val profession: String? = null, // 职业
    val rarity: Int = 0,           // 稀有度
    @SerialName("name_en_unavailable")
    val nameEnUnavailable: Boolean = false,
    @SerialName("name_jp_unavailable")
    val nameJpUnavailable: Boolean = false,
    @SerialName("name_kr_unavailable")
    val nameKrUnavailable: Boolean = false,
    @SerialName("name_tw_unavailable")
    val nameTwUnavailable: Boolean = false,
    val rangeId: List<String>? = null
) {
    val isOperator: Boolean
        get() = profession in setOf("CASTER", "MEDIC", "PIONEER", "SNIPER", "SPECIAL", "SUPPORT", "TANK", "WARRIOR")

    val codeName: String
        get() {
            if (id.isEmpty()) return ""
            val parts = id.split("_")
            return if (parts.size >= 3) parts[2] else id
        }
}