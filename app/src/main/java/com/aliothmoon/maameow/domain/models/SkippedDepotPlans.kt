package com.aliothmoon.maameow.domain.models

/**
 * 一份库存保持里预检时已充足、因而没下发的计划，对齐上游 70d1f06bb8
 *
 * 预检用的是识别前的缓存；本轮仓库识别后若实际不足，计划已无法在运行中补进队列，只能提示重跑
 * 只收关卡今天打得了的，打不了的实际不足也补不上
 *
 * @param firstOnly 仅执行第一个不足计划：只提示第一个翻转的
 * @param sortByProgress 按达成率排序执行：复查时按最新库存重排，对齐上游 ReviewSkippedPlansAfterDepotSync
 * @param chosen 仅首个模式下本轮下发的主计划，只提示按最新库存会排在它前面的翻转
 * @param countDualChips 双芯片折算计入芯片组，与预检口径一致
 */
data class SkippedDepotPlans(
    val taskName: String,
    val plans: List<Plan>,
    val firstOnly: Boolean,
    val sortByProgress: Boolean = false,
    val chosen: Plan? = null,
    val countDualChips: Boolean = false,
) {
    /** @param no 计划序号，从 1 起，与预检日志一致 */
    data class Plan(val no: Int, val dropId: String, val dropCount: Int)

    /** 按最新库存挑出要提示的翻转，附带当前数量，按执行顺序排列 */
    fun stale(countOf: (String) -> Int): List<Pair<Plan, Int>> {
        val counts = (plans + listOfNotNull(chosen)).associateWith {
            DualChipConversion.count(it.dropId, countDualChips, countOf)
        }
        val order = depotPlanOrder<Plan>(
            sortByProgress,
            progress = { depotPlanProgress(counts.getValue(it), it.dropCount) },
            no = { it.no },
        )
        val flipped = plans
            .filter { counts.getValue(it) < it.dropCount }
            .filter { chosen == null || order.compare(it, chosen) < 0 }
            .sortedWith(order)
        return (if (firstOnly) flipped.take(1) else flipped).map { it to counts.getValue(it) }
    }
}

/** 复查出的翻转：预检够、实际不够 */
data class StaleSkippedPlan(
    val taskName: String,
    val no: Int,
    val dropName: String,
    val current: Int,
    val target: Int,
)

/** 库存达成率，执行侧排序与复查共用 */
fun depotPlanProgress(current: Int, dropCount: Int): Double = current.toDouble() / dropCount

/** 库存保持的执行顺序：排序开启按达成率升序，同率与未开启都按原序号 */
fun <T> depotPlanOrder(
    sortByProgress: Boolean,
    progress: (T) -> Double,
    no: (T) -> Int,
): Comparator<T> = if (sortByProgress) compareBy(progress, no) else compareBy(no)
