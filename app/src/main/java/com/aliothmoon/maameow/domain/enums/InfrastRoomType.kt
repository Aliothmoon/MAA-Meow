package com.aliothmoon.maameow.domain.enums

enum class InfrastRoomType(val displayName: String) {
    /** 制造站 */
    Mfg("制造站"),

    /** 贸易站 */
    Trade("贸易站"),

    /** 控制中心 */
    Control("控制中心"),

    /** 发电站 */
    Power("发电站"),

    /** 会客室 */
    Reception("会客室"),

    /** 办公室(+速公招那个) */
    Office("办公室"),

    /** 宿舍 */
    Dorm("宿舍"),

    /** 加工站(合精英材料) */
    Processing("加工站"),

    /** 训练室 */
    Training("训练室"),;
    companion object {
        val values = InfrastRoomType.entries
    }
}

