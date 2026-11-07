package com.aliothmoon.maameow.data.repository

import com.aliothmoon.maameow.data.model.toolbox.DepotItem
import com.aliothmoon.maameow.data.resource.ItemInfo

/** 游戏 sortId 升序，查不到的靠后并按 id 兜底 */
fun depotItemOrder(itemMap: Map<String, ItemInfo>): Comparator<String> =
    compareBy({ itemMap[it]?.sortId ?: Int.MAX_VALUE }, { it })

/** 将持久化 map 转为展示/导出用列表，排序与识别回调一致 */
fun DepotSnapshot.toSortedItems(itemMap: Map<String, ItemInfo>): List<DepotItem> =
    items.map { (id, count) -> DepotItem(id, count) }
        .sortedWith(compareBy(depotItemOrder(itemMap)) { it.id })

/** 相邻两次全量识别之差，不含其间合并的掉落 */
data class DepotDelta(
    val fromMillis: Long,
    val changes: Map<String, Int>,
)

fun DepotSnapshot.delta(): DepotDelta? {
    if (prevSyncTimeMillis <= 0L) return null
    val changes = (scanItems.keys + prevScanItems.keys)
        .associateWith { (scanItems[it] ?: 0) - (prevScanItems[it] ?: 0) }
        .filterValues { it != 0 }
    return DepotDelta(prevSyncTimeMillis, changes)
}

fun formatDepotDelta(value: Int): String = if (value > 0) "+$value" else "$value"
/**
 * 企鹅物流 / ArkPlanner 的仓库 JSON。
 *
 * 按原始快照导出，不做任何物品过滤（抽卡资源也在内），与识别结果同源。
 * 工具页面与库存页共用，别在两处各写一份格式。
 */
fun DepotSnapshot.toArkPlannerJson(itemMap: Map<String, ItemInfo>): String {
    val itemsJson = toSortedItems(itemMap)
        .joinToString(",") { """{"id":"${it.id}","have":${it.count}}""" }
    return """{"@type":"@penguin-statistics/depot","items":[$itemsJson]}"""
}

/** Lolicon 的仓库 JSON，口径同 [toArkPlannerJson]。 */
fun DepotSnapshot.toLoliconJson(itemMap: Map<String, ItemInfo>): String =
    "{${toSortedItems(itemMap).joinToString(",") { "\"${it.id}\":${it.count}" }}}"
