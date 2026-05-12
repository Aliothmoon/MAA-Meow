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

    @PrefKey(default = "P720") val backgroundResolution: String = "P720",

    @PrefKey(default = "SYSTEM") val language: String = "SYSTEM",

    @PrefKey(default = "") val pendingChangelogVersion: String = "",
    @PrefKey(default = "") val pendingChangelogContent: String = "",

    /**
     * 自动战斗 干员部署"按住-暂停"模式 (对应 Core ControlFeat::SWIPE_WITH_PAUSE)
     * 启用后部署干员前会模拟按住 ESC 暂停游戏, 提高干员部署精确度;
     * 个别设备上 ESC 注入异常时可关闭, 改用普通滑动部署
     */
    @PrefKey(default = "true") val deploymentWithPause: String = "true",
)
