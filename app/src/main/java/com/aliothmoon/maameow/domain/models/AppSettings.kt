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

    /** 见 [CoreDataLocation]，改了要重启 */
    @PrefKey(default = "APP_DIR")
    val coreDataLocation: String = "APP_DIR",

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

    /** 主任务链运行时长上限，到点停止并视为自动结束 */
    @PrefKey(default = "false")
    val runDurationLimitEnabled: String = "false",

    @PrefKey(default = "240")
    val runDurationLimitMinutes: String = "240",

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

    /** Live Updates 通知是否启用（Android 16+ promoted ongoing / ProgressStyle 展示）。 */
    @PrefKey(default = "true")
    val liveUpdateEnabled: String = "true",

    /** 小米设备上是否用超级岛（焦点通知）展示：关闭则回退原生实时更新样式。 */
    @PrefKey(default = "true")
    val liveUpdateUseHyperIsland: String = "true",

    /** Live Updates 状态栏 chip 短关键文本内容：BOTH=进度+任务名 / PROGRESS=仅进度 / TASK=仅任务名 / LOG=最新日志 / NONE=不显示。 */
    @PrefKey(default = "BOTH")
    val liveUpdateChipContent: String = "BOTH",

    /** Live Updates 进度条颜色方案：DEFAULT/BLUE/GREEN/ORANGE/PURPLE/PINK/TEAL/CUSTOM。 */
    @PrefKey(default = "DEFAULT")
    val liveUpdateColorScheme: String = "DEFAULT",

    /** Live Updates 自定义主色 HEX（如 "#2196F3"），仅 liveUpdateColorScheme=CUSTOM 时使用。 */
    @PrefKey(default = "")
    val liveUpdateCustomColor: String = "",

    /** Live Updates 追踪图标：DEFAULT=合成玉 / LOGO=MAA 图标 / DOT=圆点 / CUSTOM=自定义图片。 */
    @PrefKey(default = "DEFAULT")
    val liveUpdateTrackerIcon: String = "DEFAULT",

    /** Live Updates 自定义追踪图标文件路径，仅 liveUpdateTrackerIcon=CUSTOM 时使用。 */
    @PrefKey(default = "")
    val liveUpdateCustomTrackerPath: String = "",

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

    /** 划火柴模式（Core DeploymentWithPause）；与 WPF RuntimeSettings.DeployWithPause 同名同默认 */
    @PrefKey(default = "false")
    val deployWithPause: String = "false",

    @PrefKey(name = "announcement_read_version", default = "")
    val announcementReadHash: String = "",

    /** 已看过的首启引导版本，小于当前版本则展示 */
    @PrefKey(default = "0")
    val onboardingSeenVersion: String = "0",

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

    /** 一图流 OpenAPI Token，只用于干员识别拉取 */
    @PrefKey(default = "")
    val yituliuOpenApiToken: String = "",

    /** 干员识别从一图流拉取，不再进游戏截图识别 */
    @PrefKey(default = "false")
    val operBoxUseYituliuApi: String = "false",

    /** 喝醉过但还没醒酒，下次启动提示 */
    @PrefKey(default = "false")
    val pallasHangover: String = "false",

    )
