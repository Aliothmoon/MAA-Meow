package com.aliothmoon.maameow.domain.models

import com.aliothmoon.maameow.constant.OFFICIAL_SHIZUKU_PACKAGE
import com.aliothmoon.preferences.PrefKey
import com.aliothmoon.preferences.PrefSchema
import kotlinx.serialization.Serializable

@Serializable
@PrefSchema
data class AppSettings(
    @PrefKey(default = "ACCESSIBILITY")
    val overlayMode: String = "ACCESSIBILITY",

    @PrefKey(default = "BACKGROUND")
    val runMode: String = "BACKGROUND",

    @PrefKey(default = "GITHUB")
    val updateSource: String = "GITHUB",

    @PrefKey(default = "")
    val mirrorChyanCdk: String = "",

    @PrefKey(default = "false")
    val debugMode: String = "false",

    @PrefKey(default = "true")
    val autoCheckUpdate: String = "true",

    @PrefKey(default = "false")
    val autoDownloadUpdate: String = "false",

    @PrefKey(default = "SHIZUKU")
    val startupBackend: String = "SHIZUKU",

    @PrefKey(default = "false")
    val skipShizukuCheck: String = "false",

    /**
     * Shizuku 管理器快捷入口是否启用。
     * 入口包名默认官方 Shizuku，可由用户选择自定义应用。
     */
    @PrefKey(default = "false")
    val shizukuShortcutEnabled: String = "false",

    @PrefKey(default = OFFICIAL_SHIZUKU_PACKAGE)
    val shizukuLaunchPackage: String = OFFICIAL_SHIZUKU_PACKAGE,

    @PrefKey(default = "false")
    val muteOnGameLaunch: String = "false",

    /** 非空表示该包可能残留 MaaMeow 设置的静音，需要在关闭或重连时恢复。 */
    @PrefKey(default = "")
    val mutedGamePackage: String = "",

    @PrefKey(default = "false")
    val closeAppOnTaskEnd: String = "false",

    @PrefKey(default = "false")
    val useHardwareScreenOff: String = "false",

    @PrefKey(default = "STABLE")
    val updateChannel: String = "STABLE",

    @PrefKey(default = "false")
    val showTouchPreview: String = "false",

    @PrefKey(default = "SYSTEM")
    val themeMode: String = "SYSTEM",

    @PrefKey(default = "DEFAULT")
    val eventNotificationLevel: String = "DEFAULT",

    @PrefKey(default = "true")
    val liveIslandXmsfBypass: String = "true",

    @PrefKey(default = "P720")
    val backgroundResolution: String = "P720",

    @PrefKey(default = "SYSTEM")
    val language: String = "SYSTEM",

    @PrefKey(default = "")
    val pendingChangelogVersion: String = "",

    @PrefKey(default = "")
    val pendingChangelogContent: String = "",

    // 与 pending 分开存：pending 会被下一次检查覆盖成尚未安装版本的日志
    @PrefKey(default = "")
    val currentChangelogVersion: String = "",

    @PrefKey(default = "")
    val currentChangelogContent: String = "",

    /**
     * 自动战斗 干员部署"按住-暂停"模式 (对应 Core ControlFeat::SWIPE_WITH_PAUSE)
     * 启用后部署干员前会模拟按住 ESC 暂停游戏, 提高干员部署精确度;
     * 个别设备上 ESC 注入异常时可关闭, 改用普通滑动部署
     */
    @PrefKey(default = "true")
    val deploymentWithPause: String = "true",

    @PrefKey(name = "announcement_read_version", default = "")
    val announcementReadHash: String = "",

    @PrefKey(default = "false")
    val forceFullscreenOnVirtualDisplay: String = "false",

    /**
     * 后台模式任务运行中，回桌面时是否自动进入系统画中画
     * 会改变按 Home 的默认行为，故可关
     */
    @PrefKey(default = "true")
    val pipOnHome: String = "true",

    /**
     * 是否启用 Android 特化任务覆盖（overrides/resource/tasks/tasks.json）
     * 启用后该目录作为最高优先级覆盖层，在加载链末位加载
     */
    @PrefKey(default = "false")
    val tasksOverrideEnabled: String = "false",

    /**
     * 是否启用系统莫奈主题色（Android 12+ Material You）
     * 启用后主题跟随系统壁纸动态取色，关闭则使用内置硬编码蓝色主题
     * Android 12 以下设备只能使用内置蓝色主题
     */
    @PrefKey(default = "false")
    val useSystemMonetColor: String = "false",

    /**
     * 页面缩放。
     * - `auto` / `0`：按最小宽度自动推荐（新装默认）
     * - `80`~`110`：手动百分比（已有用户存的值保持不动）
     */
    @PrefKey(default = "auto")
    val fontSizeScale: String = "auto",

    /** 是否显示成就解锁时的 Snackbar 提示 */
    @PrefKey(default = "true")
    val showAchievementSnackbar: String = "true",

    /** 是否启用主界面自定义图片背景（仅四个主 Tab 生效） */
    @PrefKey(default = "false")
    val customBackgroundEnabled: String = "false",

    /**
     * 图片文件固定存放在 filesDir/backgrounds/bg.jpg，路径本身无需持久化。
     */
    @PrefKey(default = "")
    val customBackgroundToken: String = "",

    /** 背景图不透明度 0~100（默认 80） */
    @PrefKey(default = "80")
    val customBackgroundImageAlpha: String = "80",

    /** 背景遮罩强度 0~100（默认 25，用于保证前景文字可读性） */
    @PrefKey(default = "25")
    val customBackgroundScrim: String = "25",

    /** 背景模糊强度 0~100（默认 0，仅 API 31+ 生效） */
    @PrefKey(default = "0")
    val customBackgroundBlur: String = "0",

    // ───────────────── 定时唤醒 + 解锁 ─────────────────

    /** 解锁方式：swipe / pin，默认滑动（无密码） */
    @PrefKey(default = "swipe")
    val wakeUnlockType: String = "swipe",

    /** 解锁 PIN（纯数字）；仅 type=pin 时使用 */
    @PrefKey(default = "")
    val wakeCredential: String = "",

    @PrefKey(default = "true")
    val reportToPenguin: String = "true",

    @PrefKey(default = "true")
    val reportToYituliu: String = "true",

    /** 企鹅物流 ID；一图流 uuid 共用 */
    @PrefKey(default = "")
    val penguinId: String = "",

    )
