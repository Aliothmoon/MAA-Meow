package com.aliothmoon.maameow.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aliothmoon.maameow.data.model.toolbox.DepotItem
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * 单个配置档的仓库快照。
 *
 * @param items itemId -> 数量
 * @param syncTimeMillis 上次「完整仓库识别」的时间戳；0 表示从未识别过。
 *   掉落累加不更新此值 —— 它表示识别时间，不是数据变更时间。
 */
@Serializable
data class DepotSnapshot(
    val items: Map<String, Int> = emptyMap(),
    val syncTimeMillis: Long = 0L,
)

/**
 * 仓库数据：按配置档分片持久化，运行时以**内存快照**为权威读取源。
 *
 * ## 为何内存写穿
 *
 * MaaCore 回调线程上识别完成 / 掉落后，下一条 Fight 的 `TaskChainStart` 会立刻
 * [countOf] 重算缺口。若只写 DataStore 再等 `store.data` 传播，与同步回调之间
 * **没有 happens-before**，会读到旧库存并多刷。
 *
 * 约定：
 * - [replaceAllSync] / [applyDropsSync]（及对应 suspend 封装）**先**原子更新内存，
 *   **再**串行异步落盘；[countOf] / [snapshot] 只读内存。
 * - 不在 MaaCore 回调线程 `runBlocking` 等 DataStore。
 * - **不订阅 `store.data`**：磁盘只在某档尚未加载时读一次（见 [onActiveProfileChanged]）。
 * - 切配置档时优先用已有内存分片，没有才读盘 hydrate。
 *
 * ## 调用方必须同步写入
 *
 * 「前序掉落反映进后续缺口」之所以成立，依赖两件事叠加：
 * 1. `MaaCoreCallback` 是 oneway binder，而 binder 对**同一 node 的异步事务严格串行保序**
 *    —— 所以 `StageDrops` 一定先于下一条 `TaskChainStart` 被处理；
 * 2. 处理 `StageDrops` 的一方（`SubTaskHandler` / `ToolboxResultCollector`）调用的是
 *    [applyDropsSync] / [replaceAllSync]，在回调线程上**同步**完成内存写入。
 *
 * 把调用点改回 `launch { applyDrops(...) }` 之类的异步形式会破坏第 2 点：
 * 顺序保证还在，写入却跑到了下一条回调之后，缺口重算读到旧库存 —— 且不会报错，
 * 只表现为偶尔多刷几关。改动这些调用点前请先确认这条不变量。
 */
