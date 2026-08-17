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
    val report: ReportOptions = ReportOptions.DEFAULT,
) {
    /**
     * 按缺口生成 Fight 参数 JSON。need≤0 或 [forceSkip] 时下发 `times=0`：
     * core（v6.17.0-beta.2 起）会禁用整条 Fight 子任务链，不进终端也不导航
     * drops 仍给 1 占位，任务已在队列里只能改参，字段不能空
     *
     * DepotMaintain append 与两侧刷新走本方法；Fight 目标库存 append 自行组 JSON
     *（`times=actualTimes`），刷新再经此抬到 MAX。
     *
     * @param forceSkip 缺口尚在但已判定跑不动（理智不足），直接跳过整个任务
     */
    fun toFightParamsJson(need: Int, forceSkip: Boolean = false): String = buildJsonObject {
        val skip = need <= 0 || forceSkip
        put("stage", stage)
        put("times", if (skip) 0 else Int.MAX_VALUE)
        put("series", series)
        put("medicine", medicine)
        put("stone", stone)
        medicineExpireDays?.let { put("medicine_expire_days", it) }
        if (drGrandet) put("DrGrandet", true)
        put("drops", buildJsonObject { put(dropId, if (need <= 0) 1 else need) })
        putReportFields(report)
    }.toString()
}
