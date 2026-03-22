package com.aliothmoon.maameow.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.utils.Misc
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val app: Application,
    private val appSettingsManager: AppSettingsManager,
    private val permissionManager: PermissionManager,
) : ViewModel() {

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

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

    val updateChannel: StateFlow<UpdateChannel> = appSettingsManager.updateChannel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UpdateChannel.STABLE)

    fun setUpdateChannel(channel: UpdateChannel) {
        viewModelScope.launch {
            appSettingsManager.setUpdateChannel(channel)
        }
    }

    val themeMode: StateFlow<AppSettingsManager.ThemeMode> = appSettingsManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettingsManager.ThemeMode.WHITE)

    fun setThemeMode(mode: AppSettingsManager.ThemeMode) {
        viewModelScope.launch {
            appSettingsManager.setThemeMode(mode)
        }
    }
}
