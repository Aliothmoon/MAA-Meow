package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.DepotMaintainConfig.Companion.EXPIRING_MEDICINE_DAYS
import com.aliothmoon.maameow.domain.models.DropTarget
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.maa.task.TaskSlot
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.serialization.Serializable

/** 库存保持计划：把 dropId 刷到 dropCount。 */
@Serializable
data class DepotMaintainPlan(
    val stage: String = "",
    val dropId: String = "",
    val dropCount: Int = 0,
    val useMedicine: Boolean = false,
    val medicineCount: Int = 0,
    val useStone: Boolean = false,
    val stoneCount: Int = 0,
)

/** 一条计划在本次运行里会被怎么处理 */
enum class DepotPlanOutcome {
    NoItem,
    ZeroTarget,
    Enough,
    StageRequired,
    StageClosed,
    Runnable,
}

/**
 * 判定一条计划的去向，执行侧据此跳过并打日志，配置页据此渲染
 *
 * 分支顺序对齐上游 SerializeTask：先查配置完整性，再查库存，最后才查关卡
 * —— 库存已够就不必再报关卡问题
 */
fun depotPlanOutcome(
    plan: DepotMaintainPlan,
    currentCount: Int,
    isStageOpen: (String) -> Boolean,
): DepotPlanOutcome = when {
    plan.dropId.isBlank() -> DepotPlanOutcome.NoItem
    plan.dropCount <= 0 -> DepotPlanOutcome.ZeroTarget
    currentCount >= plan.dropCount -> DepotPlanOutcome.Enough
    // 空串是合法关卡参数（当前/上次），但库存保持算不出缺口，必须落到具体关卡
    plan.stage.isBlank() -> DepotPlanOutcome.StageRequired
    !isStageOpen(plan.stage) -> DepotPlanOutcome.StageClosed
    else -> DepotPlanOutcome.Runnable
}

/**
 * 库存保持：展开为可选 Depot + N 个 Fight
 * 无库存记录按 0 满量刷（与 Fight 目标库存「未识别 skip」不同）
 */
@Serializable
data class DepotMaintainConfig(
    val updateDepot: Boolean = true,
    val customStageCode: Boolean = false,
    /** false→series=1；true→series=0（AUTO）。对齐 WPF UseAutoSeries。 */
    val useAutoSeries: Boolean = false,
    val skipDuringActivity: Boolean = false,
    val skipDuringResourceCollection: Boolean = false,
    /** 关掉后各计划隐藏理智药行，且一律按 0 下发 */
    val useMedicine: Boolean = true,
    /** 关掉后各计划隐藏源石行，且一律按 0 下发 */
    val useStone: Boolean = true,
    /** 对全部计划生效，阈值固定 [EXPIRING_MEDICINE_DAYS] 天 */
    val useExpiringMedicine: Boolean = false,
    val plans: List<DepotMaintainPlan> = emptyList(),
) : TaskParamProvider {

    override fun toTaskParams(ctx: TaskParamContext): List<MaaTaskParams> {
        if (skipDuringActivity && ctx.activityManager.isActivityOpen()) {
            ctx.appendLog(uiTextOf(R.string.runlog_depot_skipped_activity), LogLevel.INFO)
            return emptyList()
        }
        if (skipDuringResourceCollection && ctx.activityManager.isResourceCollectionOpen()) {
            ctx.appendLog(uiTextOf(R.string.runlog_depot_skipped_resource), LogLevel.INFO)
            return emptyList()
        }

        val params = mutableListOf<MaaTaskParams>()

        // append 缺口只是初值；Start 时 Refresher 用最新库存重算
        if (updateDepot) {
            params += MaaTaskParams(MaaTaskType.DEPOT, "{}")
        }

        val series = if (useAutoSeries) 0 else 1

        // 每份库存保持的计划日志前插一条分段，跟上游 AddLogSection 对齐
        if (plans.isNotEmpty()) {
            ctx.appendLog(uiTextOf(R.string.runlog_log_section, ctx.node.name), LogLevel.TRACE)
        }

        plans.forEachIndexed { index, plan ->
            val no = index + 1
            // 共用文案的首参在别处是任务名，编号前缀由调用方给
            val label = "#$no"
            val current = ctx.depotRepository.countOf(plan.dropId)
            val outcome = depotPlanOutcome(plan, current) { ctx.activityManager.isStageOpen(it) }
            when (outcome) {
                DepotPlanOutcome.NoItem -> {
                    ctx.appendLog(
                        uiTextOf(R.string.runlog_depot_plan_invalid_drop, no),
                        LogLevel.ERROR
                    )
                    return@forEachIndexed
                }

                DepotPlanOutcome.ZeroTarget -> {
                    ctx.appendLog(
                        uiTextOf(R.string.runlog_depot_plan_zero_count, no),
                        LogLevel.ERROR
                    )
                    return@forEachIndexed
                }

                DepotPlanOutcome.Enough -> {
                    val dropName = ctx.itemHelper.getItemInfo(plan.dropId)?.name ?: plan.dropId
                    ctx.appendLog(
                        uiTextOf(
                            R.string.runlog_depot_plan_inventory_enough,
                            label, dropName, current, plan.dropCount,
                        ),
                        LogLevel.TRACE,
                    )
                    return@forEachIndexed
                }

                DepotPlanOutcome.StageRequired -> {
                    ctx.appendLog(uiTextOf(R.string.runlog_depot_plan_no_stage, no), LogLevel.ERROR)
                    return@forEachIndexed
                }

                DepotPlanOutcome.StageClosed -> {
                    ctx.appendLog(
                        uiTextOf(R.string.runlog_depot_plan_stage_not_open, no, plan.stage),
                        LogLevel.TRACE,
                    )
                    return@forEachIndexed
                }

                DepotPlanOutcome.Runnable -> Unit
            }

            val need = plan.dropCount - current
            val dropName = ctx.itemHelper.getItemInfo(plan.dropId)?.name ?: plan.dropId
            ctx.appendLog(
                uiTextOf(
                    R.string.runlog_depot_plan_inventory_insufficient,
                    label, dropName, current, plan.dropCount, need,
                ),
                LogLevel.TRACE,
            )

            val listIndex = params.size
            val target = DropTarget(
                dropId = plan.dropId,
                dropCount = plan.dropCount,
                stage = plan.stage,
                medicine = if (useMedicine && plan.useMedicine) plan.medicineCount else 0,
                stone = if (useStone && plan.useStone) plan.stoneCount else 0,
                series = series,
                logLabel = no.toString(),
                medicineExpireDays = if (useExpiringMedicine) EXPIRING_MEDICINE_DAYS else null,
                report = ctx.report,
            )
            ctx.dropsRefresher.stage(TaskSlot(ctx.node.id, listIndex), target)
            params += MaaTaskParams(
                type = MaaTaskType.FIGHT,
                params = target.toFightParamsJson(need),
            )
        }

        return params
    }

    companion object {
        /** 临期药阈值，对齐上游 DepotMaintainTask.ExpiringMedicineDays，不开放给用户调 */
        const val EXPIRING_MEDICINE_DAYS = 2
    }
}
