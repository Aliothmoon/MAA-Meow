package com.aliothmoon.maameow.utils

import android.content.Context
import android.os.Build
import android.provider.Settings

object EyeProtectionDetector {

    data class DetectionResult(
        val isEnabled: Boolean,
        val source: String? = null
    )

    /** 判断设备是否开启护眼模式 */
    fun isEyeProtectionEnabled(context: Context): Boolean {
        return detect(context).isEnabled
    }

    /** 探测设备护眼模式详情 */
    fun detect(context: Context): DetectionResult {
        val resolver = context.contentResolver
        return detectInternal(
            secureGetter = { key -> runCatching { Settings.Secure.getInt(resolver, key, 0) == 1 }.getOrDefault(false) },
            systemGetter = { key -> runCatching { Settings.System.getInt(resolver, key, 0) == 1 }.getOrDefault(false) },
            globalGetter = { key -> runCatching { Settings.Global.getInt(resolver, key, 0) == 1 }.getOrDefault(false) },
            serviceChecker = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching {
                        val cdm = context.getSystemService("color_display")
                        cdm?.javaClass?.getMethod("isNightDisplayActivated")?.invoke(cdm) as? Boolean
                    }.getOrNull() == true
                } else {
                    false
                }
            }
        )
    }

    internal fun detectInternal(
        secureGetter: (String) -> Boolean,
        systemGetter: (String) -> Boolean,
        globalGetter: (String) -> Boolean,
        serviceChecker: () -> Boolean
    ): DetectionResult {
        // AOSP 标准
        if (secureGetter("night_display_activated")) {
            return DetectionResult(true, "aosp:night_display_activated")
        }

        // 小米
        if (systemGetter("screen_paper_mode_enabled")) {
            return DetectionResult(true, "xiaomi:screen_paper_mode_enabled")
        }

        // 华为与荣耀
        if (systemGetter("eyes_protection_mode")) {
            return DetectionResult(true, "huawei:eyes_protection_mode")
        }

        // 三星
        if (systemGetter("blue_light_filter") || globalGetter("blue_light_filter")) {
            return DetectionResult(true, "samsung:blue_light_filter")
        }

        // OPPO 与一加
        if (systemGetter("coloros_eyeprotect_enable") || systemGetter("eyeprotect_enable")) {
            return DetectionResult(true, "oppo:coloros_eyeprotect_enable")
        }

        // vivo 与 iQOO
        if (systemGetter("vivo_night_display") || secureGetter("vivo_night_display")) {
            return DetectionResult(true, "vivo:vivo_night_display")
        }

        // 系统服务反射兜底
        if (serviceChecker()) {
            return DetectionResult(true, "color_display:isNightDisplayActivated")
        }

        return DetectionResult(false, null)
    }
}
