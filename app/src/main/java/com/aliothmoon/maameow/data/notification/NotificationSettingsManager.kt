package com.aliothmoon.maameow.data.notification

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
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

class NotificationSettingsManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(name = "notification_settings")
    }

    val settings: Flow<NotificationSettings> =
        with(NotificationSettingsSchema) { context.notificationDataStore.flow }

    private val initialSettings: NotificationSettings = runBlocking { settings.first() }

    suspend fun updateSettings(new: NotificationSettings) {
        with(NotificationSettingsSchema) { context.notificationDataStore.update(new) }
    }

    val sendOnComplete: StateFlow<Boolean> = settings
        .map { it.sendOnComplete.toBooleanStrictOrNull() ?: true }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.sendOnComplete.toBooleanStrictOrNull() ?: true
        )

    val sendOnError: StateFlow<Boolean> = settings
        .map { it.sendOnError.toBooleanStrictOrNull() ?: true }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.sendOnError.toBooleanStrictOrNull() ?: true
        )

    val sendOnServiceDied: StateFlow<Boolean> = settings
        .map { it.sendOnServiceDied.toBooleanStrictOrNull() ?: false }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.sendOnServiceDied.toBooleanStrictOrNull() ?: false
        )

    val includeLogDetails: StateFlow<Boolean> = settings
        .map { it.includeLogDetails.toBooleanStrictOrNull() ?: false }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.includeLogDetails.toBooleanStrictOrNull() ?: false
        )

    val enabledProviderIds: StateFlow<List<String>> = settings
        .map { it.enabledProviders.split(",").filter { id -> id.isNotEmpty() } }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            initialSettings.enabledProviders.split(",").filter { it.isNotEmpty() }
        )
}
