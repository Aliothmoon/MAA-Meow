package com.aliothmoon.maameow.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.aliothmoon.maameow.data.model.toolbox.DepotItem
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ItemHelper
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
    /** 至少完整识别过一次仓库，普通材料的掉落累加与缺口重算以此为基线 */
    val hasSynced: Boolean get() = syncTimeMillis > 0L

    /** 物品是否具备库存基线：普通材料需全量识别过，货币类靠战斗累计自带初值 0 */
    fun hasBaselineFor(itemId: String): Boolean =
        hasSynced || itemId in ItemHelper.CURRENCY_ITEM_IDS

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

    val isLoaded: StateFlow<Boolean> get() = shards.isLoaded

    fun start() = shards.start()

    fun set(items: List<DepotItem>, profileId: String? = null): DepotSnapshot? {
        val scan = items.associate { it.id to it.count }
        val transform: (DepotSnapshot) -> DepotSnapshot = { current ->
            // 仓库识别不覆盖货币类，保留已有累计值（识别结果含该项时以识别为准）
            val preservedCurrencies = current.items.filterKeys { it in ItemHelper.CURRENCY_ITEM_IDS }
            DepotSnapshot(
                items = preservedCurrencies + scan,
                syncTimeMillis = System.currentTimeMillis(),
                scanItems = scan,
                prevScanItems = current.scanItems,
                prevSyncTimeMillis = if (current.scanItems.isEmpty()) 0L else current.syncTimeMillis,
            )
        }
        return if (profileId.isNullOrEmpty()) shards.mutate(transform) else shards.mutateFor(profileId, transform)
    }

    /** 将单场战斗掉落写入指定配置档（传 null 时退回活跃档） */
    fun merge(drops: List<Pair<String, Int>>, profileId: String? = null): Boolean {
        val valid = drops.filter { (itemId, add) -> add > 0 && !shouldExclude(itemId) }
        // 整关只掉排除集（如声望、家具零件）时无须记账，不算失败
        if (valid.isEmpty()) return true
        val transform: (DepotSnapshot) -> DepotSnapshot = { current ->
            // 未同步仓库前仅累计货币类，普通材料留空避免产生残缺库存
            val applicable = if (current.hasSynced) {
                valid
            } else {
                valid.filter { (itemId, _) -> itemId in ItemHelper.CURRENCY_ITEM_IDS }
            }
            if (applicable.isEmpty()) {
                current
            } else {
                val merged = current.items.toMutableMap()
                for ((itemId, add) in applicable) {
                    merged[itemId] = (merged[itemId] ?: 0) + add
                }
                current.copy(items = merged)
            }
        }
        val targetId = profileId?.takeIf { it.isNotEmpty() } ?: taskChainState.profileId.value
        val changed = shards.mutateForChanged(targetId, transform)
        if (!changed) {
            Timber.w("掉落未写入仓库：配置档无基线或已删除（profileId=%s, items=%d）", targetId, valid.size)
        }
        return changed
    }

    fun countOf(itemId: String): Int = snapshot.value.countOf(itemId)

    /** 按指定配置档读库存，避免运行中切档串读其他档数据 */
    fun countOf(itemId: String, profileId: String?): Int =
        if (profileId.isNullOrEmpty()) snapshot.value.countOf(itemId)
        else (shards.all.value[profileId] ?: DepotSnapshot()).countOf(itemId)

    fun hasSynced(profileId: String?): Boolean =
        if (profileId.isNullOrEmpty()) snapshot.value.hasSynced
        else shards.all.value[profileId]?.hasSynced == true

    /** 指定档是否具备该物品的库存基线（货币类免全量识别） */
    fun hasBaselineFor(itemId: String, profileId: String? = null): Boolean =
        itemId in ItemHelper.CURRENCY_ITEM_IDS || hasSynced(profileId)

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
