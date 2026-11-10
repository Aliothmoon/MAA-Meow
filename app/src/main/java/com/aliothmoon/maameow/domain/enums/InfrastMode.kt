package com.aliothmoon.maameow.domain.enums

enum class InfrastMode(val value: Int) {
    /** 普通 */
    Normal(0),

    /** 自定义 */
    Custom(10000),

    /** 轮换 */
    Rotation(20000);

    companion object {
        val values = InfrastMode.entries
    }
}
