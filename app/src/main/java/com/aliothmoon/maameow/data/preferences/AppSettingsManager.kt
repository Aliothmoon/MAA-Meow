package com.aliothmoon.maameow.data.preferences

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.data.achievement.AchievementEvents
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.model.update.UpdateSource
import com.aliothmoon.maameow.data.preferences.AppSettingsManager.Companion.FONT_SIZE_SCALE_AUTO
import com.aliothmoon.maameow.domain.models.AppSettings
import com.aliothmoon.maameow.domain.models.AppSettingsSchema
import com.aliothmoon.maameow.domain.models.CoreDataLocation
import com.aliothmoon.maameow.domain.models.OverlayControlMode
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.models.RunDurationLimit
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.models.UnlockCredential
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList


/** 构造不等待读盘；启动时读取 .value 前须先 awaitLoaded */
class AppSettingsManager internal constructor(
    private val context: Context,
    private val achievementRepository: AchievementRepository,
    private val scope: CoroutineScope,
) {
    constructor(context: Context, achievementRepository: AchievementRepository) : this(
        context,
        achievementRepository,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    companion object {
        val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

        /** 解锁方式：滑动 / 手动录制（录制触控序列回放，可覆盖密码锁屏）/ PIN */
        const val WAKE_TYPE_SWIPE = UnlockCredential.TYPE_SWIPE
        const val WAKE_TYPE_PIN = UnlockCredential.TYPE_PIN
        const val WAKE_TYPE_GESTURE = UnlockCredential.TYPE_GESTURE
        val WAKE_UNLOCK_TYPES = UnlockCredential.TYPES

        /** 纯数字 PIN 最大位数 */
        const val MAX_PIN_LENGTH = 16

        /** 首启引导版本，内容大改需全员重看时 +1 */
        const val ONBOARDING_VERSION = 1

        /** 页面缩放：0 = 自动；手动为 80–110 */
        const val FONT_SIZE_SCALE_MIN = 80
        const val FONT_SIZE_SCALE_MAX = 110
        const val FONT_SIZE_SCALE_AUTO = 0
        const val FONT_SIZE_SCALE_DEFAULT = FONT_SIZE_SCALE_AUTO

        /**
         * 解析存储值。
         * - `"auto"` / `"0"` → [FONT_SIZE_SCALE_AUTO]
         * - `80`–`110` → 对应整数
         * - 非法 → 默认自动
         */
        fun parseFontSizeScale(raw: String): Int {
            if (raw.equals("auto", ignoreCase = true) || raw == "0") {
                return FONT_SIZE_SCALE_AUTO
            }
            val n = raw.toIntOrNull() ?: return FONT_SIZE_SCALE_DEFAULT
            if (n == FONT_SIZE_SCALE_AUTO) return FONT_SIZE_SCALE_AUTO
            if (n in FONT_SIZE_SCALE_MIN..FONT_SIZE_SCALE_MAX) return n
            return FONT_SIZE_SCALE_DEFAULT
        }

        fun isFontSizeScaleAuto(scale: Int): Boolean = scale == FONT_SIZE_SCALE_AUTO

        /**
         * 得到实际生效的页面缩放百分比
         * 自动模式见 [com.aliothmoon.maameow.utils.UiScale.recommendedFontSizeScale]
         */
        fun resolveFontSizeScale(
            stored: Int,
            smallestWidthDp: Int,
            fontScale: Float,
        ): Int {
            if (!isFontSizeScaleAuto(stored)) {
                return stored.coerceIn(FONT_SIZE_SCALE_MIN, FONT_SIZE_SCALE_MAX)
            }
            return com.aliothmoon.maameow.utils.UiScale.recommendedFontSizeScale(
                smallestWidthDp = smallestWidthDp,
                fontScale = fontScale,
            )
        }
    }

    val settings: Flow<AppSettings> = with(AppSettingsSchema) { context.dataStore.flow }

    private val defaults = AppSettings()

    @Volatile
    private var initialSettings: AppSettings? = null
    private val loaded = CompletableDeferred<Unit>()
    private val settingUpdates = CopyOnWriteArrayList<(AppSettings) -> Unit>()

    /** 首次快照的所有字段发布后才放行业务初始化 */
    suspend fun awaitLoaded() = loaded.await()

    private fun <T> setting(read: (AppSettings) -> T): StateFlow<T> {
        val state = MutableStateFlow(read(defaults))
        settingUpdates += { state.value = read(it) }
        // 晚于 init 块声明的设置项在此补发
        initialSettings?.let { state.value = read(it) }
        return state.asStateFlow()
    }

    suspend fun setSettings(settings: AppSettings) {
        with(AppSettingsSchema) { context.dataStore.update(settings) }
    }

    // 悬浮窗模式
    val overlayControlMode: StateFlow<OverlayControlMode> = setting {
        runCatching { OverlayControlMode.valueOf(it.overlayMode) }
            .getOrDefault(OverlayControlMode.ACCESSIBILITY)
    }

    suspend fun setFloatWindowMode(mode: OverlayControlMode) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[overlayMode] = mode.name }
        }
    }

    // 运行模式
    val runMode: StateFlow<RunMode> = setting {
        runCatching { RunMode.valueOf(it.runMode) }
            .getOrDefault(RunMode.BACKGROUND)
    }

    suspend fun setRunMode(mode: RunMode) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[runMode] = mode.name }
        }
    }

    // 更新源
    val updateSource: StateFlow<UpdateSource> = setting { s ->
        runCatching {
            UpdateSource.entries
                .find { it.type == s.updateSource.toInt() }
                ?: UpdateSource.GITHUB
        }
            .getOrDefault(UpdateSource.GITHUB)
    }

    suspend fun setUpdateSource(source: UpdateSource) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[updateSource] = source.type.toString() }
        }
    }

    // Mirror酱 CDK
    val mirrorChyanCdk: StateFlow<String> = setting { it.mirrorChyanCdk }

    suspend fun setMirrorChyanCdk(cdk: String) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[mirrorChyanCdk] = cdk }
        }
    }

    // 调试模式
    val debugMode: StateFlow<Boolean> = setting { it.debugMode.toBooleanStrictOrNull() ?: false }

    suspend fun setDebugMode(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[debugMode] = enabled.toString() }
        }
    }

    // 启动时自动检查更新
    val autoCheckUpdate: StateFlow<Boolean> = setting { it.autoCheckUpdate.toBooleanStrictOrNull() ?: true }

    suspend fun setAutoCheckUpdate(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[autoCheckUpdate] = enabled.toString() }
        }
    }

    // 启动时自动下载更新
    val autoDownloadUpdate: StateFlow<Boolean> = setting { it.autoDownloadUpdate.toBooleanStrictOrNull() ?: false }

    suspend fun setAutoDownloadUpdate(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[autoDownloadUpdate] = enabled.toString() }
        }
    }

    // IPC服务启动模式
    val startupBackend: StateFlow<RemoteBackend> = setting {
        runCatching { RemoteBackend.valueOf(it.startupBackend) }
            .getOrDefault(RemoteBackend.SHIZUKU)
    }

    suspend fun setStartupBackend(backend: RemoteBackend) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[startupBackend] = backend.name }
        }
    }

    // MaaCore 数据目录
    val coreDataLocation: StateFlow<CoreDataLocation> = setting { CoreDataLocation.parse(it.coreDataLocation) }

    suspend fun setCoreDataLocation(location: CoreDataLocation) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[coreDataLocation] = location.name }
        }
    }

    // 跳过 Shizuku 检查
    val skipShizukuCheck: StateFlow<Boolean> = setting { it.skipShizukuCheck.toBooleanStrictOrNull() ?: false }

    suspend fun setSkipShizukuCheck(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[skipShizukuCheck] = enabled.toString() }
        }
    }

    // Shizuku 管理器快捷入口是否启用
    val shizukuShortcutEnabled: StateFlow<Boolean> =
        setting { it.shizukuShortcutEnabled.toBooleanStrictOrNull() ?: false }

    suspend fun setShizukuShortcutEnabled(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[shizukuShortcutEnabled] = enabled.toString() }
        }
    }

    // Shizuku 管理器入口包名，始终保持为非空包名。
    val shizukuLaunchPackage: StateFlow<String> = setting { it.shizukuLaunchPackage }

    suspend fun setShizukuLaunchPackage(packageName: String) {
        val trimmedPackageName = packageName.trim()
        require(trimmedPackageName.isNotEmpty()) { "shizukuLaunchPackage must not be blank" }
        with(AppSettingsSchema) {
            context.dataStore.edit {
                it[shizukuLaunchPackage] = trimmedPackageName
            }
        }
    }

    // 游戏启动时静音
    val muteOnGameLaunch: StateFlow<Boolean> = setting { it.muteOnGameLaunch.toBooleanStrictOrNull() ?: false }

    suspend fun setMuteOnGameLaunch(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[muteOnGameLaunch] = enabled.toString() }
        }
    }

    val initialMutedGamePackage: String
        get() = checkNotNull(initialSettings) { "Settings have not loaded" }.mutedGamePackage

    internal suspend fun setMutedGamePackage(packageName: String) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[mutedGamePackage] = packageName }
        }
    }

    // 任务结束时关闭应用
    val closeAppOnTaskEnd: StateFlow<Boolean> = setting { it.closeAppOnTaskEnd.toBooleanStrictOrNull() ?: false }

    suspend fun setCloseAppOnTaskEnd(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[closeAppOnTaskEnd] = enabled.toString() }
        }
    }

    val runDurationLimitEnabled: StateFlow<Boolean> =
        setting { it.runDurationLimitEnabled.toBooleanStrictOrNull() ?: false }

    suspend fun setRunDurationLimitEnabled(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[runDurationLimitEnabled] = enabled.toString() }
        }
    }

    val runDurationLimitMinutes: StateFlow<Int> = setting {
        it.runDurationLimitMinutes.toIntOrNull()
            ?.coerceIn(RunDurationLimit.MIN_MINUTES, RunDurationLimit.MAX_MINUTES)
            ?: RunDurationLimit.DEFAULT_MINUTES
    }

    suspend fun setRunDurationLimitMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(RunDurationLimit.MIN_MINUTES, RunDurationLimit.MAX_MINUTES)
        with(AppSettingsSchema) {
            context.dataStore.edit { it[runDurationLimitMinutes] = clamped.toString() }
        }
    }

    val deployWithPause: StateFlow<Boolean> = setting { it.deployWithPause.toBooleanStrictOrNull() ?: false }

    suspend fun setDeployWithPause(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[deployWithPause] = enabled.toString() }
        }
    }

    val useHardwareScreenOff: StateFlow<Boolean> = setting { it.useHardwareScreenOff.toBooleanStrictOrNull() ?: false }

    suspend fun setUseHardwareScreenOff(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[useHardwareScreenOff] = enabled.toString() }
        }
    }

    // 触摸预览
    val showTouchPreview: StateFlow<Boolean> = setting { it.showTouchPreview.toBooleanStrictOrNull() ?: false }

    suspend fun setShowTouchPreview(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[showTouchPreview] = enabled.toString() }
        }
    }

    // 画中画
    val pipOnHome: StateFlow<Boolean> = setting { it.pipOnHome.toBooleanStrictOrNull() ?: true }

    suspend fun setPipOnHome(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[pipOnHome] = enabled.toString() }
        }
    }

    // 更新渠道
    val updateChannel: StateFlow<UpdateChannel> = setting {
        runCatching { UpdateChannel.valueOf(it.updateChannel) }
            .getOrDefault(UpdateChannel.STABLE)
    }

    suspend fun setUpdateChannel(channel: UpdateChannel) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[updateChannel] = channel.name }
        }
    }

    // 主题模式
    enum class ThemeMode {
        SYSTEM, WHITE, DARK, PURE_DARK
    }

    val themeMode: StateFlow<ThemeMode> = setting {
        runCatching {
            ThemeMode.valueOf(if (it.themeMode == "LIGHT") "WHITE" else it.themeMode)
        }.getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[themeMode] = mode.name }
        }
    }

    // 内部通知级别
    enum class EventNotificationLevel(@param:StringRes val labelRes: Int) {
        OFF(R.string.notification_level_off),
        DEFAULT(R.string.notification_level_default),
        HIGH(R.string.notification_level_high),
    }

    val eventNotificationLevel: StateFlow<EventNotificationLevel> = setting {
        runCatching { EventNotificationLevel.valueOf(it.eventNotificationLevel) }
            .getOrDefault(EventNotificationLevel.DEFAULT)
    }

    suspend fun setEventNotificationLevel(level: EventNotificationLevel) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[eventNotificationLevel] = level.name }
        }
    }

    // 超级岛断网旁路
    val liveIslandXmsfBypass: StateFlow<Boolean> = setting { it.liveIslandXmsfBypass.toBooleanStrictOrNull() ?: true }

    suspend fun setLiveIslandXmsfBypass(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[liveIslandXmsfBypass] = enabled.toString() }
        }
    }

    // Live Updates 通知自定义
    enum class LiveUpdateChipContent(@param:StringRes val labelRes: Int) {
        BOTH(R.string.live_update_chip_both),
        PROGRESS(R.string.live_update_chip_progress),
        TASK(R.string.live_update_chip_task),
        LOG(R.string.live_update_chip_log),
        NONE(R.string.live_update_chip_none),
    }

    enum class LiveUpdateColorScheme(@param:StringRes val labelRes: Int) {
        DEFAULT(R.string.live_update_color_default),
        BLUE(R.string.live_update_color_blue),
        GREEN(R.string.live_update_color_green),
        ORANGE(R.string.live_update_color_orange),
        PURPLE(R.string.live_update_color_purple),
        PINK(R.string.live_update_color_pink),
        TEAL(R.string.live_update_color_teal),
        CUSTOM(R.string.live_update_color_custom),
    }

    enum class LiveUpdateTrackerIcon(@param:StringRes val labelRes: Int) {
        DEFAULT(R.string.live_update_icon_default),
        LOGO(R.string.live_update_icon_logo),
        DOT(R.string.live_update_icon_dot),
        CUSTOM(R.string.live_update_icon_custom),
    }

    /// Live Updates 通知是否启用（Android 16+ promoted ongoing / ProgressStyle 展示）。
    val liveUpdateEnabled: StateFlow<Boolean> = setting { it.liveUpdateEnabled.toBooleanStrictOrNull() ?: true }

    suspend fun setLiveUpdateEnabled(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[liveUpdateEnabled] = enabled.toString() }
        }
    }

    /// 小米设备上是否用超级岛（焦点通知）展示：关闭则回退原生实时更新样式。
    val liveUpdateUseHyperIsland: StateFlow<Boolean> =
        setting { it.liveUpdateUseHyperIsland.toBooleanStrictOrNull() ?: true }

    suspend fun setLiveUpdateUseHyperIsland(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[liveUpdateUseHyperIsland] = enabled.toString() }
        }
    }

    /// Live Updates 状态栏 chip 短关键文本内容（both=进度+任务名 / progress=仅进度 / task=仅任务名 / log=最新日志 / none=不显示）。
    val liveUpdateChipContent: StateFlow<LiveUpdateChipContent> = setting {
        runCatching { LiveUpdateChipContent.valueOf(it.liveUpdateChipContent) }
            .getOrDefault(LiveUpdateChipContent.BOTH)
    }

    suspend fun setLiveUpdateChipContent(content: LiveUpdateChipContent) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[liveUpdateChipContent] = content.name }
        }
    }

    /// Live Updates 进度条颜色方案（default/blue/green/orange/purple/pink/teal/custom）。
    val liveUpdateColorScheme: StateFlow<LiveUpdateColorScheme> = setting {
        runCatching { LiveUpdateColorScheme.valueOf(it.liveUpdateColorScheme) }
            .getOrDefault(LiveUpdateColorScheme.DEFAULT)
    }

    suspend fun setLiveUpdateColorScheme(scheme: LiveUpdateColorScheme) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[liveUpdateColorScheme] = scheme.name }
        }
    }

    /// Live Updates 自定义主色 HEX（如 "#2196F3"），仅 liveUpdateColorScheme=custom 时使用。
    val liveUpdateCustomColor: StateFlow<String> = setting { it.liveUpdateCustomColor }

    suspend fun setLiveUpdateCustomColor(color: String) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[liveUpdateCustomColor] = color }
        }
    }

    /// Live Updates 进度条追踪图标（default=合成玉 / logo=MAA 图标 / dot=圆点 / custom=自定义图片）。
    val liveUpdateTrackerIcon: StateFlow<LiveUpdateTrackerIcon> = setting {
        runCatching { LiveUpdateTrackerIcon.valueOf(it.liveUpdateTrackerIcon) }
            .getOrDefault(LiveUpdateTrackerIcon.DEFAULT)
    }

    suspend fun setLiveUpdateTrackerIcon(icon: LiveUpdateTrackerIcon) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[liveUpdateTrackerIcon] = icon.name }
        }
    }

    /// Live Updates 自定义追踪图标文件路径，仅 liveUpdateTrackerIcon=custom 时使用。
    val liveUpdateCustomTrackerPath: StateFlow<String> = setting { it.liveUpdateCustomTrackerPath }

    suspend fun setLiveUpdateCustomTrackerPath(path: String) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[liveUpdateCustomTrackerPath] = path }
        }
    }

    // 后台虚拟屏分辨率
    val backgroundResolution: StateFlow<DefaultDisplayConfig.ResolutionPreference> = setting {
        runCatching { DefaultDisplayConfig.ResolutionPreference.valueOf(it.backgroundResolution) }
            .getOrDefault(DefaultDisplayConfig.ResolutionPreference.P720)
    }

    suspend fun setBackgroundResolution(pref: DefaultDisplayConfig.ResolutionPreference) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[backgroundResolution] = pref.name }
        }
    }

    // 应用语言
    enum class AppLanguage(val tag: String) {
        // 仅用于兼容旧数据；启动时会被收敛成显式语言。
        SYSTEM(""),
        ZH("zh"),
        EN("en"),
    }

    /** 资源与干员名用的语言码，system 收敛成显式语言 */
    val displayLanguage: String
        get() = ResourceDataManager.displayLanguageCode(
            LocaleBootstrap.resolveSelectedLanguage(language.value)
        )

    val language: StateFlow<AppLanguage> = setting {
        runCatching { AppLanguage.valueOf(it.language) }
            .getOrDefault(AppLanguage.SYSTEM)
    }

    suspend fun setLanguage(lang: AppLanguage) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[language] = lang.name }
        }
        achievementRepository.report {
            event = AchievementEvents.LANGUAGE_CHANGED
        }
    }

    // 待展示的更新公告
    val pendingChangelogVersion: StateFlow<String> = setting { it.pendingChangelogVersion }

    val pendingChangelogContent: StateFlow<String> = setting { it.pendingChangelogContent }

    suspend fun savePendingChangelog(version: String, content: String) {
        with(AppSettingsSchema) {
            context.dataStore.edit {
                it[pendingChangelogVersion] = version.removePrefix("v")
                it[pendingChangelogContent] = content
            }
        }
    }

    val currentChangelogVersion: StateFlow<String> = setting { it.currentChangelogVersion }

    val currentChangelogContent: StateFlow<String> = setting { it.currentChangelogContent }

    /** 单次 edit 内完成，避免中途崩溃导致两份都丢 */
    suspend fun promotePendingChangelog() {
        with(AppSettingsSchema) {
            context.dataStore.edit {
                val version = it[pendingChangelogVersion].orEmpty()
                val content = it[pendingChangelogContent].orEmpty()
                if (version.isNotEmpty() && content.isNotEmpty()) {
                    it[currentChangelogVersion] = version.removePrefix("v")
                    it[currentChangelogContent] = content
                }
                it[pendingChangelogVersion] = ""
                it[pendingChangelogContent] = ""
            }
        }
    }

    // 虚拟屏启动游戏时强制全屏模式
    val forceFullscreenOnVirtualDisplay: StateFlow<Boolean> =
        setting { it.forceFullscreenOnVirtualDisplay.toBooleanStrictOrNull() ?: false }

    suspend fun setForceFullscreenOnVirtualDisplay(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[forceFullscreenOnVirtualDisplay] = enabled.toString() }
        }
    }

    // Android 任务配置覆盖开关
    val tasksOverrideEnabled: StateFlow<Boolean> = setting { it.tasksOverrideEnabled.toBooleanStrictOrNull() ?: false }

    suspend fun setTasksOverrideEnabled(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[tasksOverrideEnabled] = enabled.toString() }
        }
    }

    val announcementReadHash: StateFlow<String> = setting { it.announcementReadHash }

    suspend fun setAnnouncementReadHash(hash: String) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[announcementReadHash] = hash }
        }
    }

    private fun parseNeedsOnboarding(raw: String): Boolean =
        (raw.toIntOrNull() ?: 0) < ONBOARDING_VERSION

    val needsOnboarding: StateFlow<Boolean> = setting { parseNeedsOnboarding(it.onboardingSeenVersion) }

    suspend fun markOnboardingSeen() {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[onboardingSeenVersion] = ONBOARDING_VERSION.toString() }
        }
    }

    // 是否启用系统莫奈主题色（Android 12+ Material You）
    private fun parseUseSystemMonetColor(raw: String): Boolean =
        raw.toBooleanStrictOrNull() ?: true

    val useSystemMonetColor: StateFlow<Boolean> = setting { parseUseSystemMonetColor(it.useSystemMonetColor) }

    suspend fun setUseSystemMonetColor(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[useSystemMonetColor] = enabled.toString() }
        }
    }

    // 页面缩放（0=自动，或 80~110 手动）
    val fontSizeScale: StateFlow<Int> = setting { parseFontSizeScale(it.fontSizeScale) }

    suspend fun setFontSizeScale(scale: Int) {
        with(AppSettingsSchema) {
            context.dataStore.edit {
                it[fontSizeScale] = if (isFontSizeScaleAuto(scale)) {
                    "auto"
                } else {
                    scale.coerceIn(FONT_SIZE_SCALE_MIN, FONT_SIZE_SCALE_MAX).toString()
                }
            }
        }
    }

    // 是否显示成就解锁时的 Snackbar 提示
    val showAchievementSnackbar: StateFlow<Boolean> =
        setting { it.showAchievementSnackbar.toBooleanStrictOrNull() ?: true }

    suspend fun setShowAchievementSnackbar(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[showAchievementSnackbar] = enabled.toString() }
        }
    }

    // ============ 自定义图片背景（仅四个主 Tab 生效）============

    /** 将 0~100 的原始字符串解析为合法百分比 */
    private fun parsePercent(raw: String, default: Int): Int =
        raw.toIntOrNull()?.coerceIn(0, 100) ?: default

    val customBackgroundEnabled: StateFlow<Boolean> =
        setting { it.customBackgroundEnabled.toBooleanStrictOrNull() ?: false }

    suspend fun setCustomBackgroundEnabled(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[customBackgroundEnabled] = enabled.toString() }
        }
    }

    val customBackgroundToken: StateFlow<String> = setting { it.customBackgroundToken }

    /** 保存/清除背景时开关与令牌总是成对变更，合并为一次写入避免中间态。 */
    suspend fun setCustomBackgroundState(enabled: Boolean, token: String) {
        with(AppSettingsSchema) {
            context.dataStore.edit {
                it[customBackgroundEnabled] = enabled.toString()
                it[customBackgroundToken] = token
            }
        }
    }

    val customBackgroundImageAlpha: StateFlow<Int> = setting { parsePercent(it.customBackgroundImageAlpha, 80) }

    suspend fun setCustomBackgroundImageAlpha(value: Int) {
        with(AppSettingsSchema) {
            context.dataStore.edit {
                it[customBackgroundImageAlpha] = value.coerceIn(0, 100).toString()
            }
        }
    }

    val customBackgroundScrim: StateFlow<Int> = setting { parsePercent(it.customBackgroundScrim, 25) }

    suspend fun setCustomBackgroundScrim(value: Int) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[customBackgroundScrim] = value.coerceIn(0, 100).toString() }
        }
    }

    val customBackgroundBlur: StateFlow<Int> = setting { parsePercent(it.customBackgroundBlur, 0) }

    suspend fun setCustomBackgroundBlur(value: Int) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[customBackgroundBlur] = value.coerceIn(0, 100).toString() }
        }
    }

    // ───────────────── 唤醒 + 解锁 ─────────────────

    val wakeUnlockType: StateFlow<String> = setting {
        val t = it.wakeUnlockType
        if (t in WAKE_UNLOCK_TYPES) t else "swipe"
    }

    suspend fun setWakeUnlockType(type: String) {
        if (type !in WAKE_UNLOCK_TYPES) return
        with(AppSettingsSchema) {
            context.dataStore.edit { it[wakeUnlockType] = type }
        }
    }

    val wakeCredential: StateFlow<String> = setting { it.wakeCredential }

    suspend fun setWakeCredential(credential: String) {
        // 注入走 KEYCODE_0..9，仅保留数字
        val digits = credential.filter { it.isDigit() }.take(MAX_PIN_LENGTH)
        with(AppSettingsSchema) {
            context.dataStore.edit { it[wakeCredential] = digits }
        }
    }

    val reportToPenguin: StateFlow<Boolean> = setting { it.reportToPenguin.toBooleanStrictOrNull() ?: true }

    suspend fun setReportToPenguin(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[reportToPenguin] = enabled.toString() }
        }
    }

    val reportToYituliu: StateFlow<Boolean> = setting { it.reportToYituliu.toBooleanStrictOrNull() ?: true }

    suspend fun setReportToYituliu(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[reportToYituliu] = enabled.toString() }
        }
    }

    val penguinId: StateFlow<String> = setting { it.penguinId }

    suspend fun setPenguinId(id: String) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[penguinId] = id.trim() }
        }
    }

    val yituliuOpenApiToken: StateFlow<String> = setting { it.yituliuOpenApiToken }

    suspend fun setYituliuOpenApiToken(token: String) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[yituliuOpenApiToken] = token.trim() }
        }
    }

    val operBoxUseYituliuApi: StateFlow<Boolean> =
        setting { it.operBoxUseYituliuApi.toBooleanStrictOrNull() ?: false }

    suspend fun setOperBoxUseYituliuApi(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[operBoxUseYituliuApi] = enabled.toString() }
        }
    }

    // 放在类体末尾，启动收集器时 setting() 已全部注册
    init {
        // 单次收集统一更新，避免就绪标记早于各字段的 stateIn
        scope.launch {
            settings.collect { snapshot ->
                if (initialSettings == null) initialSettings = snapshot
                settingUpdates.forEach { it(snapshot) }
                loaded.complete(Unit)
            }
        }.invokeOnCompletion { cause ->
            if (cause != null) loaded.completeExceptionally(cause)
        }
    }

}
