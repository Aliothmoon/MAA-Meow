package com.aliothmoon.maameow.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
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

    // 远程服务启动模式
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

    // 任务结束后关闭应用
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

}
