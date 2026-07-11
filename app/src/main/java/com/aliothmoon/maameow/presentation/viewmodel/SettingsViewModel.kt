package com.aliothmoon.maameow.presentation.viewmodel

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.constant.OFFICIAL_SHIZUKU_PACKAGE
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.ConfigBackupManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.MaaResourceLoader
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.utils.Misc
import com.aliothmoon.maameow.utils.deleteManagedCustomWallpaper
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.resolveSelectedLanguage
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.toLocaleList
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream

class SettingsViewModel(
    private val app: Application,
    private val appSettingsManager: AppSettingsManager,
    private val permissionManager: PermissionManager,
    private val configBackupManager: ConfigBackupManager,
    private val taskChainState: TaskChainState,
    private val resourceDataManager: ResourceDataManager,
    private val resourceLoader: MaaResourceLoader,
    private val achievementReporter: AchievementReporter,
) : ViewModel() {
    private val wallpaperMutex = Mutex()

    // ========== 导入导出 ==========

    private val _backupMessage = MutableStateFlow<UiText?>(null)
    val backupMessage: StateFlow<UiText?> = _backupMessage.asStateFlow()

    private val _showRestartDialog = MutableStateFlow(false)
    val showRestartDialog: StateFlow<Boolean> = _showRestartDialog.asStateFlow()

    fun clearBackupMessage() {
        _backupMessage.value = null
    }

    fun dismissRestartDialog() {
        _showRestartDialog.value = false
    }

    fun confirmRestart() {
        _showRestartDialog.value = false
        Misc.restartApp(app)
    }

    fun exportConfig(outputStream: OutputStream) {
        viewModelScope.launch {
            try {
                configBackupManager.exportTo(outputStream)
                _backupMessage.value = uiTextOf(R.string.settings_export_success)
            } catch (e: Exception) {
                Timber.e(e, "export config failed")
                _backupMessage.value = uiTextOf(R.string.settings_export_failed, e.message.orEmpty())
            }
        }
    }

    fun importConfig(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                configBackupManager.importFrom(inputStream)
                _showRestartDialog.value = true
            } catch (e: Exception) {
                Timber.e(e, "import config failed")
                _backupMessage.value = uiTextOf(R.string.settings_import_failed, e.message.orEmpty())
            }
        }
    }

    // ========== 现有设置 ==========

    val debugMode: StateFlow<Boolean> = appSettingsManager.debugMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setDebugMode(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setDebugMode(enabled)
            achievementReporter.reportDebugModeChanged(enabled)
            val state = RemoteServiceManager.state.value
            if (state is RemoteServiceManager.ServiceState.Connected) {
                RemoteServiceManager.unbind()
            }
            if (enabled) {
                Misc.restartApp(app)
            }
        }
    }

    val autoCheckUpdate: StateFlow<Boolean> = appSettingsManager.autoCheckUpdate
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            !BuildConfig.DEBUG
        )

    fun setAutoCheckUpdate(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setAutoCheckUpdate(enabled)
        }
    }

    val autoDownloadUpdate: StateFlow<Boolean> = appSettingsManager.autoDownloadUpdate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setAutoDownloadUpdate(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setAutoDownloadUpdate(enabled)
        }
    }

    val startupBackend: StateFlow<RemoteBackend> = appSettingsManager.startupBackend
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RemoteBackend.SHIZUKU)

    fun setStartupBackend(backend: RemoteBackend) {
        viewModelScope.launch {
            permissionManager.setStartupBackend(backend)
        }
    }

    val skipShizukuCheck: StateFlow<Boolean> = appSettingsManager.skipShizukuCheck
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setSkipShizukuCheck(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setSkipShizukuCheck(enabled)
        }
    }

    val shizukuLaunchPackage: StateFlow<String> = appSettingsManager.shizukuLaunchPackage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OFFICIAL_SHIZUKU_PACKAGE)

    val shizukuShortcutEnabled: StateFlow<Boolean> = appSettingsManager.shizukuShortcutEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setShizukuShortcutEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setShizukuShortcutEnabled(enabled)
        }
    }

    fun setShizukuLaunchPackage(packageName: String) {
        viewModelScope.launch {
            appSettingsManager.setShizukuLaunchPackage(packageName)
        }
    }

    val deploymentWithPause: StateFlow<Boolean> = appSettingsManager.deploymentWithPause
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setDeploymentWithPause(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setDeploymentWithPause(enabled)
        }
    }

    val forceFullscreenOnVirtualDisplay: StateFlow<Boolean> = appSettingsManager.forceFullscreenOnVirtualDisplay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setForceFullscreenOnVirtualDisplay(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setForceFullscreenOnVirtualDisplay(enabled)
        }
    }

    val allowForegroundScheduledTask: StateFlow<Boolean> = appSettingsManager.allowForegroundScheduledTask

    fun setAllowForegroundScheduledTask(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setAllowForegroundScheduledTask(enabled)
        }
    }

    val runScheduleWhenLocked: StateFlow<Boolean> = appSettingsManager.runScheduleWhenLocked

    fun setRunScheduleWhenLocked(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setRunScheduleWhenLocked(enabled)
        }
    }

    val updateChannel: StateFlow<UpdateChannel> = appSettingsManager.updateChannel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UpdateChannel.STABLE)

    fun setUpdateChannel(channel: UpdateChannel) {
        viewModelScope.launch {
            appSettingsManager.setUpdateChannel(channel)
        }
    }

    val themeMode: StateFlow<AppSettingsManager.ThemeMode> = appSettingsManager.themeMode
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppSettingsManager.ThemeMode.WHITE
        )

    fun setThemeMode(mode: AppSettingsManager.ThemeMode) {
        viewModelScope.launch {
            appSettingsManager.setThemeMode(mode)
        }
    }

    val backgroundResolution: StateFlow<DefaultDisplayConfig.ResolutionPreference> =
        appSettingsManager.backgroundResolution
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                DefaultDisplayConfig.ResolutionPreference.P720
            )

    fun setBackgroundResolution(pref: DefaultDisplayConfig.ResolutionPreference) {
        viewModelScope.launch {
            appSettingsManager.setBackgroundResolution(pref)
        }
    }

    val language: StateFlow<AppSettingsManager.AppLanguage> = appSettingsManager.language
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppSettingsManager.AppLanguage.SYSTEM
        )

    fun setLanguage(lang: AppSettingsManager.AppLanguage) {
        viewModelScope.launch {
            val resolved = resolveSelectedLanguage(lang)
            appSettingsManager.setLanguage(resolved)
            AppCompatDelegate.setApplicationLocales(resolved.toLocaleList())
            resourceDataManager.refreshDisplayLanguage(
                clientType = taskChainState.getClientType(),
                displayLanguage = ResourceDataManager.displayLanguageCode(resolved)
            )
        }
    }

    // Android 特化任务覆盖
    val tasksOverrideEnabled: StateFlow<Boolean> = appSettingsManager.tasksOverrideEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setTasksOverrideEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setTasksOverrideEnabled(enabled)
            // 开关变更后重置加载状态，下次任务启动时按最新配置重新加载
            resourceLoader.reset()
        }
    }

    // ============ System Monet theme color ============
    val useSystemMonetColor: StateFlow<Boolean> = appSettingsManager.useSystemMonetColor
    fun setUseSystemMonetColor(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setUseSystemMonetColor(enabled)
        }
    }

    val customWallpaperPath: StateFlow<String> = appSettingsManager.customWallpaperPath
    suspend fun setCustomWallpaperPath(path: String): Boolean = wallpaperMutex.withLock {
        val oldPath = appSettingsManager.customWallpaperPath.value
        var pathCommitted = false
        try {
            appSettingsManager.setCustomWallpaperPath(path)
            pathCommitted = true
            deleteManagedCustomWallpaper(app, oldPath, except = path)
            if (oldPath.isBlank()) {
                if (appSettingsManager.cardOpacity.value == AppSettingsManager.CARD_OPACITY_DEFAULT) {
                    runCatching {
                        appSettingsManager.setCardOpacity(AppSettingsManager.CARD_OPACITY_WALLPAPER_DEFAULT)
                    }.onFailure { Timber.w(it, "Failed to set wallpaper card opacity") }
                }
                if (!appSettingsManager.useSystemMonetColor.value) {
                    runCatching { appSettingsManager.setUseSystemMonetColor(true) }
                        .onFailure { Timber.w(it, "Failed to enable wallpaper color extraction") }
                }
            }
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.e(error, "Failed to persist custom wallpaper")
            if (!pathCommitted) deleteManagedCustomWallpaper(app, path, except = oldPath)
            false
        }
    }

    fun clearCustomWallpaper() {
        viewModelScope.launch {
            wallpaperMutex.withLock {
                val oldPath = appSettingsManager.customWallpaperPath.value
                try {
                    appSettingsManager.setCustomWallpaperPath("")
                    deleteManagedCustomWallpaper(app, oldPath)
                    if (appSettingsManager.cardOpacity.value == AppSettingsManager.CARD_OPACITY_WALLPAPER_DEFAULT) {
                        appSettingsManager.setCardOpacity(AppSettingsManager.CARD_OPACITY_DEFAULT)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Timber.e(error, "Failed to clear custom wallpaper")
                }
            }
        }
    }

    val cardOpacity: StateFlow<Int> = appSettingsManager.cardOpacity
    fun setCardOpacity(opacity: Int) {
        viewModelScope.launch {
            appSettingsManager.setCardOpacity(opacity)
        }
    }

    // ============ Font Size Scale ============
    val fontSizeScale: StateFlow<Int> = appSettingsManager.fontSizeScale
    fun setFontSizeScale(scale: Int) {
        viewModelScope.launch {
            appSettingsManager.setFontSizeScale(scale)
        }
    }
    // 成就 Snackbar 提示开关
    val showAchievementSnackbar: StateFlow<Boolean> = appSettingsManager.showAchievementSnackbar
    fun setShowAchievementSnackbar(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setShowAchievementSnackbar(enabled)
        }
    }
}
