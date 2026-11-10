package com.aliothmoon.maameow.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withTimeoutOrNull
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

    val all: StateFlow<Map<String, T>> = _shards.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    val snapshot: StateFlow<T> = DerivedActiveSnapshot()

    /**
     * 全部配置档的分片（只读）。库存页跨配置档查看时用；
     * 从未写入过的配置档不在 map 中，调用方按 [empty] 兜底。
     */
    val allShards: StateFlow<Map<String, T>> = _shards.asStateFlow()

    private sealed interface PersistOp {
        data class Write(val profileId: String) : PersistOp
        data class Delete(val profileId: String) : PersistOp
        data class Flush(val done: CompletableDeferred<Unit>) : PersistOp
    }

    private val ops = Channel<PersistOp>(Channel.UNLIMITED)

    private companion object {
        /** 等一次落盘的兜底上限：写回协程停摆时不能让调用方无限挂起 */
        const val PERSIST_WAIT_TIMEOUT_MS = 5_000L
    }

    init {
        syncScope.launch { doConsume() }
        scope.launch { load() }
    }

    /** 订阅 [TaskChainState.profileDeleted] → [remove]。 */
    fun start() {
        scope.launch {
            // 逐条 try/catch：一条删档失败（磁盘 IOException 经由 awaitPersist 抛上来）不能让
            // 整个收集协程退出——start 只在 AppModule 里调一次，退出后该 store 永久不再清理分片。
            taskChainState.profileDeleted.collect { profileId ->
                try {
                    remove(profileId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "清理配置档分片失败: %s%s", keyPrefix, profileId)
                }
            }
        }
    }

    /** 写活跃档：同步改内存并排队落盘；返回写入值，没写返回 null */
    fun mutate(transform: (T) -> T): T? = write(taskChainState.profileId.value, transform)

    /** 写指定档；已删的档丢弃，免得留下孤儿分片 */
    fun mutate(profileId: String, transform: (T) -> T): T? {
        if (taskChainState.profiles.value.none { it.id == profileId }) {
            Timber.w("配置档不存在，跳过写入: %s%s", keyPrefix, profileId)
            return null
        }
        return write(profileId, transform)
    }

    private fun write(profileId: String, transform: (T) -> T): T? {
        if (profileId.isEmpty()) {
            Timber.w("活跃配置档为空，跳过写入: %s", keyPrefix)
            return null
        }
        return mutateFor(profileId, transform)
    }

    /** 同步改内存并排队落盘（指定配置档）。库存页需要操作非活跃档。 */
    fun mutateFor(profileId: String, transform: (T) -> T): T? =
        mutateForInternal(profileId, transform).value

    /**
     * 同 [mutateFor]，但返回「值到底有没有变」。
     *
     * 调用方若靠返回值判断「写成功没有」，不能用 [mutateFor]：目标档分片存在但守卫拒绝写
     * （例如 `hasSynced == false` 时 transform 原样返回）时返回值非空却什么都没写。
     */
    fun mutateForChanged(profileId: String, transform: (T) -> T): Boolean =
        mutateForInternal(profileId, transform).changed

    private data class Mutation<T>(val value: T?, val changed: Boolean)

    private fun mutateForInternal(profileId: String, transform: (T) -> T): Mutation<T> {
        if (profileId.isEmpty()) {
            Timber.w("配置档为空，跳过写入: %s", keyPrefix)
            return Mutation(null, false)
        }
        if (!_isLoaded.value) {
            Timber.w("分片尚未装载完成即写入，基准值可能为空: %s%s", keyPrefix, profileId)
        }
        // 配置档已被删掉时不要再建分片：运行中删档后，这一场的识别结果仍会按开跑档写进来，
        // 没有这道闸就会给死档留一条完整分片（UI 看不到、磁盘不回收，导入同 id 备份时还会复活）。
        // 检查必须放进 _shards.update 的 lambda 里：与 remove() 的 `it - profileId` 在同一个
        // 原子顺序点上判定，否则「检查通过 → 删档先落地 → 我们再写回」仍会复活死档。
        // 列表为空/读不到视为「还没载入」——那种情况下没有依据判断某个 id 是死档，照常放行。
        var result: T? = null
        var changed = false
        var ignored = false
        _shards.update { map ->
            // _shards.update 的 lambda 可能被 CAS 重试执行多次：标志必须在每次尝试开头重置，
            // 否则上一次尝试算出的 changed=true 会带着「ignored」一起返回，出现
            // 「内存写了、磁盘没排队」的错位。
            result = null
            changed = false
            ignored = false
            if (isProfileDeleted(profileId)) {
                ignored = true
                return@update map
            }
            val cur = map[profileId] ?: empty()
            val next = transform(cur)
            if (next === cur) {
                result = map[profileId]
                map
            } else {
                changed = true
                result = next
                map + (profileId to next)
            }
        }
        if (ignored) {
            Timber.w("配置档已删除，忽略写入: %s%s", keyPrefix, profileId)
            return Mutation(null, false)
        }
        if (changed) ops.trySend(PersistOp.Write(profileId))
        return Mutation(result, changed)
    }

    /**
     * 目标档是否还在配置档列表里：null = 还没载入/读不到，无法判断。
     *
     * 写路径只在「确定已删除」时拦（fail-open：未知就照常写），
     * 删除路径只在「确定还在」时跳过（未知就照删）——两者的容错方向相反，
     * 所以必须暴露三态，不能共用一个布尔。
     */
    private fun profileExistence(profileId: String): Boolean? {
        val knownProfiles = runCatching { taskChainState.profiles.value }.getOrNull() ?: return null
        if (!taskChainState.isLoaded.value || knownProfiles.isEmpty()) return null
        return knownProfiles.any { it.id == profileId }
    }

    /** 确定已删除（列表已载入、非空，且不含该 id） */
    private fun isProfileDeleted(profileId: String): Boolean = profileExistence(profileId) == false

    suspend fun remove(profileId: String) {
        if (profileId.isEmpty()) return
        // 删档事件是异步送达的：期间用户可能又导入了含同 id 的备份并写入。
        // 这时若照删，会把刚重建的档的分片（内存 + 磁盘）一起抹掉。
        if (profileExistence(profileId) == true) {
            Timber.i("配置档已重建，跳过清理: %s%s", keyPrefix, profileId)
            return
        }
        _shards.update { it - profileId }
        ops.send(PersistOp.Delete(profileId))
        awaitPersist()
    }

    /**
     * 等到本 Flush 前的写/删完成；失败抛 [IOException]。
     *
     * 带超时：写回队列是单协程串行消费，一旦那个协程停摆（作用域被取消、上一次
     * `store.edit` 卡在慢盘上），`done.await()` 会永远不返回 —— 删档/导入的调用方
     * 就会一直挂在 `viewModelScope` 里，连带把删除事件的收集也拖住。
     */
    suspend fun awaitPersist() {
        val done = CompletableDeferred<Unit>()
        ops.send(PersistOp.Flush(done))
        val finished = withTimeoutOrNull(PERSIST_WAIT_TIMEOUT_MS) { done.await() }
        if (finished == null) {
            Timber.w("等待分片落盘超时(%dms): %s", PERSIST_WAIT_TIMEOUT_MS, keyPrefix)
        }
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
        // 按配置档列表对账：删除事件可能丢（无订阅者/进程被杀）、删除落盘可能失败，
        // 这些孤儿分片如果原样灌进内存，`countOf/hasSynced` 会把死档当有效基线
        // （表现：库存永远不涨、判定照旧）。列表未载入或为空时不做对账，避免误删。
        val knownProfiles = runCatching { taskChainState.profiles.value }.getOrNull()
        val reconcile = taskChainState.isLoaded.value && !knownProfiles.isNullOrEmpty()
        val orphans = if (reconcile && knownProfiles != null) {
            val validIds = knownProfiles.map { it.id }.toSet()
            fromDisk.keys - validIds
        } else {
            emptySet()
        }
        if (orphans.isNotEmpty()) {
            Timber.i("丢弃 %d 条孤儿分片: %s %s", orphans.size, keyPrefix, orphans)
            runCatching {
                store.edit { prefsEdit -> orphans.forEach { prefsEdit.remove(keyOf(it)) } }
            }.onFailure { Timber.e(it, "清理孤儿分片失败: %s", keyPrefix) }
        }
        _shards.update { current -> (fromDisk - orphans) + current }
        _isLoaded.value = true
    }

    private suspend fun doConsume() {
        var pendingError: IOException? = null
        for (op in ops) {
            try {
                when (op) {
                    is PersistOp.Write -> {
                        // 排队期间配置档可能已被删：落盘前再判一次，并把内存里刚写回的键也撤掉，
                        // 否则「内存有值 → 写盘」这条链仍会给死档留下分片
                        if (isProfileDeleted(op.profileId)) {
                            Timber.w("配置档已删除，丢弃落盘: %s%s", keyPrefix, op.profileId)
                            _shards.update { it - op.profileId }
                        } else {
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
                            } else {
                                // 出队时内存里已经没有这条分片（被并发 remove 撤掉）：merge 那边
                                // 早已返回 true，这里不吭声就等于「报成功但磁盘什么都没有」
                                Timber.w("分片已不在内存，跳过落盘: %s%s", keyPrefix, op.profileId)
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
