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
 * 仓库数据持久化，按配置档（TaskProfile）分片存于 DataStore，每档一个 key。
 *
 * 上游 WPF 把仓库数据存在全局 `DataDir/depot.json`，与多配置无关，
 * 导致多账号共用一份库存。此处按 profileId 分片修正该缺陷 ——
 * 仓库是账号属性，而 TaskProfile 是 MaaMeow 里最接近「账号」的粒度。
 */
class DepotRepository(
    private val store: DataStore<Preferences>,
    private val taskChainState: TaskChainState,
) {
    private val json = JsonUtils.common
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 跟随活跃配置档自动切换的仓库快照。构造即开始收集，不必等 [start]。
     *
     * DataStore 在文件损坏或磁盘异常时会让 `data` 抛 IOException，
     * 不接住会终结收集协程使快照永久静止，故与 KSP 生成的偏好访问器一样回退空值。
     */
    val snapshot: StateFlow<DepotSnapshot> =
        combine(taskChainState.activeProfileId, store.data) { profileId, prefs ->
            if (profileId.isEmpty()) DepotSnapshot() else decode(prefs[keyOf(profileId)])
        }
            .catch { e ->
                if (e is IOException) {
                    Timber.e(e, "读取仓库数据失败")
                    emit(DepotSnapshot())
                } else {
                    throw e
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, DepotSnapshot())

    /** 订阅配置档删除事件以清理其分片。 */
    fun start() {
        scope.launch {
            taskChainState.profileDeleted.collect { dropProfile(it) }
        }
    }

    /**
     * 仓库识别完成：全量覆盖并刷新识别时间。
     * 不过滤排除集 —— 识别结果就是仓库事实，家具/碳/经验真在仓库里就该记录。
     */
    suspend fun replaceAll(items: List<DepotItem>) {
        editSnapshot { _ ->
            DepotSnapshot(
                items = items.associate { it.id to it.count },
                syncTimeMillis = System.currentTimeMillis(),
            )
        }
    }

    /**
     * 关卡掉落增量累加，对齐上游 ToolboxViewModel.UpdateDepotFromDrops：
     * 只累加 add > 0、过滤排除项，且**不更新** syncTimeMillis。
     *
     * @param drops itemId 到本次新增数量的列表
     */
    suspend fun applyDrops(drops: List<Pair<String, Int>>) {
        val valid = drops.filter { (itemId, add) -> add > 0 && !shouldExclude(itemId) }
        if (valid.isEmpty()) return
        editSnapshot { current ->
            val merged = current.items.toMutableMap()
            for ((itemId, add) in valid) {
                merged[itemId] = (merged[itemId] ?: 0) + add
            }
            current.copy(items = merged)
        }
    }

    /** 当前库存数量；无快照或无该材料一律返回 0（对齐上游语义）。 */
    fun countOf(itemId: String): Int = snapshot.value.items[itemId] ?: 0

    /** 配置档被删除时清理其分片。 */
    suspend fun dropProfile(profileId: String) {
        if (profileId.isEmpty()) return
        try {
            store.edit { it.remove(keyOf(profileId)) }
        } catch (e: IOException) {
            Timber.w(e, "删除仓库分片失败: %s", profileId)
        }
    }

    /**
     * 在 DataStore 事务内读-改-写当前配置档的快照。
     * 从事务内读取而非 [snapshot]，避免并发写入时用到陈旧值。
     */
    private suspend fun editSnapshot(transform: (DepotSnapshot) -> DepotSnapshot) {
        // activeProfileId 由 TaskChainState 异步加载，加载完成前写入会落到错误的分片
        taskChainState.isLoaded.first { it }
        val profileId = taskChainState.activeProfileId.value
        if (profileId.isEmpty()) {
            Timber.w("活跃配置档为空，跳过仓库写入")
            return
        }
        try {
            store.edit { prefs ->
                val key = keyOf(profileId)
                prefs[key] = json.encodeToString(transform(decode(prefs[key])))
            }
        } catch (e: IOException) {
            Timber.e(e, "写入仓库分片失败: %s", profileId)
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
