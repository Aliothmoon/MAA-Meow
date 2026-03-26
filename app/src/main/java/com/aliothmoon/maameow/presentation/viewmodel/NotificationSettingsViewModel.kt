package com.aliothmoon.maameow.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.notification.NotificationSettings
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.domain.service.ExternalNotificationService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationSettingsViewModel(
    private val settingsManager: NotificationSettingsManager,
    private val notificationService: ExternalNotificationService,
) : ViewModel() {

    val settings: StateFlow<NotificationSettings> = settingsManager.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationSettings())

    val enabledProviders: StateFlow<Set<String>> = settingsManager.enabledProviderIds
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val sendOnComplete: StateFlow<Boolean> = settingsManager.sendOnComplete
    val sendOnError: StateFlow<Boolean> = settingsManager.sendOnError
    val sendOnServiceDied: StateFlow<Boolean> = settingsManager.sendOnServiceDied
    val includeLogDetails: StateFlow<Boolean> = settingsManager.includeLogDetails

    fun updateSettings(transform: NotificationSettings.() -> NotificationSettings) {
        viewModelScope.launch {
            val current = settings.value
            settingsManager.updateSettings(current.transform())
        }
    }

    fun toggleProvider(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            val providers = current.enabledProviders
                .split(",")
                .filter { it.isNotBlank() }
                .toMutableSet()
            if (enabled) providers.add(id) else providers.remove(id)
            settingsManager.updateSettings(current.copy(enabledProviders = providers.joinToString(",")))
        }
    }

    fun sendTest() {
        notificationService.sendTest()
    }
}
