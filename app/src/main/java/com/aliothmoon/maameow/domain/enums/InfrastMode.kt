package com.aliothmoon.maameow.domain.enums

enum class InfrastMode(val value: Int, val displayName: String) {
    /** 普通 */
    Normal(0, "常规模式"),

    /** 自定义 */
    Custom(10000, "自定义基建配置"),

    /** 轮换 */
    Rotation(20000, "队列轮换");

    companion object {
        val values = InfrastMode.entries
    }
}
