package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.models.DropTarget
import com.aliothmoon.maameow.domain.models.SeriesLock
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 库存保持计划：把 dropId 材料刷到 dropCount 数量。
 *
 * 迁移自 WPF DepotMaintainTask.Plan。
 * 上游的 TaskId 字段用于运行时回头读配置，Android 侧不持久化它：
 * 运行时状态不应污染配置，且后续的缺口重算会在下发任务时把计划字段整体快照走。
 */
@Serializable
data class DepotMaintainPlan(
    /** 关卡代码；空串表示未指定（展开时会报错跳过） */
    val stage: String = "",
    /** 指定掉落材料 ID */
    val dropId: String = "",
    /** 目标库存数量 */
    val dropCount: Int = 0,
    val useMedicine: Boolean = false,
    val medicineCount: Int = 0,
    val useStone: Boolean = false,
    val stoneCount: Int = 0,
)

/**
 * 库存保持任务配置。
 *
 * 容器型任务：本身不对应任何 MaaCore 任务类型，展开成 1 个 Depot（可选）+ N 个 Fight。
 * 迁移自 WPF DepotMaintainTask + DepotMaintainTaskUserControlModel.ISerialize.Serialize。
 */
@Serializable
data class DepotMaintainConfig(
    /** 任务开始前先跑一次仓库识别，刷新库存数据 */
    val updateDepot: Boolean = true,
    /** 关卡手动输入（对齐 WPF IsStageManually，命名沿用 FightConfig 的 customStageCode） */
    val customStageCode: Boolean = false,
    /** SideStory 活动期间跳过整个任务 */
    val skipDuringActivity: Boolean = false,
    /** 资源收集限时全天开放期间跳过整个任务 */
    val skipDuringResourceCollection: Boolean = false,
    /** 保持计划，顺序即优先级 */
    val plans: List<DepotMaintainPlan> = emptyList(),
) : TaskParamProvider {

    override fun toTaskParams(ctx: TaskParamContext): TaskParamResult {
        val logs = mutableListOf<Pair<UiText, LogLevel>>()

        if (skipDuringActivity && ctx.activityManager.isActivityOpen()) {
            logs += uiTextOf(R.string.runlog_depot_skipped_activity) to LogLevel.INFO
            return TaskParamResult(emptyList(), logs)
        }
        if (skipDuringResourceCollection && ctx.activityManager.isResourceCollectionOpen()) {
            logs += uiTextOf(R.string.runlog_depot_skipped_resource) to LogLevel.INFO
            return TaskParamResult(emptyList(), logs)
        }

        val params = mutableListOf<MaaTaskParams>()

        // 刷新库存数据。注意：本轮的缺口在下面按当前快照算死，识别结果要到下次运行才生效，
        // 运行时按最新库存重算是后续任务（对齐上游 RefreshFightTaskDrops）
        if (updateDepot) {
            params += MaaTaskParams(MaaTaskType.DEPOT, "{}")
        }

        // TODO: MaaCore 适配代理倍率后删除，交回 core 默认值 1
        val series = if (SeriesLock.isLocked(ctx.clientType)) -1 else 1

        plans.forEachIndexed { index, plan ->
            val no = index + 1
            if (plan.dropId.isBlank()) {
                logs += uiTextOf(R.string.runlog_depot_plan_invalid_drop, no) to LogLevel.ERROR
                return@forEachIndexed
            }
            if (plan.dropCount <= 0) {
                logs += uiTextOf(R.string.runlog_depot_plan_zero_count, no) to LogLevel.ERROR
                return@forEachIndexed
            }
            if (plan.stage.isBlank()) {
                logs += uiTextOf(R.string.runlog_depot_plan_no_stage, no) to LogLevel.ERROR
                return@forEachIndexed
            }
            // 对齐上游：GetFightStage 无条件走 IsStageOpen，手动输入模式同样受检
            // （customStageCode 只改变 UI 控件形态）。上游此处 AddLog 未传颜色，默认 Trace
            if (!ctx.activityManager.isStageOpen(plan.stage)) {
                logs += uiTextOf(
                    R.string.runlog_depot_plan_stage_not_open, no, plan.stage
                ) to LogLevel.TRACE
                return@forEachIndexed
            }

            // 对齐上游：无库存数据或无该材料记录一律当 0，即按目标数量满量刷
            val current = ctx.depotRepository.countOf(plan.dropId)
            val need = plan.dropCount - current
            if (need <= 0) {
                val dropName = ctx.itemHelper.getItemInfo(plan.dropId)?.name ?: plan.dropId
                // 第 1 个占位符是 %1$s：PR5 运行时重算会复用同一条并传任务名
                logs += uiTextOf(
                    R.string.runlog_depot_plan_inventory_enough,
                    no.toString(), dropName, current, plan.dropCount,
                ) to LogLevel.TRACE
                return@forEachIndexed
            }

            val medicine = if (plan.useMedicine) plan.medicineCount else 0
            val stone = if (plan.useStone) plan.stoneCount else 0
            // times 固定 Int.MAX_VALUE，靠 drops 达标终止，而非预先算次数
            val json = buildJsonObject {
                put("stage", plan.stage)
                put("times", Int.MAX_VALUE)
                put("series", series)
                put("medicine", medicine)
                put("stone", stone)
                put("drops", buildJsonObject { put(plan.dropId, need) })
            }
            // dropTarget 快照整份计划字段，任务开始时按最新库存重算（前序掉落会反映进来）
            params += MaaTaskParams(
                type = MaaTaskType.FIGHT,
                params = json.toString(),
                dropTarget = DropTarget(
                    dropId = plan.dropId,
                    dropCount = plan.dropCount,
                    stage = plan.stage,
                    medicine = medicine,
                    stone = stone,
                    series = series,
                    logLabel = no.toString(),
                ),
            )
        }

        return TaskParamResult(params, logs)
    }
}
