package com.aliothmoon.maameow.domain.models

/**
 * 指定掉落「目标库存」模式的运行时重算目标。
 *
 * 在 append 时随 [com.aliothmoon.maameow.maa.task.MaaTaskParams] 一起快照，
 * 任务真正开始（TaskChainStart）时由 [com.aliothmoon.maameow.domain.service.FightDropsRefresher]
 * 按最新库存重算缺口并通过 AsstSetTaskParams 回写。
 *
 * 迁移自 WPF FightSettingsUserControlModel.RefreshFightTaskDrops 的入参。
 * 之所以快照整份字段而非反查配置：运行中改配置不应影响已排队任务；
 * 且 SetTaskParams 是整表重放，刷新 JSON 必须带上 medicine/stone/series 等，
 * 否则会把 append 时的值冲成 core 默认值。
 *
 * @param dropId 材料 ID
 * @param dropCount 目标库存数量
 * @param stage 关卡代码
 * @param medicine 理智药上限（开关关闭时为 0）
 * @param stone 源石上限（开关关闭时为 0）
 * @param series 代理倍率（含 SeriesLock 钳制结果）
 * @param logLabel 日志标签：库存保持为计划序号，理智作战为节点名
 * @param medicineExpireDays 临期理智药天数；null 表示不下发该字段
 * @param drGrandet 是否开启葛朗台模式
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
)
