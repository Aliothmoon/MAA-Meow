package com.aliothmoon.maameow.presentation.viewmodel

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.ConfigBackupManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.utils.Misc
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.resolveSelectedLanguage
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.toLocaleList
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
) : ViewModel() {

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

    val deploymentWithPause: StateFlow<Boolean> = appSettingsManager.deploymentWithPause
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setDeploymentWithPause(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setDeploymentWithPause(enabled)
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
}
