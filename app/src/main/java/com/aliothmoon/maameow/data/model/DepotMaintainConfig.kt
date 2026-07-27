package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.maa.task.MaaTaskParams
import kotlinx.serialization.Serializable

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
 * 容器型任务：本身不对应任何 MaaCore 任务类型，由 DepotMaintainExpander
 * 展开成 1 个 Depot（可选）+ N 个 Fight。
 *
 * 迁移自 WPF DepotMaintainTask + DepotMaintainTaskUserControlModel。
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

    /**
     * 展开需要库存快照与活动开放状态等外部依赖，无法在配置对象内完成，
     * 实际展开由 DepotMaintainExpander 负责（AnalyzeTaskChainUseCase 调用）。
     *
     * 此处返回空列表而非抛异常：即便被漏接的分支误调用也只是不产出任务，不会崩溃。
     */
    override fun toTaskParams(ctx: TaskParamContext): List<MaaTaskParams> = emptyList()
}
