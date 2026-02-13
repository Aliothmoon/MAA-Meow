package com.aliothmoon.maameow.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.aliothmoon.maameow.data.model.update.UpdateSource
import com.aliothmoon.maameow.domain.models.AppSettings
import com.aliothmoon.maameow.domain.models.AppSettingsSchema
import com.aliothmoon.maameow.domain.models.OverlayControlMode
import com.aliothmoon.maameow.domain.models.RunMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn


class AppSettingsManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")
    }

    val settings: Flow<AppSettings> = with(AppSettingsSchema) { context.dataStore.flow }

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
        .stateIn(scope, SharingStarted.Eagerly, OverlayControlMode.ACCESSIBILITY)

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
        .stateIn(scope, SharingStarted.Eagerly, RunMode.BACKGROUND)

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
        .stateIn(scope, SharingStarted.Eagerly, UpdateSource.GITHUB)

    suspend fun setUpdateSource(source: UpdateSource) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[updateSource] = source.type.toString() }
        }
    }

    // Mirror酱 CDK
    val mirrorChyanCdk: StateFlow<String> = settings
        .map { it.mirrorChyanCdk }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, "")

    suspend fun setMirrorChyanCdk(cdk: String) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[mirrorChyanCdk] = cdk }
        }
    }

    // 调试模式
    val debugMode: StateFlow<Boolean> = settings
        .map { it.debugMode.toBooleanStrictOrNull() ?: false }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)

    suspend fun setDebugMode(enabled: Boolean) {
        with(AppSettingsSchema) {
            context.dataStore.edit { it[debugMode] = enabled.toString() }
        }
    }
}
