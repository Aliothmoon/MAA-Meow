package com.aliothmoon.maameow.domain.enums

/**
 * @param minCoreVersion 首个认识该设施名的 core 版本，旧 core 收到不认识的设施名会拒掉整个基建任务
 */
enum class InfrastRoomType(val minCoreVersion: String? = null) {
    /** 制造站 */
    Mfg,

    /** 贸易站 */
    Trade,

    /** 控制中心 */
    Control,

    /** 发电站 */
    Power,

    /** 会客室 */
    Reception,

    /** 办公室(+速公招那个) */
    Office,

    /** 回收站 */
    Recycle("v6.20.0-beta.1"),

    /** 宿舍 */
    Dorm,

    /** 加工站(合精英材料) */
    Processing,

    /** 训练室 */
    Training,

    /** 副手换人 */
    AssistantChange("v6.18.0-beta.1");

    companion object {
        val values = InfrastRoomType.entries
    }
}
