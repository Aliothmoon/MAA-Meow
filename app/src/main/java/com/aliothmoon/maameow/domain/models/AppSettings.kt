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

    /** 非空表示该包可能残留 MaaMeow 设置的静音，需要在关闭或重连时恢复。 */
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
     * 后台虚拟显示器模式下，游戏漂移到主屏时是否自动拉回。
     * 部分 ROM（如 One UI / B 服 U8 SDK）会在启动或登录切换时把游戏挪回主屏，
     * 启用后 AppWatchdog 会周期检测并尝试把任务移回虚拟显示器。
     */
    @PrefKey(default = "true") val driftAutoRepinEnabled: String = "true",

    /**
     * 检测到漂移后等待多少秒再拉回（默认 5）。
     * 游戏启动后常有一段「登录弹窗 / SDK 鉴权」窗口期，立刻拉回会被游戏视为异常并
     * 重复弹出登录流程。留足延迟窗口让瞬态漂移自行回落，仅对持续漂移才介入。
     * 合法范围 1~60 秒。
     */
    @PrefKey(default = "5") val driftAutoRepinDelaySec: String = "5",

    /**
     * 是否启用 Android 特化任务覆盖（overrides/resource/tasks/tasks.json）
     * 启用后该目录作为最高优先级覆盖层，在加载链末位加载
     */
    @PrefKey(default = "false") val tasksOverrideEnabled: String = "false",

    @PrefKey(default = "false") val allowForegroundScheduledTask: String = "false",

    /** 定时任务触发时跳过锁屏检查 */
    @PrefKey(default = "false") val runScheduleWhenLocked: String = "false",

    /**
     * 是否启用系统莫奈主题色（Android 12+ Material You）
     * 启用后主题跟随系统壁纸动态取色，关闭则使用内置硬编码蓝色主题
     * Android 12 以下设备只能使用内置蓝色主题
     */
    @PrefKey(default = "false") val useSystemMonetColor: String = "false",

    /**
     * 页面缩放。
     * - `auto` / `0`：按最小宽度自动推荐（新装默认）
     * - `80`~`110`：手动百分比（已有用户存的值保持不动）
     */
    @PrefKey(default = "auto") val fontSizeScale: String = "auto",

    /** 是否显示成就解锁时的 Snackbar 提示 */
    @PrefKey(default = "true") val showAchievementSnackbar: String = "true",

    /** 是否启用主界面自定义图片背景（仅四个主 Tab 生效） */
    @PrefKey(default = "false") val customBackgroundEnabled: String = "false",

    /**
     * 图片文件固定存放在 filesDir/backgrounds/bg.jpg，路径本身无需持久化。
     */
    @PrefKey(default = "") val customBackgroundToken: String = "",

    /** 背景图不透明度 0~100（默认 80） */
    @PrefKey(default = "80") val customBackgroundImageAlpha: String = "80",

    /** 背景遮罩强度 0~100（默认 25，用于保证前景文字可读性） */
    @PrefKey(default = "25") val customBackgroundScrim: String = "25",

    /** 背景模糊强度 0~100（默认 0，仅 API 31+ 生效） */
    @PrefKey(default = "0") val customBackgroundBlur: String = "0",

    // ───────────────── 定时唤醒 + 解锁 ─────────────────

    /** 是否启用定时唤醒 + 解锁功能 */
    @PrefKey(default = "false") val wakeScheduleEnabled: String = "false",

    /**
     * 唤醒时间点列表，逗号分隔的 `HH:mm` 格式，如 `"07:30,12:00,19:00"`。
     * 非法项在解析时被过滤；为空或开关关闭时不设置任何闹钟。
     */
    @PrefKey(default = "") val wakeScheduleTimesCsv: String = "",

    /** 解锁策略：none / swipe / pin / password / keyguard */
    @PrefKey(default = "swipe") val wakeUnlockType: String = "swipe",

    /** 解锁密码 / PIN。空字符串表示不输入。 */
    @PrefKey(default = "") val wakeCredential: String = "",

    /** 解锁完成后等待 N 秒再自动息屏（默认 0 = 不息屏，用户自行管理） */
    @PrefKey(default = "0") val wakeAutoSleepDelaySec: String = "0",

    /**
     * 解锁滑动起点 X 坐标（屏幕宽度百分比 0.0–1.0）。`-1.0` 表示未校准，
     * 引擎回退到屏幕宽度 50%（居中）。用户通过「校准滑动起点」功能设置。
     */
    @PrefKey(default = "-1.0") val swipeStartXPercent: String = "-1.0",

    /**
     * 解锁滑动起点 Y 坐标（屏幕高度百分比 0.0–1.0）。`-1.0` 表示未校准，
     * 引擎回退到屏幕高度 90%（底部 10%）。用户通过「校准滑动起点」功能设置。
     */
    @PrefKey(default = "-1.0") val swipeStartYPercent: String = "-1.0",

    /**
     * swipe 后等待秒数（浮点字符串，如 "1.5"）。给锁屏动画 + PIN 键盘弹出 + 密码框
     * 获取焦点预留的时间。MIUI/ColorOS 等 ROM 动画较慢，默认 1.5s；AOSP 可以调低到 0.8s。
     */
    @PrefKey(default = "1.5") val wakePinWaitSec: String = "1.5",

    /**
     * 解锁失败最大重试次数。引擎在「shell 成功但仍锁屏」时会重新执行整个解锁序列，
     * 直到成功或达到上限。0 表示不重试。
     */
    @PrefKey(default = "2") val wakeUnlockMaxRetries: String = "2",
)
