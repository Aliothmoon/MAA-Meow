package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.notification.NotificationSettings
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.notification.LiveCapability
import com.aliothmoon.maameow.domain.notification.LiveSessionCoordinator
import com.aliothmoon.maameow.domain.notification.LiveUpdatePublisher
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.ExternalNotificationService
import com.aliothmoon.maameow.manager.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationSettingsViewModel(
    private val settingsManager: NotificationSettingsManager,
    private val notificationService: ExternalNotificationService,
    private val achievementReporter: AchievementReporter,
    private val livePublisher: LiveUpdatePublisher,
    private val liveCoordinator: LiveSessionCoordinator,
    private val permissionManager: PermissionManager,
    private val appSettingsManager: AppSettingsManager,
) : ViewModel() {

    companion object {
        val ALL_PROVIDER_IDS = setOf(
            "ServerChan",
            "Telegram",
            "Discord",
            "DingTalk",
            "KOOK",
            "Discord Webhook",
            "SMTP",
            "Bark",
            "Qmsg",
            "Gotify",
            "CustomWebhook",
        )
    }

    val settings: StateFlow<NotificationSettings> = settingsManager.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationSettings())

    val enabledProviders: StateFlow<Set<String>> = settingsManager.enabledProviderIds
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val sendOnComplete: StateFlow<Boolean> = settingsManager.sendOnComplete
    val sendOnError: StateFlow<Boolean> = settingsManager.sendOnError
    val sendOnServiceDied: StateFlow<Boolean> = settingsManager.sendOnServiceDied
    val includeLogDetails: StateFlow<Boolean> = settingsManager.includeLogDetails

    private val _liveCapability = MutableStateFlow(livePublisher.capability)
    val liveCapability: StateFlow<LiveCapability> = _liveCapability.asStateFlow()

    val liveIslandXmsfBypass: StateFlow<Boolean> = appSettingsManager.liveIslandXmsfBypass

    init {
        // 旁路开关会改变后端选择，展示方式得跟着落盘值走，不能等下次 onResume
        viewModelScope.launch {
            appSettingsManager.liveIslandXmsfBypass.drop(1).collect {
                _liveCapability.value = livePublisher.capability
            }
        }
    }

    fun refreshLiveCapability() {
        permissionManager.refresh()
        _liveCapability.value = livePublisher.refreshCapability()
    }

    fun setLiveIslandXmsfBypass(enabled: Boolean) {
        viewModelScope.launch { appSettingsManager.setLiveIslandXmsfBypass(enabled) }
    }

    fun requestPostNotifications(context: Context) {
        viewModelScope.launch {
            permissionManager.requestNotification(context)
            refreshLiveCapability()
        }
    }

    fun openAppNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openPromotedSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= 36) {
            val promoted = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launched = runCatching { context.startActivity(promoted) }.isSuccess
            if (launched) return
        }
        openAppNotificationSettings(context)
    }

    fun sendLiveTest(title: String, content: String) {
        liveCoordinator.publishTest(title, content)
    }

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
            achievementReporter.reportNotificationProviders(providers, ALL_PROVIDER_IDS)
        }
    }

    fun sendTest(title: String, content: String) {
        notificationService.sendTest(title, content)
    }
}
