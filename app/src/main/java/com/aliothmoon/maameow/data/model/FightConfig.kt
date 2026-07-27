package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.domain.models.DropTarget
import com.aliothmoon.maameow.domain.models.SeriesLock
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 未开放关卡重置策略
 *
 * 迁移自 WPF FightStageResetMode
 * - CURRENT: 关卡不在列表中时重置为 ""（当前/上次）
 * - IGNORE: 保持原值不变
 */
@Serializable
enum class StageResetMode {
    CURRENT,
    IGNORE
}

/**
 * 理智作战配置
 *
 * 完整迁移自 WPF FightSettingsUserControlModel.cs
 * 包含常规设置和高级设置的全部配置项
 *
 * WPF 源文件:
 * - ViewModel: FightSettingsUserControlModel.cs (第37-1044行)
 * - View: FightSettingsUserControl.xaml (第21-488行)
 * - Model: FightTask.cs (第19-60行)
 */
@Serializable
data class FightConfig(
    // ============ 常规设置 - 理智消耗 ============

    /**
     * 是否使用理智药
     *
     * 注意: 当使用源石时，此选项自动禁用
     */
    val useMedicine: Boolean = false,

    /**
     * 理智药数量
     *
     * 注意: 使用源石时会自动设为 999
     */
    val medicineNumber: Int = 999,

    /**
     * 是否使用源石
     *
     * 注意: 默认不保存此设置（AllowUseStoneSave=false）
     */
    val useStone: Boolean = false,

    /**
     * 源石数量
     *
     */
    val stoneNumber: Int = 0,

    // ============ 常规设置 - 战斗限制 ============

    /**
     * 是否限制战斗次数
     *
     */
    val hasTimesLimited: Boolean = false,

    /**
     * 最大战斗次数
     *
     * 默认值: 5（WPF 第453行）
     */
    val maxTimes: Int = 5,

    // ============ 常规设置 - 指定掉落 ============

    /**
     * 是否指定材料掉落
     *
     */
    val isSpecifiedDrops: Boolean = false,

    /**
     * 掉落材料 ID
     *
     * 材料列表从资源文件加载，排除特定材料（见 ViewModel 第548-564行）
     */
    val dropsItemId: String = "",

    /**
     * 掉落材料数量
     *
     * 默认值: 5（WPF 第644行）
     * 语义取决于 [isInventoryTarget]：false 时是本次要刷的数量，true 时是目标库存。
     */
    val dropsQuantity: Int = 5,

    /**
     * 指定掉落使用「目标库存」模式。
     *
     * false（默认）：[dropsQuantity] 是本次要刷的数量
     * true：[dropsQuantity] 是目标库存，实际刷取缺口 = dropsQuantity - 当前库存
     *
     * 迁移自 WPF FightTask.IsInventoryTarget。
     * 运行时缺口由 FightDropsRefresher 在任务开始时按最新库存重算。
     */
    val isInventoryTarget: Boolean = false,

    // ============ 常规设置 - 代理倍率与关卡 ============

    /**
     * 代理倍率
     *
     * 选项: 0(AUTO), 6, 5, 4, 3, 2, 1, -1(不切换)
     * 默认值: 0 (AUTO)
     * 可通过 HideSeries 隐藏
     */
    val series: Int = 0,

    /**
     * 首选关卡
     *
     * 可选关卡代码（如 "CE-6"）或从关卡列表选择
     * 支持关卡代码自动转换（如 "龙门币"→"CE-6"）
     */
    val stage1: String = "",

    /**
     * 备选关卡列表（可动态增删）
     *
     * 迁移自 WPF FightTask.StagePlan（去掉首项主关卡后的剩余项，可为任意数量）
     * 仅在 UseAlternateStage=true 时参与选关；每项为关卡代码，允许为空（代表「当前/上次」，与 WPF 空 StagePlanItem 一致）
     *
     * 注: 取代旧的固定 stage2/stage3/stage4 字段，不做老配置迁移
     *     （ignoreUnknownKeys 静默忽略旧字段，升级后旧备选选择丢失，首选关卡保留）
     */
    val alternateStages: List<String> = emptyList(),

    // ============ 高级设置 - 剿灭相关 ============

    /**
     * 使用自定义剿灭
     *
     */
    val useCustomAnnihilation: Boolean = false,

    /**
     * 剿灭关卡选择
     *
     * 选项: "Annihilation", "Chernobog@Annihilation",
     *       "LungmenOutskirts@Annihilation", "LungmenDowntown@Annihilation"
     */
    val annihilationStage: String = "Annihilation",

    // ============ 高级设置 - 战斗策略 ============

    /**
     * 博朗台模式（节省理智模式）
     *
     * Tooltip: "等待理智恢复后再开始行动，在理智即将溢出时开始作战"
     */
    val isDrGrandet: Boolean = false,

    /**
     * 自定义关卡代码
     *
     * 启用后，关卡选择从下拉框变为文本输入框
     */
    val customStageCode: Boolean = false,

    /**
     * 使用备选关卡
     *
     * 启用后显示 Stage2, Stage3, Stage4
     * 与 HideUnavailableStage 互斥
     */
    val useAlternateStage: Boolean = false,

    // ============ 高级设置 - 保存与显示 ============

    /**
     * 允许保存源石使用
     *
     * 默认值: false（WPF 第728行）
     * 启用前需要弹出警告确认框（见 ViewModel 第728-754行）
     */
    val allowUseStoneSave: Boolean = false,

    /**
     * 使用即将过期的理智药
     */
    val useExpiringMedicine: Boolean = false,

    /**
     * 吃药临期天数 (1-7)
     */
    val medicineExpireDays: Int = 2,

    /**
     * 活动即将结束时自动延长过期药天数
     */
    val useExpireMedicineForActivity: Boolean = false,

    /**
     * 隐藏不可用关卡
     *
     * 默认值: true（WPF 第769行）
     * 与 UseAlternateStage 互斥
     */
    val hideUnavailableStage: Boolean = true,

    /**
     * 未开放关卡重置策略
     *
     * 迁移自 WPF FightTask.StageResetMode
     * HideUnavailableStage=true 时强制 CURRENT
     * UseAlternateStage=true 时强制 IGNORE
     */
    val stageResetMode: StageResetMode = StageResetMode.CURRENT,

    /**
     * 隐藏代理倍率选择
     *
     * 隐藏常规设置中的代理倍率选择下拉框
     */
    val hideSeries: Boolean = false,

    /**
     * 在刷理智设置中显示自定义基建计划
     *
     * 控制常规设置第4行的基建计划选择是否显示（Grid Row 4）
     * 注意: 暂不迁移此功能
     */
    val customInfrastPlanShowInFightSettings: Boolean = false,

    /**
     * 游戏掉线时自动重连
     *
     * 默认值: true（WPF 第806行）
     */
    val autoRestartOnDrop: Boolean = true,

    // ============ 高级设置 - 周计划 ============

    /**
     * 是否启用周计划
     *
     * 迁移自 WPF FightTask.UseWeeklySchedule
     * 启用后仅在 weeklySchedule 中勾选的日期执行该任务
     */
    val useWeeklySchedule: Boolean = false,

    /**
     * 周计划配置
     *
     * 迁移自 WPF FightTask.WeeklySchedule
     * key 为 DayOfWeek 枚举名（MONDAY~SUNDAY），value 为是否启用
     */
    val weeklySchedule: Map<String, Boolean> = mapOf(
        "MONDAY" to true,
        "TUESDAY" to true,
        "WEDNESDAY" to true,
        "THURSDAY" to true,
        "FRIDAY" to true,
        "SATURDAY" to true,
        "SUNDAY" to true,
    )
) : TaskParamProvider {
    /**
     * 获取实际使用的关卡
     *
     * 根据备选关卡和关卡开放状态自动选择。
     * 需要 [ActivityManager] 判定关卡开放，故由调用方显式传入 ——
     * 早前这里用 GlobalContext 反向抓依赖，拿不到时会静默跳过全部开放判定、
     * 返回一个可能未开放的关卡，且单测因 Koin 未启动而只覆盖了那条降级分支。
     */
    fun getActiveStage(activityManager: ActivityManager): String {
        if (stage1.isEmpty()) return ""

        // 如果不使用备选关卡，直接返回首选关卡
        if (!useAlternateStage) return stage1

        var candidates = (listOf(stage1) + alternateStages).filter { it.isNotEmpty() }

        // customStageCode 手动输入时跳过此检查仅 CURRENT 重置策略需要按候选列表成员过滤，
        // 故 stageList 延迟到此分支内构建，避免备选/IGNORE 场景每次白白重建整份合并关卡列表
        if (!customStageCode && stageResetMode == StageResetMode.CURRENT) {
            val stageList = activityManager.getMergedStageList(filterByToday = false)
            candidates = candidates.filter { code ->
                stageList.any { it.code == code }
            }
        }

        // 参考 WPF GetFightStage: 从上往下取第一个今日开放的关卡
        // 用 isStageOpen（经 getStageInfo 兜底，对齐 WPF StageManager.IsStageOpen）判定，
        // 而非「候选列表成员 + isOpenToday」；否则主线关卡（如 16-14，不在候选列表）会被误判未开放而跳过
        val day = activityManager.getYjDayOfWeek()
        candidates.firstOrNull { code -> activityManager.isStageOpen(code, day) }
            ?.let { return it }

        // 全不开放则回退第一条候选，无候选则返回 ""
        return candidates.firstOrNull() ?: ""
    }

    /**
     * 验证配置有效性
     */
    fun isValid(): Boolean {
        // 必须至少指定一个关卡
        if (stage1.isEmpty()) return false

        // 如果指定掉落，必须选择材料
        if (isSpecifiedDrops && dropsItemId.isEmpty()) return false

        return true
    }

    override fun toTaskParams(ctx: TaskParamContext): TaskParamResult {
        var stage = getActiveStage(ctx.activityManager)

        // 自定义剿灭替换 (WPF SerializeTask line 735-738)
        if (stage == "Annihilation" && useCustomAnnihilation) {
            stage = annihilationStage
        }

        // 理智药和碎石独立判断 (WPF line 721-722)
        val actualMedicine = if (useMedicine) medicineNumber else 0
        val actualStone = if (useStone) stoneNumber else 0

        // 次数: WPF 不限制时使用 Int.MAX_VALUE (line 724)
        val actualTimes = if (hasTimesLimited) maxTimes else Int.MAX_VALUE

        // TODO: MaaCore 适配代理倍率 7~10 后删除，恢复为 put("series", series)
        val effectiveSeries = if (SeriesLock.isLocked(ctx.clientType)) -1 else series

        val expireDays = if (useExpiringMedicine) {
            var days = medicineExpireDays.coerceIn(1, 7)
            if (useExpireMedicineForActivity) {
                val activityExpireDays = ctx.activityManager.getActivityAwareExpireDays()
                if (activityExpireDays > 0) {
                    days = maxOf(days, activityExpireDays)
                }
            }
            days
        } else {
            null
        }

        val paramsJson = buildJsonObject {
            put("stage", stage)
            put("medicine", actualMedicine)
            expireDays?.let { put("medicine_expire_days", it) }
            put("stone", actualStone)
            put("times", actualTimes)
            put("series", effectiveSeries)
            if (isDrGrandet) {
                put("DrGrandet", true)
            }
            if (isSpecifiedDrops && dropsItemId.isNotBlank()) {
                // 目标库存模式：append 期初值按 dropsQuantity 下发（相当于假设库存为 0），
                // 任务真正开始时 FightDropsRefresher 会用最新库存再算一次覆盖
                put("drops", buildJsonObject {
                    put(dropsItemId, dropsQuantity)
                })
            }
        }

        val dropTarget = if (isSpecifiedDrops && isInventoryTarget && dropsItemId.isNotBlank()) {
            DropTarget(
                dropId = dropsItemId,
                dropCount = dropsQuantity,
                stage = stage,
                medicine = actualMedicine,
                stone = actualStone,
                series = effectiveSeries,
                // 占位；AnalyzeTaskChainUseCase 会覆盖为节点名
                logLabel = "Fight",
                medicineExpireDays = expireDays,
                drGrandet = isDrGrandet,
            )
        } else {
            null
        }

        return TaskParamResult(
            listOf(MaaTaskParams(MaaTaskType.FIGHT, paramsJson.toString(), dropTarget = dropTarget))
        )
    }
}