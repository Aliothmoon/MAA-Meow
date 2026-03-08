package com.aliothmoon.maameow.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.TaskTypeInfo
import com.aliothmoon.maameow.data.model.TaskParamProvider
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.remote.PermissionGrantRequest
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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

    private val defaultChain: List<TaskChainNode> by lazy { buildDefaultChain() }

    private val _chain = MutableStateFlow(defaultChain)
    val chain: StateFlow<List<TaskChainNode>> = _chain.asStateFlow()

    init {
        // 从 DataStore 加载持久化数据（仅一次），覆盖默认值
        scope.launch {
            val prefs = context.store.data.first()
            _chain.value = decodeChain(prefs[CHAIN_KEY])
        }
    }


    suspend fun addNode(typeInfo: TaskTypeInfo, afterIndex: Int = -1) {
        updateChain { current ->
            val node = TaskChainNode(
                id = UUID.randomUUID().toString(),
                name = typeInfo.displayName,
                enabled = true,
                config = typeInfo.defaultConfig()
            )
            if (afterIndex < 0 || afterIndex >= current.size) {
                current.add(node)
            } else {
                current.add(afterIndex + 1, node)
            }
            Timber.d("Added node: %s (%s)", node.name, typeInfo.name)
        }
    }

    suspend fun removeNode(nodeId: String) {
        updateChain { current ->
            current.removeAll { it.id == nodeId }
            Timber.d("Removed node: %s", nodeId)
        }
    }

    suspend fun renameNode(nodeId: String, newName: String) {
        updateChain { current ->
            val idx = current.indexOfFirst { it.id == nodeId }
            if (idx >= 0) {
                current[idx] = current[idx].copy(name = newName)
                Timber.d("Renamed node %s to: %s", nodeId, newName)
            } else {
                Timber.w("renameNode: node %s not found", nodeId)
            }
        }
    }

    suspend fun setNodeEnabled(nodeId: String, enabled: Boolean) {
        updateChain { current ->
            val idx = current.indexOfFirst { it.id == nodeId }
            if (idx >= 0) {
                current[idx] = current[idx].copy(enabled = enabled)
                Timber.d("Set node %s enabled: %s", nodeId, enabled)
            } else {
                Timber.w("setNodeEnabled: node %s not found", nodeId)
            }
        }
    }

    suspend fun updateNodeConfig(nodeId: String, config: TaskParamProvider) {
        updateChain { current ->
            val idx = current.indexOfFirst { it.id == nodeId }
            if (idx >= 0) {
                current[idx] = current[idx].copy(config = config)
            } else {
                Timber.w("updateNodeConfig: node %s not found", nodeId)
            }
        }
    }

    suspend fun reorderNodes(fromIndex: Int, toIndex: Int) {
        updateChain { current ->
            require(fromIndex in current.indices) { "fromIndex out of bounds: $fromIndex" }
            require(toIndex in current.indices) { "toIndex out of bounds: $toIndex" }
            val node = current.removeAt(fromIndex)
            current.add(toIndex, node)
            Timber.d("Moved node from %d to %d", fromIndex, toIndex)
        }
    }

    inline fun <reified T : TaskParamProvider> firstConfigFlow(): Flow<T?> {
        return chain.map { nodes ->
            nodes.firstNotNullOfOrNull { it.config as? T }
        }.distinctUntilChanged()
    }

    inline fun <reified T : TaskParamProvider> findFirstConfig(): T? {
        return chain.value.firstNotNullOfOrNull { it.config as? T }
    }

    fun grantGameBatteryExemption() {
        findFirstConfig<WakeUpConfig>()?.let {
            val pkg = Packages[it.clientType] ?: return
            runCatching {
                RemoteServiceManager.getInstanceOrNull()?.grantPermissions(
                    PermissionGrantRequest(
                        packageName = pkg,
                        uid = 0,
                        permissions = PermissionGrantRequest.PERM_BATTERY
                    )
                )
                Timber.d("Battery exemption granted for game: %s", pkg)
            }.onFailure { e ->
                Timber.w(e, "Failed to grant battery exemption for game")
            }
        }
    }

    private suspend inline fun updateChain(
        crossinline block: (MutableList<TaskChainNode>) -> Unit
    ) {
        val current = _chain.value.toMutableList()
        block(current)
        reindex(current)
        val snapshot = current.toList()
        _chain.value = snapshot              // 同步更新，立即可见
        context.store.edit { prefs ->        // 异步持久化
            prefs[CHAIN_KEY] = json.encodeToString<List<TaskChainNode>>(snapshot)
        }
    }

    private fun decodeChain(raw: String?): List<TaskChainNode> {
        if (raw.isNullOrEmpty()) return defaultChain
        return runCatching {
            json.decodeFromString<List<TaskChainNode>>(raw)
        }.getOrElse {
            Timber.w(it, "Failed to decode task chain, using defaults")
            defaultChain
        }
    }

    private fun reindex(nodes: MutableList<TaskChainNode>) {
        for (i in nodes.indices) {
            nodes[i] = nodes[i].copy(order = i)
        }
    }

    private fun buildDefaultChain(): List<TaskChainNode> {
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
