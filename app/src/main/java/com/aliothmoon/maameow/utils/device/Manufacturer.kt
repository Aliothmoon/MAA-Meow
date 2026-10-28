package com.aliothmoon.maameow.utils.device

import android.os.Build

/** 按厂商、品牌的机型判断，两个进程共用；要分 ROM 和版本用 [RomDetector] */
object Manufacturer {

    // 后台管控激进，定时任务要引导开自启动
    private val RESTRICTIVE = setOf(
        "xiaomi", "redmi", "oppo", "realme", "oneplus",
        "vivo", "iqoo", "huawei", "honor", "samsung",
        "meizu", "smartisan", "letv", "zte", "nubia",
    )

    private val name: String = Build.MANUFACTURER.orEmpty().lowercase()

    fun isSamsung(): Boolean = name == "samsung"

    fun isHonorBrand(): Boolean = Build.BRAND.equals("honor", ignoreCase = true)

    fun isKnownRestrictive(): Boolean = name in RESTRICTIVE
}
