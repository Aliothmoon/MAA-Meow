package com.aliothmoon.maameow.schedule.service

/**
 * 厂商私有省电设置的追加指引（文案在 res：schedule_oem_hint_*）
 *
 * AOSP 电池优化白名单之外，国产 ROM 还各有一套省电策略开关
 */
enum class OemPowerHint {
    MIUI,
    HUAWEI,
    OPPO,
    VIVO,
    SAMSUNG,
    MEIZU,
}

object OemPowerHints {

    /** null = 无需追加 */
    fun hintFor(manufacturer: String): OemPowerHint? = when (manufacturer.lowercase()) {
        "xiaomi", "redmi" -> OemPowerHint.MIUI
        "huawei", "honor" -> OemPowerHint.HUAWEI
        "oppo", "realme", "oneplus" -> OemPowerHint.OPPO
        "vivo", "iqoo" -> OemPowerHint.VIVO
        "samsung" -> OemPowerHint.SAMSUNG
        "meizu" -> OemPowerHint.MEIZU
        else -> null
    }
}
