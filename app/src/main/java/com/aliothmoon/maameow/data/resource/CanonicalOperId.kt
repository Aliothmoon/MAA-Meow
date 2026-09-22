package com.aliothmoon.maameow.data.resource

/**
 * 升变形态干员 ID 归一
 * 对齐 WPF DataHelper.GetCanonicalOperId：干员识别把升变形态当独立 ID 返回，
 * 不折回基础形态会让基础形态落进「未拥有」
 *
 * 同时是升变形态 ID 的唯一真相源，[ResourceDataManager] 的虚拟干员集合从这里取
 */
object CanonicalOperId {

    /** 升变形态 ID -> 基础形态 ID */
    private val PROMOTED = mapOf(
        "char_1001_amiya2" to "char_002_amiya",  // 阿米娅-WARRIOR
        "char_1037_amiya3" to "char_002_amiya",  // 阿米娅-MEDIC
    )

    /** 升变形态 ID，不参与花名册 */
    val promotedIds: Set<String> = PROMOTED.keys

    fun of(id: String): String = PROMOTED[id] ?: id
}
