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
import timber.log.Timber

/**
 * @param syncTimeMillis 上次全量识别；0=从未识别；merge 不更新
 * @param scanItems 本次识别原值，merge 不动
 * @param prevSyncTimeMillis 上次识别时间；0=无对比基线
 */
@Serializable
data class DepotSnapshot(
    val items: Map<String, Int> = emptyMap(),
    val syncTimeMillis: Long = 0L,
    val scanItems: Map<String, Int> = emptyMap(),
    val prevScanItems: Map<String, Int> = emptyMap(),
    val prevSyncTimeMillis: Long = 0L,
) {
    /** 至少完整识别过一次仓库，掉落累加与缺口重算都以此为基线 */
    val hasSynced: Boolean get() = syncTimeMillis > 0L

    fun countOf(itemId: String): Int = items[itemId] ?: 0
}

/** 仓库分片：内存权威，set/merge 同步写内存并排队落盘。 */
class DepotRepository(
    store: DataStore<Preferences>,
    private val taskChainState: TaskChainState,
) {
    private val shards = ProfileShardStore(
        store = store,
        taskChainState = taskChainState,
        keyPrefix = KEY_PREFIX,
        serializer = DepotSnapshot.serializer(),
        empty = ::DepotSnapshot,
    )

    val snapshot: StateFlow<DepotSnapshot> get() = shards.snapshot

    /** 全部配置档的仓库快照（只读），库存页跨配置档查看用。 */
    val snapshots: StateFlow<Map<String, DepotSnapshot>> get() = shards.allShards

    val isLoaded: StateFlow<Boolean> get() = shards.isLoaded

    fun start() = shards.start()

    fun set(items: List<DepotItem>, profileId: String? = null): DepotSnapshot? {
        val scan = items.associate { it.id to it.count }
        val transform: (DepotSnapshot) -> DepotSnapshot = { current ->
            DepotSnapshot(
                items = scan,
                syncTimeMillis = System.currentTimeMillis(),
                scanItems = scan,
                prevScanItems = current.scanItems,
                prevSyncTimeMillis = if (current.scanItems.isEmpty()) 0L else current.syncTimeMillis,
            )
        }
        return if (profileId.isNullOrEmpty()) shards.mutate(transform) else shards.mutateFor(profileId, transform)
    }

    /**
     * 把一场战斗的掉落写进仓库。
     *
     * [profileId] 由任务启动时捕获并一路传下来：跑起来之后再切配置档，这一场的掉落仍应记在
     * 开跑那一档上，而不是跟着「当前活跃档」跑过去（`shards.mutate` 默认写活跃档）。
     * 传 null 时退回活跃档，用于没有任务上下文的调用点。
     */
    fun merge(drops: List<Pair<String, Int>>, profileId: String? = null): Boolean {
        val valid = drops.filter { (itemId, add) -> add > 0 && !shouldExclude(itemId) }
        // 整关只掉排除集（例如 CE-6 只掉龙门币）时没有可记的条目：这不是失败，别让调用方告警
        if (valid.isEmpty()) return true
        val transform: (DepotSnapshot) -> DepotSnapshot = { current ->
            // 没有识别基线时累加只会得到残缺数据，留空让消费侧按无数据处理
            if (!current.hasSynced) {
                current
            } else {
                val merged = current.items.toMutableMap()
                for ((itemId, add) in valid) {
                    merged[itemId] = (merged[itemId] ?: 0) + add
                }
                current.copy(items = merged)
            }
        }
        // 用「值有没有变」判成功：目标档分片存在但 hasSynced=false 时 transform 会原样返回，
        // 拿写入结果值判会得到「成功」而实际一个数都没落，掉落静默丢失
        val targetId = profileId?.takeIf { it.isNotEmpty() } ?: taskChainState.profileId.value
        val changed = shards.mutateForChanged(targetId, transform)
        if (!changed) {
            // 这一场没有落账：该档从未识别过（没有累加基线）或已被删除。
            // 以前这里完全静默，用户只看到「库存没涨」；至少留一条可查的痕迹
            Timber.w("掉落未写入仓库：配置档无基线或已删除（profileId=%s, items=%d）", targetId, valid.size)
        }
        return changed
    }

    fun countOf(itemId: String): Int = snapshot.value.countOf(itemId)

    /**
     * 按指定配置档读库存：任务链跑到一半用户切了档，判定仍要用「开跑那一档」的数据，
     * 否则预检会按新档的库存算缺口、给 core 下发错误的 times。
     */
    fun countOf(itemId: String, profileId: String?): Int =
        if (profileId.isNullOrEmpty()) snapshot.value.countOf(itemId)
        else (snapshots.value[profileId] ?: DepotSnapshot()).countOf(itemId)

    fun hasSynced(profileId: String?): Boolean =
        if (profileId.isNullOrEmpty()) snapshot.value.hasSynced
        else snapshots.value[profileId]?.hasSynced == true

    /** 清空指定配置档仓库快照（库存页删除；非活跃档也能清）。 */
    fun clear(profileId: String) {
        // 已经是空快照时原样返回，别给「从没识别过」的档建一条 syncTimeMillis=0 的分片：
        // 那会让 hasSynced 之外的路径把它当成「有基线」，也给死档分片添来源
        shards.mutateFor(profileId) { current -> if (current == DepotSnapshot()) current else DepotSnapshot() }
    }

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
