package com.aliothmoon.maameow.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.serializer

/** 一图流 Token：开关决定用全局那份还是按配置那份；后者不进备份，复制配置不带 */
class YituliuTokenRepository(
    store: DataStore<Preferences>,
    private val taskChainState: TaskChainState,
    private val appSettings: AppSettingsManager,
) {
    private val shards = ProfileShardStore(
        store = store,
        taskChainState = taskChainState,
        keyPrefix = KEY_PREFIX,
        serializer = String.serializer(),
        empty = { "" },
    )

    data class Resolved(val token: String, val perProfile: Boolean)

    val perProfile: StateFlow<Boolean> get() = appSettings.yituliuTokenPerProfile

    val sharedToken: StateFlow<String> get() = appSettings.yituliuOpenApiToken

    val profileTokens: StateFlow<Map<String, String>> get() = shards.all

    fun start() = shards.start()

    /** 冷启动时等装载完 */
    suspend fun resolve(profileId: String): Resolved {
        appSettings.awaitLoaded()
        if (!perProfile.value) return Resolved(sharedToken.value, perProfile = false)
        shards.isLoaded.first { it }
        return Resolved(profileTokens.value[profileId].orEmpty(), perProfile = true)
    }

    suspend fun setShared(token: String) = appSettings.setYituliuOpenApiToken(token)

    fun setForProfile(profileId: String, token: String) {
        shards.mutate(profileId) { token.trim() }
    }

    /** 开启时只给当前配置带上全局 Token，其余留空，免得多账号串号 */
    suspend fun setPerProfile(enabled: Boolean) {
        if (enabled) {
            shards.isLoaded.first { it }
            val active = taskChainState.profileId.value
            val shared = sharedToken.value
            if (active.isNotEmpty() && shared.isNotEmpty() && profileTokens.value[active].isNullOrEmpty()) {
                setForProfile(active, shared)
            }
        }
        appSettings.setYituliuTokenPerProfile(enabled)
    }

    companion object {
        private const val KEY_PREFIX = "token_"

        private val Context.yituliuTokenStore: DataStore<Preferences> by preferencesDataStore(
            name = "yituliu_token",
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        )

        fun create(context: Context, taskChainState: TaskChainState, appSettings: AppSettingsManager) =
            YituliuTokenRepository(context.yituliuTokenStore, taskChainState, appSettings)
    }
}
