package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.DepotMaintainConfig.Companion.EXPIRING_MEDICINE_DAYS
import com.aliothmoon.maameow.domain.models.DropTarget
import com.aliothmoon.maameow.domain.models.DualChipConversion
import com.aliothmoon.maameow.domain.models.SkippedDepotPlans
import com.aliothmoon.maameow.domain.models.TaskCandidate
import com.aliothmoon.maameow.domain.models.TaskFallbackChain
import com.aliothmoon.maameow.domain.models.depotPlanOrder
import com.aliothmoon.maameow.domain.models.depotPlanProgress
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.maa.task.TaskSlot
import com.aliothmoon.maameow.utils.i18n.UiText
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
 * 预检时无库存记录按 0 计照常下发，前面的仓库识别会补上数据；开始时仍无任何库存数据才跳过
 * （Fight 目标库存在预检就跳过）
 */
@Serializable
data class DepotMaintainConfig(
    val updateDepot: Boolean = true,
    val onlyFirstInsufficientPlan: Boolean = false,
    /** 按库存达成率升序下发，只改执行顺序不动列表，对齐 WPF SortByProgress */
    val sortByProgress: Boolean = false,
    /** 芯片组计入双芯片折算，只改计数口径，对齐 WPF CountDualChipAsChipPack */
    val countDualChipAsChipPack: Boolean = false,
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
            params += MaaTaskParams(
                MaaTaskType.DEPOT,
                "{}",
                logName = uiTextOf(R.string.runlog_task_with_detail, ctx.node.name, uiTextOf(R.string.maa_depot)),
            )
        }

        // 每份库存保持的计划日志前插一条分段，跟上游 AddLogSection 对齐
        if (plans.isNotEmpty()) {
            ctx.appendLog(uiTextOf(R.string.runlog_log_section, ctx.node.name), LogLevel.TRACE)
        }

        // 预先评估后备计划，暂不输出日志
        val evaluated = plans.mapIndexed { index, plan ->
            val current = DualChipConversion.count(plan.dropId, countDualChipAsChipPack, ctx.depotRepository::countOf)
            PlanDecision(
                index = index,
                plan = plan,
                current = current,
                outcome = depotPlanOutcome(plan, current) { ctx.activityManager.isStageOpen(it) },
            )
        }
        // 以下按执行顺序处理，序号仍取原位置
        val decisions = evaluated.sortedWith(depotPlanOrder<PlanDecision>(sortByProgress, { it.progress }, { it.no }))
        if (sortByProgress && plans.size > 1) {
            ctx.appendLog(uiTextOf(R.string.runlog_depot_sorted_by_progress), LogLevel.INFO)
        }

        // 仅首个模式：预检日志止于主计划，其后作后备
        val primaryAt = if (onlyFirstInsufficientPlan) {
            decisions.indexOfFirst { it.outcome == DepotPlanOutcome.Runnable }
        } else {
            -1
        }
        val primary = decisions.getOrNull(primaryAt)
        val logged = if (primary != null) decisions.take(primaryAt + 1) else decisions
        for (d in logged) {
            ctx.appendLog(d.logText(ctx), d.logLevel())
        }

        // 登记不随日志截断
        stageSkippedPlans(ctx, decisions, primary)
        val chosen = if (onlyFirstInsufficientPlan) {
            listOfNotNull(primary)
        } else {
            decisions.filter { it.outcome == DepotPlanOutcome.Runnable }
        }
        for (d in chosen) {
            val listIndex = params.size
            val target = d.target(ctx)
            ctx.dropsRefresher.stage(TaskSlot(ctx.node.id, listIndex), target)
            params += MaaTaskParams(
                type = MaaTaskType.FIGHT,
                params = target.toFightParamsJson(d.need),
                logName = d.logName(ctx),
            )
            if (primary != null) {
                ctx.registerFallbacks(listIndex, buildFallbacks(ctx, decisions.drop(primaryAt + 1)))
            }
        }

        return params
    }

    private fun stageSkippedPlans(
        ctx: TaskParamContext,
        decisions: List<PlanDecision>,
        primary: PlanDecision?,
    ) {
        val plans = decisions
            .filter { d ->
                d.outcome == DepotPlanOutcome.Enough &&
                        // 当作库存为 0 再判一次，关卡缺失或今天不开放的不收
                        depotPlanOutcome(d.plan, 0) { ctx.activityManager.isStageOpen(it) } ==
                        DepotPlanOutcome.Runnable
            }
            .map { it.skippedPlan() }
        ctx.dropsRefresher.stageSkipped(
            SkippedDepotPlans(
                taskName = ctx.node.name,
                plans = plans,
                firstOnly = onlyFirstInsufficientPlan,
                sortByProgress = sortByProgress,
                chosen = primary?.skippedPlan(),
                countDualChips = countDualChipAsChipPack,
            )
        )
    }

    /** 后备候选携带其前方的跳过原因，尾部日志留到全部失败后输出 */
    private fun buildFallbacks(
        ctx: TaskParamContext,
        rest: List<PlanDecision>,
    ): TaskFallbackChain {
        val candidates = mutableListOf<TaskCandidate>()
        var pending = mutableListOf<Pair<UiText, LogLevel>>()
        for (d in rest) {
            if (d.outcome != DepotPlanOutcome.Runnable) {
                pending += d.logText(ctx) to d.logLevel()
                continue
            }
            val target = d.target(ctx)
            candidates += TaskCandidate(
                type = MaaTaskType.FIGHT,
                params = target.toFightParamsJson(d.need),
                logName = d.logName(ctx),
                dropTarget = target,
                logsBefore = pending,
                logOnSuccess = d.logText(ctx) to d.logLevel(),
            )
            pending = mutableListOf()
        }
        return TaskFallbackChain(candidates = candidates, logsWhenExhausted = pending)
    }

    private inner class PlanDecision(
        val index: Int,
        val plan: DepotMaintainPlan,
        val current: Int,
        val outcome: DepotPlanOutcome,
    ) {
        val no: Int get() = index + 1
        val need: Int get() = plan.dropCount - current

        /** 无效计划排最后 */
        val progress: Double
            get() = when (outcome) {
                DepotPlanOutcome.NoItem, DepotPlanOutcome.ZeroTarget -> Double.MAX_VALUE
                else -> depotPlanProgress(current, plan.dropCount)
            }

        fun skippedPlan() = SkippedDepotPlans.Plan(no = no, dropId = plan.dropId, dropCount = plan.dropCount)

        fun logName(ctx: TaskParamContext): UiText = UiText.Dynamic("${ctx.node.name} #$no")

        fun logLevel(): LogLevel = when (outcome) {
            DepotPlanOutcome.NoItem,
            DepotPlanOutcome.ZeroTarget,
            DepotPlanOutcome.StageRequired -> LogLevel.ERROR

            else -> LogLevel.TRACE
        }

        fun logText(ctx: TaskParamContext): UiText {
            // 共用文案的首参在别处是任务名，编号前缀由调用方给
            val label = "#$no"
            val dropName by lazy { ctx.itemHelper.getItemInfo(plan.dropId)?.name ?: plan.dropId }
            return when (outcome) {
                DepotPlanOutcome.NoItem -> uiTextOf(R.string.runlog_depot_plan_invalid_drop, no)
                DepotPlanOutcome.ZeroTarget -> uiTextOf(R.string.runlog_depot_plan_zero_count, no)
                DepotPlanOutcome.StageRequired -> uiTextOf(R.string.runlog_depot_plan_no_stage, no)
                DepotPlanOutcome.StageClosed ->
                    uiTextOf(
                        R.string.runlog_depot_plan_stage_not_open,
                        no,
                        ctx.activityManager.getStageDisplayName(plan.stage),
                    )

                DepotPlanOutcome.Enough -> uiTextOf(
                    R.string.runlog_depot_plan_inventory_enough,
                    label, dropName, current, plan.dropCount,
                )

                DepotPlanOutcome.Runnable -> uiTextOf(
                    R.string.runlog_depot_plan_inventory_insufficient,
                    label, dropName, current, plan.dropCount, need,
                )
            }
        }

        fun target(ctx: TaskParamContext): DropTarget = DropTarget(
            dropId = plan.dropId,
            dropCount = plan.dropCount,
            stage = plan.stage,
            medicine = if (useMedicine && plan.useMedicine) plan.medicineCount else 0,
            stone = if (useStone && plan.useStone) plan.stoneCount else 0,
            series = if (useAutoSeries) 0 else 1,
            logLabel = no.toString(),
            medicineExpireDays = if (useExpiringMedicine) EXPIRING_MEDICINE_DAYS else null,
            report = ctx.report,
            countDualChips = countDualChipAsChipPack,
        )
    }

    companion object {
        /** 临期药阈值，对齐上游 DepotMaintainTask.ExpiringMedicineDays，不开放给用户调 */
        const val EXPIRING_MEDICINE_DAYS = 2
    }
}
