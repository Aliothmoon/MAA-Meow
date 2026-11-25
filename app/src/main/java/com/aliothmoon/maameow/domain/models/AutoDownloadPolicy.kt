package com.aliothmoon.maameow.domain.models

import androidx.annotation.StringRes
import com.aliothmoon.maameow.R

/** 启动检测到更新后的自动下载策略 */
enum class AutoDownloadPolicy(@param:StringRes val labelRes: Int) {
    OFF(R.string.settings_auto_download_off),
    WIFI_ONLY(R.string.settings_auto_download_wifi_only),
    ALWAYS(R.string.settings_auto_download_always);

    fun allowsAutoDownload(isUnmetered: Boolean): Boolean = when (this) {
        OFF -> false
        WIFI_ONLY -> isUnmetered
        ALWAYS -> true
    }

    companion object {
        /** 兼容旧版布尔值（true/false）与枚举名 */
        fun fromStored(raw: String): AutoDownloadPolicy = when (raw.trim().uppercase()) {
            "WIFI_ONLY" -> WIFI_ONLY
            "ALWAYS", "TRUE" -> ALWAYS
            "OFF", "FALSE", "" -> OFF
            else -> OFF
        }
    }
}
