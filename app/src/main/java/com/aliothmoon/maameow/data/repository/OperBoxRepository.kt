package com.aliothmoon.maameow.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.io.IOException

/**
 * 单个配置档的干员箱快照。
 *
 * @param owned 已拥有干员
 * @param notOwned 未拥有干员（识别完成时相对全图差集）
 * @param syncTimeMillis 上次完整干员识别时间；0 表示从未识别
 */
@Serializable
data class OperBoxSnapshot(
    val owned: List<OperBoxOperator> = emptyList(),
    val notOwned: List<OperBoxOperator> = emptyList(),
    val syncTimeMillis: Long = 0L,
) {
    val hasSynced: Boolean get() = syncTimeMillis > 0L
}

/**
 * 干员识别结果持久化，按配置档分片，与 [DepotRepository] 对称。
 */
class OperBoxRepository(
    private val store: DataStore<Preferences>,
    private val taskChainState: TaskChainState,
) {
    private val json = JsonUtils.common
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val snapshot: StateFlow<OperBoxSnapshot> =
        combine(taskChainState.activeProfileId, store.data) { profileId, prefs ->
            if (profileId.isEmpty()) OperBoxSnapshot() else decode(prefs[keyOf(profileId)])
        }
            .catch { e ->
                if (e is IOException) {
                    Timber.e(e, "读取干员箱数据失败")
                    emit(OperBoxSnapshot())
                } else {
                    throw e
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, OperBoxSnapshot())

    fun start() {
        scope.launch {
            taskChainState.profileDeleted.collect { dropProfile(it) }
        }
    }

    suspend fun replaceAll(owned: List<OperBoxOperator>, notOwned: List<OperBoxOperator>) {
        editSnapshot {
            OperBoxSnapshot(
                owned = owned,
                notOwned = notOwned,
                syncTimeMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun dropProfile(profileId: String) {
        if (profileId.isEmpty()) return
        try {
            store.edit { it.remove(keyOf(profileId)) }
        } catch (e: IOException) {
            Timber.w(e, "删除干员箱分片失败: %s", profileId)
        }
    }

    private suspend fun editSnapshot(transform: (OperBoxSnapshot) -> OperBoxSnapshot) {
        taskChainState.isLoaded.first { it }
        val profileId = taskChainState.activeProfileId.value
        if (profileId.isEmpty()) {
            Timber.w("活跃配置档为空，跳过干员箱写入")
            return
        }
        try {
            store.edit { prefs ->
                val key = keyOf(profileId)
                prefs[key] = json.encodeToString(transform(decode(prefs[key])))
            }
        } catch (e: IOException) {
            Timber.e(e, "写入干员箱分片失败: %s", profileId)
        }
    }

    private fun decode(raw: String?): OperBoxSnapshot {
        if (raw.isNullOrEmpty()) return OperBoxSnapshot()
        return runCatching { json.decodeFromString<OperBoxSnapshot>(raw) }
            .getOrElse {
                Timber.e(it, "解析干员箱分片失败，回退为空快照")
                OperBoxSnapshot()
            }
    }

    companion object {
        private val Context.operBoxStore: DataStore<Preferences> by preferencesDataStore(
            name = "oper_box",
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        )

        fun create(context: Context, taskChainState: TaskChainState) =
            OperBoxRepository(context.operBoxStore, taskChainState)

        private fun keyOf(profileId: String) = stringPreferencesKey("operbox_$profileId")
    }
}
