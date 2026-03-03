package com.aliothmoon.maameow.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.TaskTypeInfo
import com.aliothmoon.maameow.data.model.TaskParamProvider
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import java.util.UUID

class TaskChainState(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = JsonUtils.common

    companion object {
        private val Context.store: DataStore<Preferences> by preferencesDataStore(
            name = "task_chain"
        )
        private val CHAIN_KEY = stringPreferencesKey("chain")
    }

    val chain: StateFlow<List<TaskChainNode>> =
        context.store.data
            .map { prefs ->
                val jsonStr = prefs[CHAIN_KEY] ?: ""
                if (jsonStr.isEmpty()) {
                    getDefaultChain()
                } else {
                    runCatching {
                        json.decodeFromString<List<TaskChainNode>>(jsonStr)
                    }.getOrElse {
                        Timber.w(it, "Failed to decode task chain, using defaults")
                        getDefaultChain()
                    }
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, getDefaultChain())

    suspend fun setChain(nodes: List<TaskChainNode>) {
        context.store.edit { prefs ->
            prefs[CHAIN_KEY] = json.encodeToString(nodes)
        }
    }

    suspend fun addNode(typeInfo: TaskTypeInfo, afterIndex: Int = -1) {
        val current = chain.value.toMutableList()
        val node = TaskChainNode(
            id = UUID.randomUUID().toString(),
            name = typeInfo.displayName,
            enabled = true,
            order = 0,
            config = typeInfo.defaultConfig()
        )
        if (afterIndex < 0 || afterIndex >= current.size) {
            current.add(node)
        } else {
            current.add(afterIndex + 1, node)
        }
        reindex(current)
        setChain(current)
        Timber.d("Added node: %s (%s)", node.name, typeInfo.name)
    }

    suspend fun removeNode(nodeId: String) {
        val current = chain.value.toMutableList()
        current.removeAll { it.id == nodeId }
        reindex(current)
        setChain(current)
        Timber.d("Removed node: %s", nodeId)
    }

    suspend fun renameNode(nodeId: String, newName: String) {
        val current = chain.value.map { node ->
            if (node.id == nodeId) node.copy(name = newName) else node
        }
        setChain(current)
        Timber.d("Renamed node %s to: %s", nodeId, newName)
    }

    suspend fun setNodeEnabled(nodeId: String, enabled: Boolean) {
        val current = chain.value.map { node ->
            if (node.id == nodeId) node.copy(enabled = enabled) else node
        }
        setChain(current)
        Timber.d("Set node %s enabled: %s", nodeId, enabled)
    }

    suspend fun updateNodeConfig(nodeId: String, config: TaskParamProvider) {
        val current = chain.value.map { node ->
            if (node.id == nodeId) node.copy(config = config) else node
        }
        setChain(current)
    }

    suspend fun reorderNodes(fromIndex: Int, toIndex: Int) {
        val current = chain.value.toMutableList()
        require(fromIndex in current.indices) { "fromIndex out of bounds: $fromIndex" }
        require(toIndex in current.indices) { "toIndex out of bounds: $toIndex" }
        val node = current.removeAt(fromIndex)
        current.add(toIndex, node)
        reindex(current)
        setChain(current)
        Timber.d("Moved node from %d to %d", fromIndex, toIndex)
    }

    inline fun <reified T : TaskParamProvider> firstConfigFlow(): Flow<T?> {
        return chain.map { nodes ->
            nodes.mapNotNull { it.config as? T }.firstOrNull()
        }.distinctUntilChanged()
    }

    inline fun <reified T : TaskParamProvider> findFirstConfig(): T? {
        return chain.value.mapNotNull { it.config as? T }.firstOrNull()
    }

    private fun reindex(nodes: MutableList<TaskChainNode>) {
        for (i in nodes.indices) {
            nodes[i] = nodes[i].copy(order = i)
        }
    }

    private fun getDefaultChain(): List<TaskChainNode> {
        return TaskTypeInfo.entries.mapIndexed { index, info ->
            TaskChainNode(
                name = info.displayName,
                enabled = false,
                order = index,
                config = info.defaultConfig()
            )
        }
    }
}
