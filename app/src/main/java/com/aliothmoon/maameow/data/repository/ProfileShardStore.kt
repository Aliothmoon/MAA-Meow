package com.aliothmoon.maameow.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import timber.log.Timber
import java.io.IOException

class ProfileShardStore<T : Any>(
    private val store: DataStore<Preferences>,
    private val taskChainState: TaskChainState,
    private val keyPrefix: String,
    private val serializer: KSerializer<T>,
    private val empty: () -> T,
) {
    private val json = JsonUtils.common
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val _shards = MutableStateFlow<Map<String, T>>(emptyMap())

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    val snapshot: StateFlow<T> = DerivedActiveSnapshot()

    private sealed interface PersistOp {
        data class Write(val profileId: String) : PersistOp
        data class Delete(val profileId: String) : PersistOp
        data class Flush(val done: CompletableDeferred<Unit>) : PersistOp
    }

    private val ops = Channel<PersistOp>(Channel.UNLIMITED)

    init {
        syncScope.launch { doConsume() }
        scope.launch { load() }
    }

    /** 订阅 [TaskChainState.profileDeleted] → [remove]。 */
    fun start() {
        scope.launch {
            taskChainState.profileDeleted.collect { remove(it) }
        }
    }

    /** 同步改内存并排队落盘。 */
    fun mutate(transform: (T) -> T) {
        val profileId = taskChainState.profileId.value
        if (profileId.isEmpty()) {
            Timber.w("活跃配置档为空，跳过写入: %s", keyPrefix)
            return
        }
        if (!_isLoaded.value) {
            Timber.w("分片尚未装载完成即写入，基准值可能为空: %s%s", keyPrefix, profileId)
        }

        _shards.update { map ->
            val cur = map[profileId] ?: empty()
            map + (profileId to transform(cur))
        }
        ops.trySend(PersistOp.Write(profileId))
    }

    suspend fun remove(profileId: String) {
        if (profileId.isEmpty()) return
        _shards.update { it - profileId }
        ops.send(PersistOp.Delete(profileId))
        awaitPersist()
    }

    /** 等到本 Flush 前的写/删完成；失败抛 [IOException]。 */
    suspend fun awaitPersist() {
        val done = CompletableDeferred<Unit>()
        ops.send(PersistOp.Flush(done))
        done.await()
    }

    private suspend fun load() {
        val prefs = try {
            store.data.first()
        } catch (e: IOException) {
            Timber.e(e, "读取分片失败: %s", keyPrefix)
            null
        }
        val fromDisk = buildMap {
            prefs?.asMap()?.forEach { (key, value) ->
                if (!key.name.startsWith(keyPrefix)) return@forEach
                val profileId = key.name.removePrefix(keyPrefix)
                put(profileId, decode(value as? String))
            }
        }
        _shards.update { current -> fromDisk + current }
        _isLoaded.value = true
    }

    private suspend fun doConsume() {
        var pendingError: IOException? = null
        for (op in ops) {
            try {
                when (op) {
                    is PersistOp.Write -> {
                        val snap = _shards.value[op.profileId]
                        if (snap != null) {
                            try {
                                store.edit { prefs ->
                                    prefs[keyOf(op.profileId)] =
                                        json.encodeToString(serializer, snap)
                                }
                                pendingError = null
                            } catch (e: IOException) {
                                Timber.e(e, "写入分片失败: %s%s", keyPrefix, op.profileId)
                                pendingError = e
                            }
                        }
                    }

                    is PersistOp.Delete -> {
                        try {
                            store.edit { it.remove(keyOf(op.profileId)) }
                            pendingError = null
                        } catch (e: IOException) {
                            Timber.w(e, "删除分片失败: %s%s", keyPrefix, op.profileId)
                            pendingError = e
                        }
                    }

                    is PersistOp.Flush -> {
                        val err = pendingError
                        if (err != null) {
                            pendingError = null
                            op.done.completeExceptionally(err)
                        } else {
                            op.done.complete(Unit)
                        }
                    }
                }
            } catch (e: Throwable) {
                Timber.e(e, "持久化队列处理失败: %s", keyPrefix)
                if (op is PersistOp.Flush) {
                    op.done.completeExceptionally(e)
                } else if (e is IOException) {
                    pendingError = e
                }
            }
        }
    }

    private fun decode(raw: String?): T {
        if (raw.isNullOrEmpty()) return empty()
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrElse {
                Timber.e(it, "解析分片失败，回退为空: %s", keyPrefix)
                empty()
            }
    }

    private fun keyOf(profileId: String) = stringPreferencesKey("$keyPrefix$profileId")


    /**
     * 活跃档 [StateFlow]：`value` 同步派生，不用 `stateIn`
     *（回调热路径 [DepotRepository.countOf] 不能等 sharing）。
     */
    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    private inner class DerivedActiveSnapshot : StateFlow<T> {
        private val changes: Flow<T> = combine(
            _shards,
            taskChainState.profileId,
        ) { shards, profileId -> shards[profileId] ?: empty() }

        override val value: T
            get() = run {
                val id = taskChainState.profileId.value
                if (id.isEmpty()) return empty()
                _shards.value[id] ?: empty()
            }

        override val replayCache: List<T>
            get() = listOf(value)

        override suspend fun collect(collector: FlowCollector<T>): Nothing {
            changes.collect { collector.emit(it) }
            error("DerivedActiveSnapshot nothing return")
        }
    }
}
