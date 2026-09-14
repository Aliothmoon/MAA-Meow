package com.aliothmoon.maameow.domain.enums

enum class InfrastRoomType {
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

    /** 宿舍 */
    Dorm,

    /** 加工站(合精英材料) */
    Processing,

    /** 训练室 */
    Training,

    /** 副手换人(core ≥ v6.18.0-beta.1，旧 core 会拒掉整个基建任务) */
    AssistantChange;

    companion object {
        val values = InfrastRoomType.entries
    }
}
