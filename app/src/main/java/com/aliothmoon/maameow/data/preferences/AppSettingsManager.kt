package com.aliothmoon.maameow.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.model.update.UpdateSource
import com.aliothmoon.maameow.domain.models.AppSettings
import com.aliothmoon.maameow.domain.models.AppSettingsSchema
import com.aliothmoon.maameow.domain.models.OverlayControlMode
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.models.RunMode
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


class AppSettingsManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")
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

    // 自动战斗干员部署「按住-暂停」(SWIPE_WITH_PAUSE)
    val deploymentWithPause: StateFlow<Boolean> = settings
        .map { it.deploymentWithPause.toBooleanStrictOrNull() ?: true }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.deploymentWithPause.toBooleanStrictOrNull() ?: true
        )

    suspend fun setDeploymentWithPause(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[deploymentWithPause] = enabled.toString() }
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
        WHITE, DARK, PURE_DARK
    }

    val themeMode: StateFlow<ThemeMode> = settings
        .map {
            val modeStr = it.themeMode
            if (modeStr == "SYSTEM" || modeStr == "LIGHT") {
                ThemeMode.WHITE
            } else {
                runCatching { ThemeMode.valueOf(modeStr) }
                    .getOrDefault(ThemeMode.WHITE)
            }
        }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            runCatching { 
                val initialModeStr = initialSettings.themeMode
                if (initialModeStr == "SYSTEM" || initialModeStr == "LIGHT") {
                    ThemeMode.WHITE
                } else {
                    ThemeMode.valueOf(initialModeStr)
                }
            }
                .getOrDefault(ThemeMode.WHITE)
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
                it[pendingChangelogVersion] = version
                it[pendingChangelogContent] = content
            }
        }
    }

    suspend fun clearPendingChangelog() {
        with(AppSettingsSchema) {
            context.dataStore.edit {
                it[pendingChangelogVersion] = ""
                it[pendingChangelogContent] = ""
            }
        }
    }

}
