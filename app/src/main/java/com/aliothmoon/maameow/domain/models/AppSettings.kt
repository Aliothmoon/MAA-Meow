package com.aliothmoon.maameow.domain.models

import com.aliothmoon.maameow.constant.OFFICIAL_SHIZUKU_PACKAGE
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
     * Shizuku 管理器快捷入口是否启用。
     * 入口包名默认官方 Shizuku，可由用户选择自定义应用。
     */
    @PrefKey(default = "false") val shizukuShortcutEnabled: String = "false",
    @PrefKey(default = OFFICIAL_SHIZUKU_PACKAGE) val shizukuLaunchPackage: String = OFFICIAL_SHIZUKU_PACKAGE,

    @PrefKey(default = "false") val muteOnGameLaunch: String = "false",

    /** Write-ahead package name for in-progress game mute session; empty when not muted. */
    @PrefKey(default = "") val mutedGamePackage: String = "",

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

    /** 定时任务触发时跳过锁屏检查 */
    @PrefKey(default = "false") val runScheduleWhenLocked: String = "false",

    /** 壁纸动态取色开关：开启后从自定义壁纸提取主色覆盖主题配色 */
    @PrefKey(default = "false") val useWallpaperColor: String = "false",

    /** 页面缩放比例（80~110，默认 100 = 1.0x） */
    @PrefKey(default = "100") val fontSizeScale: String = "100",

    /** 是否显示成就解锁时的 Snackbar 提示 */
    @PrefKey(default = "true") val showAchievementSnackbar: String = "true",

    /** 自定义壁纸 URI（content:// 或 file://），空串表示未设置 */
    @PrefKey(default = "") val wallpaperUri: String = "",

    /** 壁纸透明度 0~100，默认 100 */
    @PrefKey(default = "100") val wallpaperAlpha: String = "100",

    /** 壁纸模糊半径 0~12，默认 0 */
    @PrefKey(default = "0") val wallpaperBlur: String = "0",

    /** 壁纸磨砂玻璃效果：组件背景半透明透出壁纸，默认 false */
    @PrefKey(default = "false") val wallpaperTextContrast: String = "false",
    @PrefKey(default = "false") val wallpaperFrostedGlass: String = "false",
)
