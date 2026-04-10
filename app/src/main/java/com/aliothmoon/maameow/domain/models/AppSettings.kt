package com.aliothmoon.maameow.domain.models

import com.aliothmoon.preferences.PrefKey
import com.aliothmoon.preferences.PrefSchema
import kotlinx.serialization.Serializable

@Serializable
@PrefSchema
data class AppSettings(
    @PrefKey(default = "ACCESSIBILITY") val overlayMode: String = "ACCESSIBILITY",

    @PrefKey(default = "BACKGROUND") val runMode: String = "BACKGROUND",

    @PrefKey(default = "GITHUB") val updateSource: String = "GITHUB",

    @PrefKey(default = "") val mirrorChyanCdk: String = "",

    @PrefKey(default = "false") val debugMode: String = "false",

    @PrefKey(default = "true") val autoCheckUpdate: String = "true",

    @PrefKey(default = "false") val autoDownloadUpdate: String = "false",

    @PrefKey(default = "SHIZUKU") val startupBackend: String = "SHIZUKU",

    @PrefKey(default = "false") val skipShizukuCheck: String = "false",

    @PrefKey(default = "false") val muteOnGameLaunch: String = "false",

    @PrefKey(default = "false") val closeAppOnTaskEnd: String = "false",

    @PrefKey(default = "false") val useHardwareScreenOff: String = "false",

    @PrefKey(default = "STABLE") val updateChannel: String = "STABLE",

    @PrefKey(default = "false") val showTouchPreview: String = "false",

    @PrefKey(default = "WHITE") val themeMode: String = "WHITE",

    @PrefKey(default = "DEFAULT") val eventNotificationLevel: String = "DEFAULT",

    /** BCP 47 language tag (e.g. "en", "zh"). Empty string = follow system. */
    @PrefKey(default = "") val appLanguage: String = "",
)
