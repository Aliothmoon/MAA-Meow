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

    /**
     * Shizuku 管理器快捷入口设置。
     * 模式默认关闭；自定义包名仅在 CUSTOM 模式下作为打开入口使用。
     */
    @PrefKey(default = "OFF") val shizukuLaunchMode: String = "OFF",
    @PrefKey(default = "") val shizukuLaunchPackage: String = "",

    @PrefKey(default = "false") val muteOnGameLaunch: String = "false",

    @PrefKey(default = "false") val closeAppOnTaskEnd: String = "false",

    @PrefKey(default = "false") val useHardwareScreenOff: String = "false",

    @PrefKey(default = "STABLE") val updateChannel: String = "STABLE",

    @PrefKey(default = "false") val showTouchPreview: String = "false",

    @PrefKey(default = "SYSTEM") val themeMode: String = "SYSTEM",

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

    @PrefKey(default = "") val announcementReadVersion: String = "",

    @PrefKey(default = "false") val forceFullscreenOnVirtualDisplay: String = "false",

    /**
     * 是否启用 Android 特化任务覆盖（overrides/resource/tasks/tasks.json）
     * 启用后该目录作为最高优先级覆盖层，在加载链末位加载
     */
    @PrefKey(default = "false") val tasksOverrideEnabled: String = "false",

    @PrefKey(default = "false") val allowForegroundScheduledTask: String = "false",
    /**
     * 是否启用系统莫奈主题色（Android 12+ Material You）
     * 启用后主题跟随系统壁纸动态取色，关闭则使用内置硬编码蓝色主题
     * Android 12 以下设备只能使用内置蓝色主题
     */
    @PrefKey(default = "false") val useSystemMonetColor: String = "false",
)
