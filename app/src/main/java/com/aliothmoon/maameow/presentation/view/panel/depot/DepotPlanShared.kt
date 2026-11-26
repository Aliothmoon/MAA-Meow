package com.aliothmoon.maameow.presentation.view.panel.depot

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.aliothmoon.maameow.data.model.DepotMaintainPlan

/** 格式化「当前 / 目标」，未具备库存基线时显示「--」，含双芯片折算时拆分为「原始 (+折算)」 */
internal fun depotProgressText(
    current: Int,
    target: Int,
    synced: Boolean,
    dualChipExtra: Int = 0,
): String {
    val currentText = when {
        !synced -> "--"
        dualChipExtra > 0 -> "${(current - dualChipExtra).coerceAtLeast(0)} (+$dualChipExtra)"
        else -> current.toString()
    }
    return "$currentText / $target"
}

/** 警告提示配色（背景, 前景），深浅主题下均保持足够对比度 */
@Composable
internal fun depotWarningColors(): Pair<Color, Color> {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) {
        Color(0xFFF0B050).copy(alpha = 0.16f) to Color(0xFFF0B050)
    } else {
        Color(0xFFE6A23C).copy(alpha = 0.16f) to Color(0xFF9A5B00)
    }
}

/** 供 LazyColumn 拖拽排序使用的稳定键包装（允许相同 dropId 重复出现） */
internal data class IndexedDepotPlan(
    val uid: Long,
    val plan: DepotMaintainPlan,
)

/** 外部变更计划列表时尽量复用已有 uid，确保 LazyColumn 键稳定不闪烁 */
internal fun reconcileOrderedPlans(
    prev: List<IndexedDepotPlan>,
    next: List<DepotMaintainPlan>,
    allocUid: () -> Long,
): List<IndexedDepotPlan> {
    val matched = arrayOfNulls<Long>(next.size)
    val usedUids = HashSet<Long>(prev.size)
    val pool = LinkedHashMap<DepotMaintainPlan, ArrayDeque<Long>>()
    for (item in prev) {
        pool.getOrPut(item.plan) { ArrayDeque() }.addLast(item.uid)
    }
    for (i in next.indices) {
        val uid = pool[next[i]]?.removeFirstOrNull()
        if (uid != null) {
            matched[i] = uid
            usedUids.add(uid)
        }
    }
    if (prev.size == next.size) {
        for (i in next.indices) {
            if (matched[i] == null) {
                val candidate = prev[i].uid
                if (usedUids.add(candidate)) {
                    matched[i] = candidate
                }
            }
        }
    }
    return next.mapIndexed { i, plan ->
        IndexedDepotPlan(uid = matched[i] ?: allocUid(), plan = plan)
    }
}

/** 为外部计划列表维护稳定 uid，并支持拖拽落盘前提前登记新顺序以防回流闪烁 */
internal class DepotPlanUidAllocator {
    private var nextUid = 0L
    private var last: List<IndexedDepotPlan> = emptyList()

    fun reconcile(plans: List<DepotMaintainPlan>): List<IndexedDepotPlan> {
        val result = reconcileOrderedPlans(last, plans) { nextUid++ }
        last = result
        return result
    }

    fun seed(ordered: List<IndexedDepotPlan>) {
        last = ordered
    }
}
