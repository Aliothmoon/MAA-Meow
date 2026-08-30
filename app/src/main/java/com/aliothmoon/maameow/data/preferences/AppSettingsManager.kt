package com.aliothmoon.maameow.data.preferences

import android.content.Context
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
import com.aliothmoon.maameow.domain.models.OverlayControlMode
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.models.UnlockCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking


class AppSettingsManager(
    private val context: Context,
    private val achievementRepository: AchievementRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    // 阻塞读取 DataStore 首次值，确保后续 .value 不会是默认值
    private val initialSettings: AppSettings = runBlocking { settings.first() }

    suspend fun setSettings(settings: AppSettings) {
        with(AppSettingsSchema) { context.dataStore.update(settings) }
    }

    // 悬浮窗模式
    val overlayControlMode: StateFlow<OverlayControlMode> = settings
        .map {
            runCatching { OverlayControlMode.valueOf(it.overlayMode) }
                .getOrDefault(OverlayControlMode.ACCESSIBILITY)
        }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            runCatching { OverlayControlMode.valueOf(initialSettings.overlayMode) }
                .getOrDefault(OverlayControlMode.ACCESSIBILITY)
        )

    suspend fun setFloatWindowMode(mode: OverlayControlMode) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[overlayMode] = mode.name }
        }
    }

    // 运行模式
    val runMode: StateFlow<RunMode> = settings
        .map {
            runCatching { RunMode.valueOf(it.runMode) }
                .getOrDefault(RunMode.BACKGROUND)
        }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            runCatching { RunMode.valueOf(initialSettings.runMode) }
                .getOrDefault(RunMode.BACKGROUND)
        )

    suspend fun setRunMode(mode: RunMode) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[runMode] = mode.name }
        }
    }

    // 更新源
    val updateSource: StateFlow<UpdateSource> = settings
        .map { s ->
            runCatching {
                UpdateSource.entries
                    .find { it.type == s.updateSource.toInt() }
                    ?: UpdateSource.GITHUB
            }
                .getOrDefault(UpdateSource.GITHUB)
        }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            runCatching {
                UpdateSource.entries
                    .find { it.type == initialSettings.updateSource.toInt() }
                    ?: UpdateSource.GITHUB
            }
                .getOrDefault(UpdateSource.GITHUB)
        )

    suspend fun setUpdateSource(source: UpdateSource) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[updateSource] = source.type.toString() }
        }
    }

    // Mirror酱 CDK
    val mirrorChyanCdk: StateFlow<String> = settings
        .map { it.mirrorChyanCdk }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initialSettings.mirrorChyanCdk)

    suspend fun setMirrorChyanCdk(cdk: String) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[mirrorChyanCdk] = cdk }
        }
    }

    // 调试模式
    val debugMode: StateFlow<Boolean> = settings
        .map { it.debugMode.toBooleanStrictOrNull() ?: false }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.debugMode.toBooleanStrictOrNull() ?: false
        )

    suspend fun setDebugMode(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[debugMode] = enabled.toString() }
        }
    }

    // 启动时自动检查更新
    val autoCheckUpdate: StateFlow<Boolean> = settings
        .map { it.autoCheckUpdate.toBooleanStrictOrNull() ?: true }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.autoCheckUpdate.toBooleanStrictOrNull() ?: true
        )

    suspend fun setAutoCheckUpdate(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[autoCheckUpdate] = enabled.toString() }
        }
    }

    // 启动时自动下载更新
    val autoDownloadUpdate: StateFlow<Boolean> = settings
        .map { it.autoDownloadUpdate.toBooleanStrictOrNull() ?: false }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.autoDownloadUpdate.toBooleanStrictOrNull() ?: false
        )

    suspend fun setAutoDownloadUpdate(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[autoDownloadUpdate] = enabled.toString() }
        }
    }

    // IPC服务启动模式
    val startupBackend: StateFlow<RemoteBackend> = settings
        .map {
            runCatching { RemoteBackend.valueOf(it.startupBackend) }
                .getOrDefault(RemoteBackend.SHIZUKU)
        }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            runCatching { RemoteBackend.valueOf(initialSettings.startupBackend) }
                .getOrDefault(RemoteBackend.SHIZUKU)
        )

    suspend fun setStartupBackend(backend: RemoteBackend) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[startupBackend] = backend.name }
        }
    }

    // 跳过 Shizuku 检查
    val skipShizukuCheck: StateFlow<Boolean> = settings
        .map { it.skipShizukuCheck.toBooleanStrictOrNull() ?: false }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.skipShizukuCheck.toBooleanStrictOrNull() ?: false
        )

    suspend fun setSkipShizukuCheck(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[skipShizukuCheck] = enabled.toString() }
        }
    }

    // Shizuku 管理器快捷入口是否启用
    val shizukuShortcutEnabled: StateFlow<Boolean> = settings
        .map { it.shizukuShortcutEnabled.toBooleanStrictOrNull() ?: false }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.shizukuShortcutEnabled.toBooleanStrictOrNull() ?: false
        )

    suspend fun setShizukuShortcutEnabled(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[shizukuShortcutEnabled] = enabled.toString() }
        }
    }

    // Shizuku 管理器入口包名，始终保持为非空包名。
    val shizukuLaunchPackage: StateFlow<String> = settings
        .map { it.shizukuLaunchPackage }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initialSettings.shizukuLaunchPackage)

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
    val muteOnGameLaunch: StateFlow<Boolean> = settings
        .map { it.muteOnGameLaunch.toBooleanStrictOrNull() ?: false }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.muteOnGameLaunch.toBooleanStrictOrNull() ?: false
        )

    suspend fun setMuteOnGameLaunch(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[muteOnGameLaunch] = enabled.toString() }
        }
    }

    val initialMutedGamePackage: String get() = initialSettings.mutedGamePackage

    internal suspend fun setMutedGamePackage(packageName: String) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[mutedGamePackage] = packageName }
        }
    }

    // 任务结束时关闭应用
    val closeAppOnTaskEnd: StateFlow<Boolean> = settings
        .map { it.closeAppOnTaskEnd.toBooleanStrictOrNull() ?: false }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.closeAppOnTaskEnd.toBooleanStrictOrNull() ?: false
        )

    suspend fun setCloseAppOnTaskEnd(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[closeAppOnTaskEnd] = enabled.toString() }
        }
    }

    val deployWithPause: StateFlow<Boolean> = settings
        .map { it.deployWithPause.toBooleanStrictOrNull() ?: false }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.deployWithPause.toBooleanStrictOrNull() ?: false
        )

    suspend fun setDeployWithPause(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[deployWithPause] = enabled.toString() }
        }
    }

    val useHardwareScreenOff: StateFlow<Boolean> = settings
        .map { it.useHardwareScreenOff.toBooleanStrictOrNull() ?: false }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.useHardwareScreenOff.toBooleanStrictOrNull() ?: false
        )

    suspend fun setUseHardwareScreenOff(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[useHardwareScreenOff] = enabled.toString() }
        }
    }

    // 触摸预览
    val showTouchPreview: StateFlow<Boolean> = settings
        .map { it.showTouchPreview.toBooleanStrictOrNull() ?: false }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.showTouchPreview.toBooleanStrictOrNull() ?: false
        )

    suspend fun setShowTouchPreview(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[showTouchPreview] = enabled.toString() }
        }
    }

    // 画中画
    val pipOnHome: StateFlow<Boolean> = settings
        .map { it.pipOnHome.toBooleanStrictOrNull() ?: true }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.pipOnHome.toBooleanStrictOrNull() ?: true
        )

    suspend fun setPipOnHome(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[pipOnHome] = enabled.toString() }
        }
    }

    // 更新渠道
    val updateChannel: StateFlow<UpdateChannel> = settings
        .map {
            runCatching { UpdateChannel.valueOf(it.updateChannel) }
                .getOrDefault(UpdateChannel.STABLE)
        }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            runCatching { UpdateChannel.valueOf(initialSettings.updateChannel) }
                .getOrDefault(UpdateChannel.STABLE)
        )

    suspend fun setUpdateChannel(channel: UpdateChannel) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[updateChannel] = channel.name }
        }
    }

    // 主题模式
    enum class ThemeMode {
        SYSTEM, WHITE, DARK, PURE_DARK
    }

    val themeMode: StateFlow<ThemeMode> = settings
        .map {
            runCatching { ThemeMode.valueOf(it.themeMode) }.getOrDefault(ThemeMode.SYSTEM)
        }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            runCatching {
                val modeStr =
                    if (initialSettings.themeMode == "LIGHT") "WHITE" else initialSettings.themeMode
                ThemeMode.valueOf(modeStr)
            }.getOrDefault(ThemeMode.SYSTEM)
        )

    suspend fun setThemeMode(mode: ThemeMode) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[themeMode] = mode.name }
        }
    }

    // 内部通知级别
    enum class EventNotificationLevel(@param:androidx.annotation.StringRes val labelRes: Int) {
        OFF(R.string.notification_level_off),
        DEFAULT(R.string.notification_level_default),
        HIGH(R.string.notification_level_high),
    }

    val eventNotificationLevel: StateFlow<EventNotificationLevel> = settings
        .map {
            runCatching { EventNotificationLevel.valueOf(it.eventNotificationLevel) }
                .getOrDefault(EventNotificationLevel.DEFAULT)
        }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            runCatching { EventNotificationLevel.valueOf(initialSettings.eventNotificationLevel) }
                .getOrDefault(EventNotificationLevel.DEFAULT)
        )

    suspend fun setEventNotificationLevel(level: EventNotificationLevel) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[eventNotificationLevel] = level.name }
        }
    }

    // 超级岛断网旁路
    val liveIslandXmsfBypass: StateFlow<Boolean> = settings
        .map { it.liveIslandXmsfBypass.toBooleanStrictOrNull() ?: true }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.liveIslandXmsfBypass.toBooleanStrictOrNull() ?: true
        )

    suspend fun setLiveIslandXmsfBypass(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[liveIslandXmsfBypass] = enabled.toString() }
        }
    }

    // 后台虚拟屏分辨率
    val backgroundResolution: StateFlow<DefaultDisplayConfig.ResolutionPreference> = settings
        .map {
            runCatching { DefaultDisplayConfig.ResolutionPreference.valueOf(it.backgroundResolution) }
                .getOrDefault(DefaultDisplayConfig.ResolutionPreference.P720)
        }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            runCatching { DefaultDisplayConfig.ResolutionPreference.valueOf(initialSettings.backgroundResolution) }
                .getOrDefault(DefaultDisplayConfig.ResolutionPreference.P720)
        )

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

    val language: StateFlow<AppLanguage> = settings
        .map {
            runCatching { AppLanguage.valueOf(it.language) }
                .getOrDefault(AppLanguage.SYSTEM)
        }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            runCatching { AppLanguage.valueOf(initialSettings.language) }
                .getOrDefault(AppLanguage.SYSTEM)
        )

    suspend fun setLanguage(lang: AppLanguage) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[language] = lang.name }
        }
        achievementRepository.report {
            event = AchievementEvents.LANGUAGE_CHANGED
        }
    }

    // 待展示的更新公告
    val pendingChangelogVersion: StateFlow<String> = settings
        .map { it.pendingChangelogVersion }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initialSettings.pendingChangelogVersion)

    val pendingChangelogContent: StateFlow<String> = settings
        .map { it.pendingChangelogContent }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initialSettings.pendingChangelogContent)

    suspend fun savePendingChangelog(version: String, content: String) {
        with(AppSettingsSchema) {
            context.dataStore.edit {
                it[pendingChangelogVersion] = version.removePrefix("v")
                it[pendingChangelogContent] = content
            }
        }
    }

    val currentChangelogVersion: StateFlow<String> = settings
        .map { it.currentChangelogVersion }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initialSettings.currentChangelogVersion)

    val currentChangelogContent: StateFlow<String> = settings
        .map { it.currentChangelogContent }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initialSettings.currentChangelogContent)

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
    val forceFullscreenOnVirtualDisplay: StateFlow<Boolean> = settings
        .map { it.forceFullscreenOnVirtualDisplay.toBooleanStrictOrNull() ?: false }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.forceFullscreenOnVirtualDisplay.toBooleanStrictOrNull() ?: false
        )

    suspend fun setForceFullscreenOnVirtualDisplay(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[forceFullscreenOnVirtualDisplay] = enabled.toString() }
        }
    }

    // Android 任务配置覆盖开关
    val tasksOverrideEnabled: StateFlow<Boolean> = settings
        .map { it.tasksOverrideEnabled.toBooleanStrictOrNull() ?: false }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.tasksOverrideEnabled.toBooleanStrictOrNull() ?: false
        )

    suspend fun setTasksOverrideEnabled(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[tasksOverrideEnabled] = enabled.toString() }
        }
    }

    val announcementReadHash: StateFlow<String> = settings
        .map { it.announcementReadHash }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initialSettings.announcementReadHash)

    suspend fun setAnnouncementReadHash(hash: String) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[announcementReadHash] = hash }
        }
    }

    private fun parseNeedsOnboarding(raw: String): Boolean =
        (raw.toIntOrNull() ?: 0) < ONBOARDING_VERSION

    val needsOnboarding: StateFlow<Boolean> = settings
        .map { parseNeedsOnboarding(it.onboardingSeenVersion) }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            parseNeedsOnboarding(initialSettings.onboardingSeenVersion)
        )

    suspend fun markOnboardingSeen() {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[onboardingSeenVersion] = ONBOARDING_VERSION.toString() }
        }
    }

    // 是否启用系统莫奈主题色（Android 12+ Material You）
    private fun parseUseSystemMonetColor(raw: String): Boolean =
        raw.toBooleanStrictOrNull() ?: true

    val useSystemMonetColor: StateFlow<Boolean> = settings
        .map { parseUseSystemMonetColor(it.useSystemMonetColor) }
        .distinctUntilChanged()
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            parseUseSystemMonetColor(initialSettings.useSystemMonetColor)
        )

    suspend fun setUseSystemMonetColor(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[useSystemMonetColor] = enabled.toString() }
        }
    }

    // 页面缩放（0=自动，或 80~110 手动）
    val fontSizeScale: StateFlow<Int> = settings
        .map { parseFontSizeScale(it.fontSizeScale) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, parseFontSizeScale(initialSettings.fontSizeScale))

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
    val showAchievementSnackbar: StateFlow<Boolean> = settings
        .map { it.showAchievementSnackbar.toBooleanStrictOrNull() ?: true }
        .distinctUntilChanged()
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            initialSettings.showAchievementSnackbar.toBooleanStrictOrNull() ?: true
        )

    suspend fun setShowAchievementSnackbar(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[showAchievementSnackbar] = enabled.toString() }
        }
    }

    // ============ 自定义图片背景（仅四个主 Tab 生效）============

    /** 将 0~100 的原始字符串解析为合法百分比 */
    private fun parsePercent(raw: String, default: Int): Int =
        raw.toIntOrNull()?.coerceIn(0, 100) ?: default

    val customBackgroundEnabled: StateFlow<Boolean> = settings
        .map { it.customBackgroundEnabled.toBooleanStrictOrNull() ?: false }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.customBackgroundEnabled.toBooleanStrictOrNull() ?: false
        )

    suspend fun setCustomBackgroundEnabled(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[customBackgroundEnabled] = enabled.toString() }
        }
    }

    val customBackgroundToken: StateFlow<String> = settings
        .map { it.customBackgroundToken }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initialSettings.customBackgroundToken)

    /** 保存/清除背景时开关与令牌总是成对变更，合并为一次写入避免中间态。 */
    suspend fun setCustomBackgroundState(enabled: Boolean, token: String) {
        with(AppSettingsSchema) {
            context.dataStore.edit {
                it[customBackgroundEnabled] = enabled.toString()
                it[customBackgroundToken] = token
            }
        }
    }

    val customBackgroundImageAlpha: StateFlow<Int> = settings
        .map { parsePercent(it.customBackgroundImageAlpha, 80) }
        .distinctUntilChanged()
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            parsePercent(initialSettings.customBackgroundImageAlpha, 80)
        )

    suspend fun setCustomBackgroundImageAlpha(value: Int) {
        with(AppSettingsSchema) {
            context.dataStore.edit {
                it[customBackgroundImageAlpha] = value.coerceIn(0, 100).toString()
            }
        }
    }

    val customBackgroundScrim: StateFlow<Int> = settings
        .map { parsePercent(it.customBackgroundScrim, 25) }
        .distinctUntilChanged()
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            parsePercent(initialSettings.customBackgroundScrim, 25)
        )

    suspend fun setCustomBackgroundScrim(value: Int) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[customBackgroundScrim] = value.coerceIn(0, 100).toString() }
        }
    }

    val customBackgroundBlur: StateFlow<Int> = settings
        .map { parsePercent(it.customBackgroundBlur, 0) }
        .distinctUntilChanged()
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            parsePercent(initialSettings.customBackgroundBlur, 0)
        )

    suspend fun setCustomBackgroundBlur(value: Int) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[customBackgroundBlur] = value.coerceIn(0, 100).toString() }
        }
    }

    // ───────────────── 唤醒 + 解锁 ─────────────────

    val wakeUnlockType: StateFlow<String> = settings
        .map {
            val t = it.wakeUnlockType
            if (t in WAKE_UNLOCK_TYPES) t else "swipe"
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, "swipe")

    suspend fun setWakeUnlockType(type: String) {
        if (type !in WAKE_UNLOCK_TYPES) return
        with(AppSettingsSchema) {
            context.dataStore.edit { it[wakeUnlockType] = type }
        }
    }

    val wakeCredential: StateFlow<String> = settings
        .map { it.wakeCredential }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initialSettings.wakeCredential)

    suspend fun setWakeCredential(credential: String) {
        // 注入走 KEYCODE_0..9，仅保留数字
        val digits = credential.filter { it.isDigit() }.take(MAX_PIN_LENGTH)
        with(AppSettingsSchema) {
            context.dataStore.edit { it[wakeCredential] = digits }
        }
    }

    val reportToPenguin: StateFlow<Boolean> = settings
        .map { it.reportToPenguin.toBooleanStrictOrNull() ?: true }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.reportToPenguin.toBooleanStrictOrNull() ?: true,
        )

    suspend fun setReportToPenguin(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[reportToPenguin] = enabled.toString() }
        }
    }

    val reportToYituliu: StateFlow<Boolean> = settings
        .map { it.reportToYituliu.toBooleanStrictOrNull() ?: true }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.reportToYituliu.toBooleanStrictOrNull() ?: true,
        )

    suspend fun setReportToYituliu(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[reportToYituliu] = enabled.toString() }
        }
    }

    val penguinId: StateFlow<String> = settings
        .map { it.penguinId }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initialSettings.penguinId)

    suspend fun setPenguinId(id: String) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[penguinId] = id.trim() }
        }
    }

}
