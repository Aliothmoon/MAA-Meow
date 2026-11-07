package com.aliothmoon.maameow.data.repository

import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator

/**
 * 干员识别的导出分段：已拥有（帕拉斯置顶，界面第一张就是它）+ 未拥有。
 *
 * 没识别过就返回两个空表，避免导出/分享出一份空数据还被当成有效结果。
 * 工具页、库存页的 JSON / Markdown / CSV 与导出图都走这里，口径只有一处。
 */
fun OperBoxSnapshot.toExportSections(
    includeNotOwned: Boolean = true,
): Pair<List<OperBoxOperator>, List<OperBoxOperator>> {
    if (!hasSynced) return emptyList<OperBoxOperator>() to emptyList()
    val ownedList = owned.sortedByDescending { it.isPallas }
    val notOwnedList = if (includeNotOwned) notOwned else emptyList()
    return ownedList to notOwnedList
}

/**
 * 干员识别的导出列表：owned + notOwned（全部可用干员）。
 *
 * 没识别过就返回空表，避免导出/分享出一份空数据还被当成有效结果。
 * 工具页面与库存页共用；[includeNotOwned] 为 false 时只导出已拥有的。
 */
fun OperBoxSnapshot.toExportList(includeNotOwned: Boolean = true): List<OperBoxOperator> =
    toExportSections(includeNotOwned).let { (owned, notOwned) -> owned + notOwned }
