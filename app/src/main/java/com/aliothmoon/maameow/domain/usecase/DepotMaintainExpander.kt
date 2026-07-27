package com.aliothmoon.maameow.domain.usecase

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.DepotMaintainConfig
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 库存保持任务的展开器：把一个容器型节点展开成 1 个 Depot（可选）+ N 个 Fight。
 *
 * 迁移自 WPF DepotMaintainTaskUserControlModel.ISerialize.Serialize。
 * 之所以不放进 [DepotMaintainConfig.toTaskParams]：展开依赖库存快照与活动开放状态，
 * 配置对象不应持有这类外部依赖（见 TaskParamContext 的扩展规则）。
 */
class DepotMaintainExpander(
    private val depotRepository: DepotRepository,
    private val activityManager: ActivityManager,
    private val itemHelper: ItemHelper,
) {
    /**
     * @param params 展开出的 MaaCore 任务参数
     * @param logs 需要回放给用户的分析阶段日志
     */
    data class ExpandResult(
        val params: List<MaaTaskParams>,
        val logs: List<Pair<UiText, LogLevel>>,
    )

    /**
     * @param seriesLocked 代理倍率是否处于临时锁定期，由调用方按客户端类型判定
     */
    fun expand(cfg: DepotMaintainConfig, seriesLocked: Boolean): ExpandResult {
        val logs = mutableListOf<Pair<UiText, LogLevel>>()

        if (cfg.skipDuringActivity && activityManager.isActivityOpen()) {
            logs += uiTextOf(R.string.runlog_depot_skipped_activity) to LogLevel.INFO
            return ExpandResult(emptyList(), logs)
        }
        if (cfg.skipDuringResourceCollection && activityManager.isResourceCollectionOpen()) {
            logs += uiTextOf(R.string.runlog_depot_skipped_resource) to LogLevel.INFO
            return ExpandResult(emptyList(), logs)
        }

        val params = mutableListOf<MaaTaskParams>()

        // 刷新库存数据。注意：本轮的缺口在下面按当前快照算死，识别结果要到下次运行才生效，
        // 运行时按最新库存重算是后续任务（对齐上游 RefreshFightTaskDrops）
        if (cfg.updateDepot) {
            params += MaaTaskParams(MaaTaskType.DEPOT, "{}")
        }

        cfg.plans.forEachIndexed { index, plan ->
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
            if (!activityManager.isStageOpen(plan.stage)) {
                logs += uiTextOf(
                    R.string.runlog_depot_plan_stage_not_open, no, plan.stage
                ) to LogLevel.TRACE
                return@forEachIndexed
            }

            // 对齐上游：无库存数据或无该材料记录一律当 0，即按目标数量满量刷
            val current = depotRepository.countOf(plan.dropId)
            val need = plan.dropCount - current
            if (need <= 0) {
                val dropName = itemHelper.getItemInfo(plan.dropId)?.name ?: plan.dropId
                // 第 1 个占位符是 %1$s：PR5 运行时重算会复用同一条并传任务名
                logs += uiTextOf(
                    R.string.runlog_depot_plan_inventory_enough,
                    no.toString(), dropName, current, plan.dropCount,
                ) to LogLevel.TRACE
                return@forEachIndexed
            }

            // times 固定 Int.MAX_VALUE，靠 drops 达标终止，而非预先算次数
            val json = buildJsonObject {
                put("stage", plan.stage)
                put("times", Int.MAX_VALUE)
                // TODO: MaaCore 适配代理倍率后删除，交回 core 默认值 1
                put("series", if (seriesLocked) -1 else 1)
                put("medicine", if (plan.useMedicine) plan.medicineCount else 0)
                put("stone", if (plan.useStone) plan.stoneCount else 0)
                put("drops", buildJsonObject { put(plan.dropId, need) })
            }
            params += MaaTaskParams(MaaTaskType.FIGHT, json.toString())
        }

        return ExpandResult(params, logs)
    }
}
