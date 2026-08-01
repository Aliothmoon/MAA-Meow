package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.R
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

/**
 * 库存保持：展开为可选 Depot + N 个 Fight。
 * 无库存记录按 0 满量刷（与 Fight 目标库存「未识别 skip」不同）。
 */
@Serializable
data class DepotMaintainConfig(
    val updateDepot: Boolean = true,
    val customStageCode: Boolean = false,
    /** false→series=1；true→series=0（AUTO）。对齐 WPF UseAutoSeries。 */
    val useAutoSeries: Boolean = false,
    val skipDuringActivity: Boolean = false,
    val skipDuringResourceCollection: Boolean = false,
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

        plans.forEachIndexed { index, plan ->
            val no = index + 1
            if (plan.dropId.isBlank()) {
                ctx.appendLog(uiTextOf(R.string.runlog_depot_plan_invalid_drop, no), LogLevel.ERROR)
                return@forEachIndexed
            }
            if (plan.dropCount <= 0) {
                ctx.appendLog(uiTextOf(R.string.runlog_depot_plan_zero_count, no), LogLevel.ERROR)
                return@forEachIndexed
            }
            if (plan.stage.isBlank()) {
                ctx.appendLog(uiTextOf(R.string.runlog_depot_plan_no_stage, no), LogLevel.ERROR)
                return@forEachIndexed
            }

            val current = ctx.depotRepository.countOf(plan.dropId)
            val need = plan.dropCount - current
            if (need <= 0) {
                val dropName = ctx.itemHelper.getItemInfo(plan.dropId)?.name ?: plan.dropId
                ctx.appendLog(
                    uiTextOf(
                        R.string.runlog_depot_plan_inventory_enough,
                        no.toString(), dropName, current, plan.dropCount,
                    ),
                    LogLevel.TRACE,
                )
                return@forEachIndexed
            }

            if (!ctx.activityManager.isStageOpen(plan.stage)) {
                ctx.appendLog(
                    uiTextOf(R.string.runlog_depot_plan_stage_not_open, no, plan.stage),
                    LogLevel.TRACE,
                )
                return@forEachIndexed
            }

            val listIndex = params.size
            val target = DropTarget(
                dropId = plan.dropId,
                dropCount = plan.dropCount,
                stage = plan.stage,
                medicine = if (plan.useMedicine) plan.medicineCount else 0,
                stone = if (plan.useStone) plan.stoneCount else 0,
                series = series,
                logLabel = no.toString(),
            )
            ctx.dropsRefresher.stage(TaskSlot(ctx.node.id, listIndex), target)
            params += MaaTaskParams(
                type = MaaTaskType.FIGHT,
                params = target.toFightParamsJson(need),
            )
        }

        return params
    }
}
