package com.aliothmoon.maameow.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.aliothmoon.maameow.data.model.toolbox.DepotItem
import com.aliothmoon.maameow.data.preferences.TaskChainState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** @param syncTimeMillis 上次全量识别；0=从未识别。merge 不更新。 */
@Serializable
data class DepotSnapshot(
    val items: Map<String, Int> = emptyMap(),
    val syncTimeMillis: Long = 0L,
)

/** 仓库分片：内存权威，set/merge 同步写内存并排队落盘。 */
class DepotRepository(
    store: DataStore<Preferences>,
    taskChainState: TaskChainState,
) {
    private val shards = ProfileShardStore(
        store = store,
        taskChainState = taskChainState,
        keyPrefix = KEY_PREFIX,
        serializer = DepotSnapshot.serializer(),
        empty = ::DepotSnapshot,
    )

    val snapshot: StateFlow<DepotSnapshot> get() = shards.snapshot

    val isLoaded: StateFlow<Boolean> get() = shards.isLoaded

    fun start() = shards.start()

    fun set(items: List<DepotItem>) {
        shards.mutate {
            DepotSnapshot(
                items = items.associate { it.id to it.count },
                syncTimeMillis = System.currentTimeMillis(),
            )
        }
    }

    fun merge(drops: List<Pair<String, Int>>) {
        val valid = drops.filter { (itemId, add) -> add > 0 && !shouldExclude(itemId) }
        if (valid.isEmpty()) return
        shards.mutate { current ->
            val merged = current.items.toMutableMap()
            for ((itemId, add) in valid) {
                merged[itemId] = (merged[itemId] ?: 0) + add
            }
            current.copy(items = merged)
        }
    }

    fun countOf(itemId: String): Int = snapshot.value.items[itemId] ?: 0

    private fun shouldExclude(itemId: String): Boolean =
        itemId.isEmpty() || !itemId.all { it in '0'..'9' } || itemId in EXCLUDED_ITEM_IDS

    companion object {
        private const val KEY_PREFIX = "depot_"

        private val Context.depotStore: DataStore<Preferences> by preferencesDataStore(
            name = "depot",
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        )

        fun create(context: Context, taskChainState: TaskChainState) =
            DepotRepository(context.depotStore, taskChainState)

        private val EXCLUDED_ITEM_IDS = setOf(
            "3401",
            "3112", "3113", "3114",
            "5001",
        )
    }
}
