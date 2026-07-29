package com.aliothmoon.maameow.domain.models

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 目标库存刷新快照，只活在 [com.aliothmoon.maameow.domain.service.FightDropsRefresher]。
 * 整表字段是为了 SetTaskParams 整表重放，避免冲掉 medicine/stone/series。
 *
 * @param logLabel 库存保持=计划序号，理智作战=节点名
 */
data class DropTarget(
    val dropId: String,
    val dropCount: Int,
    val stage: String,
    val medicine: Int,
    val stone: Int,
    val series: Int,
    val logLabel: String,
    val medicineExpireDays: Int? = null,
    val drGrandet: Boolean = false,
) {
    /**
     * 按缺口生成 Fight 参数 JSON。need≤0 时 `times=0` + `drops=1` 止损
     * （任务已在队列只能改参；drops 不能空）。
     *
     * DepotMaintain append 与两侧刷新走本方法；Fight 目标库存 append 自行组 JSON
     *（`times=actualTimes`），刷新再经此抬到 MAX。
     */
    fun toFightParamsJson(need: Int): String = buildJsonObject {
        put("stage", stage)
        put("times", if (need <= 0) 0 else Int.MAX_VALUE)
        put("series", series)
        put("medicine", medicine)
        put("stone", stone)
        medicineExpireDays?.let { put("medicine_expire_days", it) }
        if (drGrandet) put("DrGrandet", true)
        put("drops", buildJsonObject { put(dropId, if (need <= 0) 1 else need) })
    }.toString()
}