class DepotRepository(
    private val store: DataStore<Preferences>,
    private val taskChainState: TaskChainState,
) {
    private val json = JsonUtils.common
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memoryLock = Any()
    private val persistMutex = Mutex()

    /** profileId → 最新已知快照（含尚未落盘的变更） */
    private val shards = ConcurrentHashMap<String, DepotSnapshot>()

    /** 有未确认落盘的分片；落盘成功且内存未再变时清除 */
    private val dirty = ConcurrentHashMap.newKeySet<String>()

    private val _snapshot = MutableStateFlow(DepotSnapshot())

    /**
     * 当前活跃配置档的仓库快照（内存权威）。
     * UI 与 [countOf] 均读此流；构造后即由 disk/profile 收集器维护。
     */
    val snapshot: StateFlow<DepotSnapshot> = _snapshot.asStateFlow()

    init {
        scope.launch {
            taskChainState.activeProfileId.collect { profileId -> onActiveProfileChanged(profileId) }
        }
    }

    /** 订阅配置档删除事件以清理其分片。 */
    fun start() {
        scope.launch {
            taskChainState.profileDeleted.collect { dropProfile(it) }
        }
    }

    /**
     * 仓库识别完成：全量覆盖并刷新识别时间（**同步更新内存**，再异步落盘）。
     * 不过滤排除集 —— 识别结果就是仓库事实。
     */
    fun replaceAllSync(items: List<DepotItem>) {
        mutateActive { _ ->
            DepotSnapshot(
                items = items.associate { it.id to it.count },
                syncTimeMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun replaceAll(items: List<DepotItem>) {
        taskChainState.isLoaded.first { it }
        replaceAllSync(items)
        awaitPersistForActive()
    }

    /**
     * 关卡掉落增量累加（**同步更新内存**）。
     * 只累加 add > 0、过滤排除项，且**不更新** syncTimeMillis。
     */
    fun applyDropsSync(drops: List<Pair<String, Int>>) {
        val valid = drops.filter { (itemId, add) -> add > 0 && !shouldExclude(itemId) }
        if (valid.isEmpty()) return
        mutateActive { current ->
            val merged = current.items.toMutableMap()
            for ((itemId, add) in valid) {
                merged[itemId] = (merged[itemId] ?: 0) + add
            }
            current.copy(items = merged)
        }
    }

    suspend fun applyDrops(drops: List<Pair<String, Int>>) {
        taskChainState.isLoaded.first { it }
        applyDropsSync(drops)
        awaitPersistForActive()
    }

    /** 当前库存数量；无快照或无该材料一律返回 0（对齐上游语义）。读内存，不读未传播的磁盘。 */
    fun countOf(itemId: String): Int = snapshot.value.items[itemId] ?: 0

    /** 配置档被删除时清理其分片（内存 + 磁盘）。 */
    suspend fun dropProfile(profileId: String) {
        if (profileId.isEmpty()) return
        synchronized(memoryLock) {
            shards.remove(profileId)
            dirty.remove(profileId)
            if (taskChainState.activeProfileId.value == profileId) {
                _snapshot.value = DepotSnapshot()
            }
        }
        try {
            store.edit { it.remove(keyOf(profileId)) }
        } catch (e: IOException) {
            Timber.w(e, "删除仓库分片失败: %s", profileId)
        }
    }

    /**
     * 切换活跃配置档：内存里没有该档的分片时才读盘 hydrate。
     *
     * **不订阅 `store.data`**。曾经用 `combine(activeProfileId, store.data)` 驱动内存，
     * 那是写穿改造前的遗留：`store.data` 的 emission 异步消费，一条携带旧值的 emission
     * 完全可能排在自己那次落盘之后才被处理，此时它会把 [shards] 回滚到旧值，
     * 下一次 [mutateActive] 便从旧值累加，**悄悄丢掉一次更新**（无异常、无日志，
     * 只表现为掉落少记一次）。`dirty` 只说明「有没有待落盘的写」，判断不了 emission 是否陈旧；
     * 重读磁盘也只是把窗口缩小，因为读盘本身是挂起点。
     *
     * 既然所有写入都先过内存再落盘，磁盘永远不可能比内存新，订阅它就没有意义 ——
     * 只在「这个档还没加载过」时读一次即可。档被删除时 [dropProfile] 会显式移除分片，
     * 于是下次切回该档能重新 hydrate。
     */
    private suspend fun onActiveProfileChanged(profileId: String) {
        if (profileId.isEmpty()) {
            synchronized(memoryLock) { _snapshot.value = DepotSnapshot() }
            return
        }
        synchronized(memoryLock) {
            shards[profileId]?.let {
                _snapshot.value = it
                return
            }
        }
        val diskSnap = try {
            decode(store.data.first()[keyOf(profileId)])
        } catch (e: IOException) {
            Timber.e(e, "读取仓库数据失败")
            DepotSnapshot()
        }
        synchronized(memoryLock) {
            // 读盘期间可能已经有写入落到内存，此时内存优先
            val effective = shards.getOrPut(profileId) { diskSnap }
            if (taskChainState.activeProfileId.value == profileId) {
                _snapshot.value = effective
            }
        }
    }

    private fun mutateActive(transform: (DepotSnapshot) -> DepotSnapshot) {
        val profileId = taskChainState.activeProfileId.value
        if (profileId.isEmpty()) {
            Timber.w("活跃配置档为空，跳过仓库写入")
            return
        }
        val next: DepotSnapshot
        synchronized(memoryLock) {
            val current = shards[profileId] ?: DepotSnapshot()
            next = transform(current)
            shards[profileId] = next
            dirty.add(profileId)
            _snapshot.value = next
        }
        scope.launch { persistProfile(profileId) }
    }

    private suspend fun awaitPersistForActive() {
        taskChainState.isLoaded.first { it }
        val profileId = taskChainState.activeProfileId.value
        if (profileId.isEmpty()) return
        // 有限次推进落盘；IO 失败时保留 dirty/内存，避免死等
        repeat(8) {
            if (profileId !in dirty) return
            persistProfile(profileId)
        }
    }

    private suspend fun persistProfile(profileId: String) {
        persistMutex.withLock {
            while (profileId in dirty) {
                val snap = synchronized(memoryLock) { shards[profileId] } ?: run {
                    dirty.remove(profileId)
                    return@withLock
                }
                try {
                    store.edit { prefs ->
                        prefs[keyOf(profileId)] = json.encodeToString(snap)
                    }
                    synchronized(memoryLock) {
                        // 落盘期间若内存又变了，保持 dirty 再写一轮
                        if (shards[profileId] == snap) {
                            dirty.remove(profileId)
                        }
                    }
                } catch (e: IOException) {
                    Timber.e(e, "写入仓库分片失败: %s", profileId)
                    // 保留 dirty，避免误从磁盘 hydrate 冲掉内存；下次写入再试
                    return@withLock
                }
            }
        }
    }

    private fun decode(raw: String?): DepotSnapshot {
        if (raw.isNullOrEmpty()) return DepotSnapshot()
        return runCatching { json.decodeFromString<DepotSnapshot>(raw) }
            .getOrElse {
                Timber.e(it, "解析仓库分片失败，回退为空快照")
                DepotSnapshot()
            }
    }

    // 空串的 all{} 恒真，故 isEmpty 判断不可省；只认 ASCII 数字，避免 isDigit 放行阿拉伯-印度数字
    private fun shouldExclude(itemId: String): Boolean =
        itemId.isEmpty() || !itemId.all { it in '0'..'9' } || itemId in EXCLUDED_ITEM_IDS

    companion object {
        private val Context.depotStore: DataStore<Preferences> by preferencesDataStore(
            name = "depot",
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        )

        fun create(context: Context, taskChainState: TaskChainState) =
            DepotRepository(context.depotStore, taskChainState)

        private fun keyOf(profileId: String) = stringPreferencesKey("depot_$profileId")

        /**
         * 掉落累加排除集，对齐上游 ToolboxViewModel.ExcludedItemIds。
         * 注意与 ItemHelper.excludedValues（掉落材料下拉过滤）语义不同，不可混用。
         */
        private val EXCLUDED_ITEM_IDS = setOf(
            "3401",                 // 家具
            "3112", "3113", "3114", // 碳
            "5001",                 // 经验
        )
    }
}
