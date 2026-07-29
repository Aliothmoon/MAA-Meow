package com.aliothmoon.maameow.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator
import com.aliothmoon.maameow.data.preferences.TaskChainState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class OperBoxSnapshot(
    val owned: List<OperBoxOperator> = emptyList(),
    val notOwned: List<OperBoxOperator> = emptyList(),
    val syncTimeMillis: Long = 0L,
) {
    val hasSynced: Boolean get() = syncTimeMillis > 0L
}

/** 干员箱分片：内存权威，set 同步写内存并排队落盘。 */
class OperBoxRepository(
    store: DataStore<Preferences>,
    taskChainState: TaskChainState,
) {
    private val shards = ProfileShardStore(
        store = store,
        taskChainState = taskChainState,
        keyPrefix = KEY_PREFIX,
        serializer = OperBoxSnapshot.serializer(),
        empty = ::OperBoxSnapshot,
    )

    val snapshot: StateFlow<OperBoxSnapshot> get() = shards.snapshot

    val isLoaded: StateFlow<Boolean> get() = shards.isLoaded

    fun start() = shards.start()

    fun set(owned: List<OperBoxOperator>, notOwned: List<OperBoxOperator>) {
        shards.mutate {
            OperBoxSnapshot(
                owned = owned,
                notOwned = notOwned,
                syncTimeMillis = System.currentTimeMillis(),
            )
        }
    }

    companion object {
        private const val KEY_PREFIX = "operbox_"

        private val Context.operBoxStore: DataStore<Preferences> by preferencesDataStore(
            name = "oper_box",
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        )

        fun create(context: Context, taskChainState: TaskChainState) =
            OperBoxRepository(context.operBoxStore, taskChainState)
    }
}
