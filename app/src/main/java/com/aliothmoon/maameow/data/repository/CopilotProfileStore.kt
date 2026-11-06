package com.aliothmoon.maameow.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.model.CopilotConfig
import com.aliothmoon.maameow.data.model.copilot.CopilotListItem
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.io.File

/** 同一 Profile 的配置与列表；作业 JSON 通过路径共享。 */
@Serializable
data class CopilotProfileData(
    val config: CopilotConfig = CopilotConfig(),
    val taskList: List<CopilotListItem> = emptyList(),
)

class CopilotProfileStore(
    private val store: DataStore<Preferences>,
    private val taskChainState: TaskChainState,
    private val legacyDir: File,
) {
    private val shards = ProfileShardStore(
        store = store,
        taskChainState = taskChainState,
        keyPrefix = KEY_PREFIX,
        serializer = CopilotProfileData.serializer(),
        empty = ::CopilotProfileData,
        prepare = ::migrateLegacy,
    )

    val isLoaded: StateFlow<Boolean> get() = shards.isLoaded

    init {
        shards.start()
        taskChainState.registerProfileDuplicator { source, target ->
            awaitLoaded()
            val data = shards.forProfile(source)
            shards.mutate(target) { data }
            shards.awaitPersist()
        }
    }

    suspend fun awaitLoaded() {
        taskChainState.isLoaded.first { it }
        isLoaded.first { it }
        shards.loadError?.let { throw it }
    }

    fun forProfile(profileId: String): CopilotProfileData = shards.forProfile(profileId)

    /** 同步更新内存，排队落盘。 */
    fun update(profileId: String, transform: (CopilotProfileData) -> CopilotProfileData) {
        if (!isLoaded.value || shards.loadError != null || !taskChainState.isLoaded.value ||
            taskChainState.profiles.value.none { it.id == profileId }
        ) return
        shards.mutate(profileId, transform)
    }

    private suspend fun migrateLegacy() {
        taskChainState.isLoaded.first { it }
        val profiles = taskChainState.profiles.value
        if (profiles.isEmpty()) return
        if (store.data.first()[MIGRATED] == true) return
        // 保留旧文件用于恢复；IO 失败时下次启动重试。
        val configFile = File(legacyDir, "config.json")
        val listFile = File(legacyDir, "task_list.json")
        val config = readLegacy(configFile) { JsonUtils.common.decodeFromString<CopilotConfig>(it) }
            ?: CopilotConfig()
        val taskList = readLegacy(listFile) { JsonUtils.common.decodeFromString<List<CopilotListItem>>(it) }
            ?: emptyList()
        val legacy = CopilotProfileData(config, taskList)
        store.edit { prefs ->
            if (prefs[MIGRATED] == true) return@edit
            profiles.forEach { profile ->
                val key = stringPreferencesKey("$KEY_PREFIX${profile.id}")
                val valid = prefs[key]?.let { raw ->
                    runCatching { JsonUtils.common.decodeFromString<CopilotProfileData>(raw) }.isSuccess
                } == true
                if (!valid) prefs[key] = JsonUtils.common.encodeToString(legacy)
            }
            // 标记与初始分片一同提交，保证迁移原子性。
            prefs[MIGRATED] = true
        }
    }

    private fun <T> readLegacy(file: File, decode: (String) -> T): T? {
        if (!file.exists()) return null
        val raw = file.readText(Charsets.UTF_8)
        if (raw.isBlank()) return null
        return runCatching { decode(raw) }.onFailure {
            Timber.w(it, "自动战斗旧配置损坏，保留原文件: %s", file.name)
        }.getOrNull()
    }

    companion object {
        internal const val KEY_PREFIX = "copilot_profile_"
        internal val MIGRATED = booleanPreferencesKey("copilot_profiles_migrated")

        private val Context.copilotStore: DataStore<Preferences> by preferencesDataStore(
            name = "copilot",
        )

        fun create(context: Context, taskChainState: TaskChainState, pathConfig: MaaPathConfig) =
            CopilotProfileStore(context.copilotStore, taskChainState, File(pathConfig.rootDir, "copilot"))
    }
}
