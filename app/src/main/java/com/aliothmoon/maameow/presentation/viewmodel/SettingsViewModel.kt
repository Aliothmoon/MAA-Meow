package com.aliothmoon.maameow.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.utils.Misc
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val app: Application,
    private val appSettingsManager: AppSettingsManager,
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
}